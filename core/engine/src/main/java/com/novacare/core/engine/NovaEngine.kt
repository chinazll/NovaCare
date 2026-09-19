package com.novacare.core.engine

import android.util.Log
import com.novacare.core.model.FileNode
import com.novacare.core.model.cleanRiskFromWire
import com.novacare.core.model.JunkItem
import com.novacare.core.model.junkKindFromWire
import com.novacare.core.model.StorageCategory
import com.novacare.core.model.StorageSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rust 确定性内核（L1）的 Kotlin 门面
 *
 * 这是 Kotlin 与 Rust 之间**唯一**的连接点。绑定代码由 UniFFI 从 Rust 侧
 * 的 proc-macro 标注自动生成（uniffi/novacare/novacare.kt），
 * 因此**不存在**手写 JNI 签名漂移的可能 —— 上一版最致命的 P0 就是
 * .so 编译了却永远加载不到（没有宿主类）。
 *
 * 契约：
 * - 引擎不可用时（.so 缺失 / ABI 不匹配）所有方法返回 null，
 *   由上层显示「暂不可用」，**绝不返回伪造数据**
 * - 所有调用切到 Default 线程，避免阻塞主线程
 */
@Singleton
class NovaEngine @Inject constructor() {

    /** 引擎是否可用（首次调用时惰性探测，失败即降级） */
    val isAvailable: Boolean by lazy {
        runCatching {
            uniffi.novacare.engineVersion().isNotBlank()
        }.onFailure { Log.e(TAG, "Rust engine unavailable", it) }
            .getOrDefault(false)
    }

    fun version(): String = if (isAvailable) {
        runCatching { uniffi.novacare.engineVersion() }.getOrDefault("unknown")
    } else {
        "Rust engine unavailable"
    }

    suspend fun analyzeStorage(rootPath: String, totalBytes: Long): StorageSnapshot? =
        engineCall {
            val report = uniffi.novacare.analyzeStorage(rootPath, totalBytes.toULong())
                ?: return@engineCall null
            StorageSnapshot(
                totalBytes = report.total.toLong(),
                usedBytes = report.used.toLong(),
                categories = report.categories.map {
                    StorageCategory(name = it.name, bytes = it.bytes.toLong(), ratio = it.ratio)
                },
                largestFiles = report.largestFiles.map { it.toDomain() },
                scanDurationMs = report.durationMs.toLong(),
                fromEngine = true,
            )
        }

    suspend fun scanJunk(
        rootPath: String,
        installedPackages: String,
        detectDuplicates: Boolean,
    ): com.novacare.core.model.JunkReport? = engineCall {
        val report = uniffi.novacare.scanJunk(rootPath, installedPackages, detectDuplicates)
            ?: return@engineCall null
        com.novacare.core.model.JunkReport(
            items = report.items.map { item ->
                JunkItem(
                    path = item.path,
                    label = item.label,
                    bytes = item.bytes.toLong(),
                    kind = junkKindFromWire(item.kind),
                    risk = cleanRiskFromWire(item.risk),
                    riskNote = item.riskNote,
                )
            },
            totalBytes = report.totalBytes.toLong(),
            safeBytes = report.safeBytes.toLong(),
            durationMs = report.durationMs.toLong(),
        )
    }

    suspend fun analyzeBattery(
        level: Int,
        temperatureTenths: Int,
        voltage: Int,
        current: Int,
        status: String,
        health: String,
        plugged: Int,
    ): BatterySnapshot? = engineCall {
        val r = uniffi.novacare.analyzeBattery(
            level.toUInt(),
            temperatureTenths,
            voltage,
            current,
            status,
            health,
            plugged,
        ) ?: return@engineCall null
        BatterySnapshot(
            healthScore = r.healthScore.toInt(),
            // null = 未知；本项目不伪造充电周期
            cycleCount = r.cycleCount?.toInt(),
            temperatureCelsius = r.temperature,
            voltageMv = r.voltage,
            level = r.level.toInt(),
            status = r.status,
            suggestions = r.suggestions,
        )
    }

    suspend fun analyzeApps(
        appsJson: String,
        sortBy: Int,
        ascending: Boolean,
    ): AppsSnapshot? = engineCall {
        val r = uniffi.novacare.analyzeApps(appsJson, sortBy, ascending) ?: return@engineCall null
        AppsSnapshot(
            totalApps = r.stats.totalApps,
            systemApps = r.stats.systemApps,
            userApps = r.stats.userApps,
            totalSize = r.stats.totalSize.toLong(),
            totalCache = r.stats.totalCache.toLong(),
            totalData = r.stats.totalData.toLong(),
            sortedPackages = r.sortedApps.map { it.packageName },
        )
    }

    private suspend fun <T> engineCall(block: suspend () -> T): T? {
        if (!isAvailable) return null
        return withContext(Dispatchers.Default) {
            runCatching { block() }
                .onFailure { Log.w(TAG, "Rust call failed", it) }
                .getOrNull()
        }
    }

    private fun uniffi.novacare.FileNode.toDomain(): FileNode = FileNode(
        path = path,
        bytes = bytes.toLong(),
        isDir = isDir,
        modifiedEpochMs = modified?.times(1000),
        children = children.map { it.toDomain() },
    )

    private companion object {
        const val TAG = "NovaEngine"
    }
}

/** 电池快照（引擎返回值的领域映射） */
data class BatterySnapshot(
    val healthScore: Int,
    val cycleCount: Int?,
    val temperatureCelsius: Float,
    val voltageMv: Int,
    val level: Int,
    val status: String,
    val suggestions: List<String>,
)

/** 应用统计快照 */
data class AppsSnapshot(
    val totalApps: Int,
    val systemApps: Int,
    val userApps: Int,
    val totalSize: Long,
    val totalCache: Long,
    val totalData: Long,
    /** 按指定规则排序后的包名序列（内核排序，Kotlin 只负责应用顺序） */
    val sortedPackages: List<String>,
)
