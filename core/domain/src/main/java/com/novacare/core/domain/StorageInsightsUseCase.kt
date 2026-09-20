package com.novacare.core.domain

import com.novacare.core.model.FileNode
import com.novacare.core.model.JunkItem
import com.novacare.core.model.JunkKind
import com.novacare.core.system.SystemPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 存储深度分析（Storage Guardian 的数据来源）
 *
 * 设计原则：**不新增扫描能力，只重新组织已有扫描结果**。
 * 全部数据来自 [ScanDeviceUseCase]（StatFs + Rust 内核 walkdir/BLAKE3），
 * 本类只做三件事：
 *   1. 把内核输出的"每份重复文件"还原成"重复文件组"（内核按份输出，UI 需要按组呈现）
 *   2. 按路径约定 + 真实修改时间给大文件做可解释的归类
 *   3. 扫空目录（内核当前不产出空目录项，见下）
 *
 * 零伪造纪律：
 *   - 内核不可用 → largestFiles / duplicates / residuals 全为空，UI 必须显示"内核不可用"
 *   - 分类不出来的就是 OTHER，绝不把没识别的文件硬塞进某个分类充数
 */
@Singleton
class StorageInsightsUseCase @Inject constructor(
    private val scanDevice: ScanDeviceUseCase,
    private val permissions: SystemPermissions,
) {

    suspend operator fun invoke(
        rootPath: String,
        detectDuplicates: Boolean,
    ): StorageInsights = withContext(Dispatchers.Default) {
        val snapshot = scanDevice(rootPath, detectDuplicates)
        val now = snapshot.nowMs

        val junkItems = snapshot.junk?.items.orEmpty()
        val largest = snapshot.storageDetail?.largestFiles.orEmpty()
            .sortedByDescending { it.bytes }

        val aged = largest
            .filter { it.bytes >= MIN_AGED_BYTES }
            .map { node ->
                AgedLargeFile(
                    file = node,
                    kind = classify(node.path),
                    ageDays = node.modifiedEpochMs
                        ?.takeIf { it > 0 }
                        ?.let { ((now - it) / 86_400_000L).toInt() },
                )
            }
            .sortedWith(compareByDescending<AgedLargeFile> { it.ageDays ?: -1 }
                .thenByDescending { it.file.bytes })

        StorageInsights(
            nowMs = now,
            totalBytes = snapshot.storage.totalBytes,
            availableBytes = snapshot.storage.availableBytes,
            engineAvailable = snapshot.engineAvailable,
            allFilesAccess = permissions.hasAllFilesAccess(),
            largestFiles = largest,
            duplicates = groupDuplicates(junkItems),
            residuals = junkItems.filter { it.kind == JunkKind.RESIDUAL },
            emptyDirs = findEmptyDirs(rootPath),
            agedFiles = aged,
            scanDurationMs = snapshot.storageDetail?.scanDurationMs ?: 0L,
            duplicatesScanned = detectDuplicates,
        )
    }

    // ============================================================
    // 重复文件分组
    // ============================================================

    /**
     * 内核对重复文件的输出是**按份**的：同组 N 份里保留第 1 份，其余 N-1 份
     * 各输出一条 DUPLICATE item，每条的 riskNote 里列出"同组其他文件"的路径
     * （**包含**被保留的那一份）。
     *
     * 所以还原一组的完整成员 = 自身 path + riskNote 里所有绝对路径行。
     * 用"成员集合"做 key 聚合，两条 item 属于同一组当且仅当成员集合相同。
     *
     * 不用 label 里的 BLAKE3 前 8 位做 key：那是展示文本，改文案就会崩；
     * 成员集合是数据本身，结构稳定。
     */
    private fun groupDuplicates(items: List<JunkItem>): List<DuplicateGroup> {
        val groups = LinkedHashMap<String, DuplicateGroup>()
        for (item in items) {
            if (item.kind != JunkKind.DUPLICATE) continue
            val members = LinkedHashSet<String>().apply {
                add(item.path)
                item.riskNote.lineSequence()
                    .map { it.trim() }
                    // 只认绝对路径行 —— riskNote 第一句是中文说明，必须过滤掉
                    .filter { it.startsWith("/") }
                    .forEach { add(it) }
            }.toList().sorted()
            if (members.size < 2) continue
            val key = members.joinToString(SEPARATOR)
            if (!groups.containsKey(key)) {
                groups[key] = DuplicateGroup(members = members, eachBytes = item.bytes)
            }
        }
        return groups.values.sortedByDescending { it.reclaimableBytes }
    }

    // ============================================================
    // 空目录（Kotlin 侧直扫）
    // ============================================================

    /**
     * 内核的 junk_detector 目前**没有**空目录检测（JunkKind.EMPTY_DIR 在 Rust 侧
     * 无对应产出）。这里在 Kotlin 侧用真实 stat 直扫补齐，并在 UI 上如实标注来源。
     *
     * 为什么不做成内核能力：改 Rust 需要重新编译 .so（本次不重编 native 产物），
     * 而空目录检测本身是 O(目录数) 的轻量操作，放 Kotlin 侧代价可接受。
     *
     * @param maxDepth 深度上限：再深就是应用私有目录，扫下去既耗时又没有清理价值
     * @param visitCap 访问目录数上限：防止在极端目录树上卡死
     */
    private fun findEmptyDirs(
        rootPath: String,
        maxDepth: Int = 4,
        visitCap: Int = 20_000,
    ): List<String> {
        val found = ArrayList<String>()
        var visited = 0

        fun walk(dir: File, depth: Int) {
            if (depth > maxDepth || visited >= visitCap || found.size >= MAX_EMPTY_DIRS) return
            // listFiles() 返回 null = 无权限/分区存储拒绝读取 —— 跳过，不猜测
            val children = dir.listFiles() ?: return
            visited++
            if (children.isEmpty()) {
                found.add(dir.absolutePath)
                return
            }
            for (child in children) {
                if (child.isDirectory) walk(child, depth + 1)
            }
        }

        runCatching { walk(File(rootPath), 0) }
        return found
    }

    // ============================================================
    // 大文件归类
    // ============================================================

    /**
     * 按**路径约定**归类，不做内容识别。
     *
     * 这是刻意的诚实选择：端侧没有跑图像/视频内容理解，就绝不能声称
     * "识别出这是截图"。每个分类都带 [AgedFileKind.why]，UI 必须展示。
     */
    private fun classify(path: String): AgedFileKind {
        val p = path.lowercase()
        return when {
            p.endsWith(".apk") || p.endsWith(".apks") || p.endsWith(".xapk") ->
                AgedFileKind.INSTALLER

            p.contains("/screenrecord") || p.contains("/screen recorder") ||
                p.contains("/screenrecording") || p.contains("screen_record") ||
                (p.contains("/movies/") && (p.contains("record") || p.contains("screen"))) ->
                AgedFileKind.SCREEN_RECORD

            p.contains("screenshot") || p.contains("/screenshots/") || p.contains("截图") ->
                AgedFileKind.SCREENSHOT

            p.contains("/download/") || p.contains("/downloads/") ->
                AgedFileKind.DOWNLOAD

            else -> AgedFileKind.OTHER
        }
    }

    private companion object {
        const val MIN_AGED_BYTES = 1024L * 1024L // 1 MB：小于这个体积谈"清理价值"没意义
        const val MAX_EMPTY_DIRS = 200
        const val SEPARATOR = "\u0000"
    }
}

/** 重复文件组：组内成员内容逐字节完全一致（BLAKE3，由 Rust 内核校验） */
data class DuplicateGroup(
    /** 内容完全相同的文件路径（含建议保留的那一份） */
    val members: List<String>,
    /** 单份体积（组内每份都一样大，这是 size 分组的前提） */
    val eachBytes: Long,
) {
    val copies: Int get() = members.size

    /** 保留一份、删除其余副本可释放的真实字节数（不是估算） */
    val reclaimableBytes: Long get() = eachBytes * (members.size - 1)
}

/**
 * 大文件的可解释归类。
 *
 * [why] 是"凭什么这么分"—— 这是和"渣功能"的分界线：
 * 用户有权知道结论的依据，而不是接受一个黑箱标签。
 */
enum class AgedFileKind(val label: String, val why: String) {
    DOWNLOAD(
        "下载",
        "路径位于 Download 目录。下载目录里的东西多数是一次性的（安装包、临时压缩包、收到的文件），且很少再被打开。",
    ),
    SCREENSHOT(
        "截图",
        "路径或文件名命中截图特征（Screenshots 目录 / screenshot 字样）。截图通常用于一次性分享，看完即无用。",
    ),
    SCREEN_RECORD(
        "录屏",
        "路径命中录屏特征（ScreenRecorder / Movies 目录下的 record/screen 文件）。录屏体积大、回看率低，是最占空间的一类。",
    ),
    INSTALLER(
        "安装包",
        "扩展名为 .apk / .apks / .xapk。装完即无用，需要时可重新下载；保留它唯一的价值是离线重装。",
    ),
    OTHER(
        "其他大文件",
        "按体积（≥1MB）与修改时间筛出，但未命中上述任何路径特征 —— 本 App 不猜测它是什么，请你自己判断。",
    ),
    ;
}

data class AgedLargeFile(
    val file: FileNode,
    val kind: AgedFileKind,
    /** 距今天数；null = 内核未给出修改时间（此时 UI 不得显示任何年龄） */
    val ageDays: Int?,
)

/** 存储深度分析结果（UI 只读，不回写） */
data class StorageInsights(
    val nowMs: Long,
    /** StatFs 真实总容量 */
    val totalBytes: Long,
    /** StatFs 真实可用容量 */
    val availableBytes: Long,
    /** 内核是否可用；false 时 largestFiles/duplicates/residuals 为空，UI 必须如实说明 */
    val engineAvailable: Boolean,
    /** 是否具备「所有文件访问」——false 时扫描结果会明显偏少，UI 必须提示 */
    val allFilesAccess: Boolean,
    val largestFiles: List<FileNode>,
    val duplicates: List<DuplicateGroup>,
    /** 内核判定的已卸载应用残留目录 */
    val residuals: List<JunkItem>,
    /** Kotlin 侧直扫得出的空目录（内核当前不产出该项） */
    val emptyDirs: List<String>,
    val agedFiles: List<AgedLargeFile>,
    val scanDurationMs: Long,
    /** 本次是否跑了重复文件哈希扫描（未跑时 UI 应提供入口，而不是显示"没有重复文件"） */
    val duplicatesScanned: Boolean,
) {
    val usedBytes: Long get() = (totalBytes - availableBytes).coerceAtLeast(0)

    /** 重复文件可释放的总量：只统计"确认保留一份"的部分，不把用户数据当垃圾 */
    val duplicatesReclaimableBytes: Long get() = duplicates.sumOf { it.reclaimableBytes }
}
