package com.novacare.core.system

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.content.Context
import android.os.Process
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 内存数据源（Memory Guardian 的唯一数据入口）
 *
 * 全部读系统真实接口，不估算、不推导：
 *   - 总览：ActivityManager.MemoryInfo + /proc/meminfo（后者无需任何权限）
 *   - 进程占用：ActivityManager.getProcessMemoryInfo → Debug.MemoryInfo.getTotalPss()
 *
 * 拿不到的地方一律用 null / -1 表达"未知"，**绝不用 0 冒充"不占内存"** ——
 * 0 和"未知"在 UI 上是完全不同的两件事。
 */
@Singleton
class MemoryProcessSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * 系统内存总览。
     *
     * [cachedBytes] 来自 /proc/meminfo 的 Cached + Buffers —— 这部分内存
     * 系统随时可以回收，把它和"应用真正占用"混在一起说"已用 90%"是误导。
     * 读不到 /proc/meminfo（个别 ROM 限制）时为 null，UI 需如实省略该行。
     */
    data class MemoryOverview(
        val totalBytes: Long,
        val availableBytes: Long,
        /** 系统认定的低内存水位线；可用量低于它时系统开始强杀后台进程 */
        val thresholdBytes: Long,
        val lowMemory: Boolean,
        /** 可回收的缓存/缓冲（/proc/meminfo）；null = 读不到 */
        val cachedBytes: Long?,
        /** /proc/meminfo 是否可读（决定 UI 能否展示细分项） */
        val memInfoAvailable: Boolean,
    ) {
        val usedBytes: Long get() = (totalBytes - availableBytes).coerceAtLeast(0)
        /** 可用占比 0.0~1.0；总量为 0（拿不到）时返回 null，UI 不得画环 */
        val availableRatio: Float?
            get() = if (totalBytes > 0) availableBytes.toFloat() / totalBytes.toFloat() else null
    }

    /** 单个进程的内存占用 */
    data class ProcessMemory(
        val pid: Int,
        val processName: String,
        /** 该进程承载的包名（一个进程可能挂多个包） */
        val packages: List<String>,
        /** PSS 字节数；-1 = 系统拒绝提供本次读数 */
        val pssBytes: Long,
        /** [RunningAppProcessInfo.importance] 原值，由 UI 翻译为中文 */
        val importance: Int,
        val isSelf: Boolean,
    )

    fun overview(): MemoryOverview {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val info = ActivityManager.MemoryInfo()
        if (am != null) {
            runCatching { am.getMemoryInfo(info) }
        }
        val memInfo = readMemInfo()
        return MemoryOverview(
            totalBytes = info.totalMem,
            availableBytes = info.availMem,
            thresholdBytes = info.threshold,
            lowMemory = info.lowMemory,
            cachedBytes = memInfo?.let { (it["Cached"] ?: 0L) + (it["Buffers"] ?: 0L) },
            memInfoAvailable = memInfo != null,
        )
    }

    /**
     * 进程列表（按 PSS 降序）。
     *
     * 诚实说明：Android 5.0 起 `getRunningAppProcesses()` 只返回系统认为
     * "值得告诉第三方"的进程（通常是前台、可见、以及部分服务进程），
     * 完整进程列表已不对第三方开放。返回数量少不是 bug，UI 必须写清楚。
     */
    fun processes(): List<ProcessMemory> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return emptyList()
        val running = runCatching { am.getRunningAppProcesses() }.getOrNull().orEmpty()
        if (running.isEmpty()) return emptyList()

        val myPid = Process.myPid()
        val out = ArrayList<ProcessMemory>(running.size)
        for (proc in running) {
            val pss = runCatching {
                val infos = am.getProcessMemoryInfo(intArrayOf(proc.pid))
                if (infos.isNullOrEmpty()) null else infos[0].totalPss
            }.getOrNull()
            out += ProcessMemory(
                pid = proc.pid,
                processName = proc.processName.orEmpty(),
                packages = proc.pkgList?.toList().orEmpty(),
                // getTotalPss() 单位为 KB；null → -1 表示"系统未给出读数"
                pssBytes = if (pss == null || pss <= 0) -1L else pss.toLong() * 1024L,
                importance = proc.importance,
                isSelf = proc.pid == myPid,
            )
        }
        return out.sortedByDescending { it.pssBytes }
    }

    /**
     * 本应用自身缓存目录的当前体积（真实 stat）。
     *
     * 只统计自己 —— 第三方 App 无权清理其他应用的缓存目录（Android 分区存储 +
     * 应用沙箱），声称"一键清理全部应用缓存"是假的。
     */
    fun selfCacheBytes(): Long = runCatching { sizeOf(context.cacheDir) }.getOrDefault(0L)

    /**
     * 清理本应用自身缓存。
     *
     * @return 真实释放字节数（删除前实测，删除后校验）
     */
    fun clearSelfCache(): Long {
        val dir = context.cacheDir ?: return 0L
        val before = runCatching { sizeOf(dir) }.getOrDefault(0L)
        var freed = 0L
        dir.listFiles()?.forEach { child ->
            val size = runCatching { sizeOf(child) }.getOrDefault(0L)
            val ok = runCatching { child.deleteRecursively() }.getOrDefault(false)
            if (ok) freed += size
        }
        // 实测兜底：以"删除前后目录体积差"为准，避免某些文件删除失败却计入释放量
        val after = runCatching { sizeOf(dir) }.getOrDefault(0L)
        return (before - after).coerceAtLeast(0L).takeIf { it > 0 } ?: freed
    }

    /**
     * 包名 → 应用名。查不到（已卸载 / 包可见性限制）时原样返回包名，
     * **绝不返回"未知应用"之类的占位串** —— 那会掩盖"查不到"这个事实。
     */
    fun appLabel(packageName: String): String = runCatching {
        context.packageManager.getApplicationLabel(
            context.packageManager.getApplicationInfo(packageName, 0),
        ).toString()
    }.getOrDefault(packageName)

    // ============================================================
    // 内部
    // ============================================================

    /** /proc/meminfo：无需权限的内核内存账本，单位是 kB */
    private fun readMemInfo(): Map<String, Long>? {
        return runCatching {
            val map = HashMap<String, Long>(16)
            File("/proc/meminfo").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val idx = line.indexOf(':')
                    if (idx <= 0) return@forEach
                    val key = line.substring(0, idx).trim()
                    val value = line.substring(idx + 1)
                        .trim()
                        .split(Regex("\\s+"))
                        .firstOrNull()
                        ?.toLongOrNull()
                        ?: return@forEach
                    map[key] = value * 1024L // kB → bytes
                }
            }
            if (map.isEmpty()) null else map
        }.getOrNull()
    }

    private fun sizeOf(file: File?): Long {
        if (file == null || !file.exists()) return 0L
        if (file.isFile) return file.length()
        var sum = 0L
        file.walkTopDown()
            .onFail { _, _ -> }
            .forEach { if (it.isFile) sum += it.length() }
        return sum
    }
}

/** importance → 中文说明（UI 复用，避免各页各写一套） */
fun importanceLabel(importance: Int): String = when (importance) {
    RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "前台"
    RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> "前台服务"
    RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "可见"
    RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE -> "可感知（如后台播放）"
    RunningAppProcessInfo.IMPORTANCE_SERVICE -> "后台服务"
    RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING -> "栈顶休眠"
    RunningAppProcessInfo.IMPORTANCE_CACHED -> "缓存（可回收）"
    RunningAppProcessInfo.IMPORTANCE_GONE -> "已退出"
    else -> "未知"
}

/** 为什么这个进程会占着内存（可解释性） */
fun importanceWhy(importance: Int): String = when (importance) {
    RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "你正在用它，系统优先保活，正常。"
    RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE ->
        "它在跑前台服务（通知栏常驻那种），系统不会主动回收。不需要就去通知栏关掉它。"
    RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "界面不可见但仍在参与显示（如悬浮窗/画中画）。"
    RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE ->
        "在做你能感知到的事（后台播放、导航、下载），不算异常。"
    RunningAppProcessInfo.IMPORTANCE_SERVICE -> "在后台跑服务，内存紧张时会被系统优先回收。"
    RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING -> "刚切走的进程，系统保留它以备你马上切回来。"
    RunningAppProcessInfo.IMPORTANCE_CACHED ->
        "已经缓存起来了，不消耗 CPU，只在内存不足时被回收 —— 这类进程多不代表卡。"
    RunningAppProcessInfo.IMPORTANCE_GONE -> "进程已退出，占用即将释放。"
    else -> "系统未给出明确的重要度级别。"
}
