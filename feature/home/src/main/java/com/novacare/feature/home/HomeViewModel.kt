package com.novacare.feature.home

import android.app.Application
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacare.core.ai.SuggestionEngine
import com.novacare.core.data.HistoryDao
import com.novacare.core.domain.DeviceSnapshot
import com.novacare.core.domain.DeviceSnapshotCache
import com.novacare.core.domain.HealthScoreUseCase
import com.novacare.core.domain.ScanDeviceUseCase
import com.novacare.core.engine.NovaEngine
import com.novacare.core.model.HealthDimension
import com.novacare.core.model.HealthScore
import com.novacare.core.system.MissingCapability
import com.novacare.core.system.SystemPermissions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 首页一次数据装载的结果。失败是**一等状态**，不能被伪装成"还在加载"。 */
sealed interface HomeLoadState {
    data object Loading : HomeLoadState
    data object Ready : HomeLoadState
    data class Failed(val message: String) : HomeLoadState
}

/**
 * 首页 —— 只读的「设备健康总览」，不承担任何破坏性操作。
 *
 * ============================================================
 * 【职责边界（与其余 tab 互斥）】
 *   首页   = 读数和判断：设备现在多健康、依据是什么、哪一项需要去处理。
 *   清理   = 唯一执行入口：扫描 → 勾选 → 释放，这条链路只在清理页。
 *   冻结   = 应用的停用/恢复。
 *   自动化 = 规则的配置与启停。
 *   设置   = 权限、引擎、AI、外观。
 *
 *   推论：**首页不出现任何执行按钮**（没有"一键释放""立即冻结"）。
 *   它对外只输出两种东西 —— 「读数」和「该去哪个页」的建议。
 * ============================================================
 *
 * 【零伪造的三条落点】
 *   1. 扫描失败必须是 Failed 状态并给出原因，不能停在"正在读取"。
 *   2. 未授予使用情况访问时，应用维度**不参与评分**（把 apps 置空交给
 *      HealthScoreUseCase，由它的既有规则自动剔除并重新归一化权重），
 *      而不是把"没有使用数据"当成"30 天未使用"。
 *   3. 读数本身不编造：拿不到就是拿不到，UI 用「未获取」而不是 0 或占位。
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val app: Application,
    private val scan: ScanDeviceUseCase,
    private val healthScore: HealthScoreUseCase,
    private val cache: DeviceSnapshotCache,
    private val engine: NovaEngine,
    private val permissions: SystemPermissions,
    private val history: HistoryDao,
) : ViewModel() {

    /** 首页只读读数（打开即显示，不必先点按钮） */
    data class Overview(
        val usedBytes: Long,
        val totalBytes: Long,
        val freeBytes: Long,
        /** 0 = 拿不到 ActivityManager 读数 */
        val memoryAvailableBytes: Long,
        val memoryTotalBytes: Long,
        val batteryPercent: Int,
        /** BatteryManager 的健康字串；"unknown" = 系统没给结论 */
        val batteryHealth: String,
        val batteryTemperatureTenths: Int,
    )

    private val _loadState = MutableStateFlow<HomeLoadState>(HomeLoadState.Loading)
    val loadState: StateFlow<HomeLoadState> = _loadState.asStateFlow()

    private val _score = MutableStateFlow<HealthScore?>(null)
    val score: StateFlow<HealthScore?> = _score.asStateFlow()

    /** 评分依据：参与加权的维度清单 + 被排除的维度及原因（诚实披露） */
    private val _basis = MutableStateFlow("")
    val basis: StateFlow<String> = _basis.asStateFlow()

    /**
     * 每个健康维度的「为什么现在要去处理」的理由。
     * 只有真实触发了才放进来 —— 没有理由就不显示跳转箭头，
     * 免得首页退化成把底栏再抄一遍的静态导航。
     */
    private val _hints = MutableStateFlow<Map<HealthDimension, String>>(emptyMap())
    val hints: StateFlow<Map<HealthDimension, String>> = _hints.asStateFlow()

    /** 引擎不可用 —— UI 必须显式告知，不能假装正常 */
    private val _engineAvailable = MutableStateFlow(true)
    val engineAvailable: StateFlow<Boolean> = _engineAvailable.asStateFlow()

    private val _engineVersion = MutableStateFlow("")
    val engineVersion: StateFlow<String> = _engineVersion.asStateFlow()

    /** 缺失的能力（降级提示的数据源） */
    private val _missing = MutableStateFlow<List<MissingCapability>>(emptyList())
    val missing: StateFlow<List<MissingCapability>> = _missing.asStateFlow()

    private val _overview = MutableStateFlow<Overview?>(null)
    val overview: StateFlow<Overview?> = _overview.asStateFlow()

    init {
        refreshCapabilities()
        load(useCache = true)
    }

    /** 权限状态可能在用户去设置页后变化，回到前台时重查 */
    fun refreshCapabilities() {
        _missing.value = permissions.missingCapabilities()
        _engineAvailable.value = engine.isAvailable
        _engineVersion.value = engine.version()
    }

    /** 用户点「重试」：跳过缓存重扫一次 */
    fun refresh() {
        load(useCache = false)
    }

    /** 由 UI 调用：跳转系统授权页 */
    fun grant(capability: MissingCapability) = permissions.launchGrantFor(capability)

    private fun load(useCache: Boolean) {
        viewModelScope.launch {
            _loadState.value = HomeLoadState.Loading

            val cached = if (useCache) cache.last else null
            val snapshot = cached ?: runCatching { scan(rootPath()) }
                .onFailure { failure ->
                    _loadState.value =
                        HomeLoadState.Failed(failure.message ?: "扫描未完成，原因未知")
                }
                .getOrNull()

            if (snapshot == null) {
                // 到这里还没被置成 Failed 就兜底：宁可报「读不到」，也不要一直卡在"正在读取"
                if (_loadState.value !is HomeLoadState.Failed) {
                    _loadState.value = HomeLoadState.Failed("无法读取设备状态")
                }
                refreshCapabilities()
                return@launch
            }

            cache.put(snapshot)
            apply(snapshot)
            _loadState.value = HomeLoadState.Ready
        }
    }

    private suspend fun apply(snapshot: DeviceSnapshot) {
        _engineAvailable.value = snapshot.engineAvailable
        _engineVersion.value = engine.version()

        val usageGranted = snapshot.usagePermissionGranted

        // 未授权 → 把 apps 置空再交给评分器。
        // HealthScoreUseCase 的既有规则是「拿不到数据的维度不参与评分，权重按
        // 剩余维度归一化」—— apps 为空时应用维度自动被剔除且不参与加权，
        // 于是总分只会由真实读数构成。这比事后过滤维度更可靠：口径完全一致。
        val scoredSnapshot = if (usageGranted) {
            snapshot
        } else {
            snapshot.copy(apps = emptyList(), usage = emptyMap())
        }
        val score = healthScore(scoredSnapshot, batteryScore = null)
        _score.value = score
        _basis.value = buildBasis(score, usageGranted)

        // 只有明确读到"最后一次使用时间"的应用才计入僵应用；
        // lastUsedEpochMs == null 表示「未知」，不是「很久没用」。
        val staleCount = if (usageGranted) {
            snapshot.apps.count { app ->
                (app.daysSinceLastUse(snapshot.nowMs) ?: -1) >= 30
            }
        } else {
            null
        }

        val used = (snapshot.storage.totalBytes - snapshot.storage.availableBytes)
            .coerceAtLeast(0L)
        val junkSafe = snapshot.junk?.safeBytes ?: 0L

        _overview.value = Overview(
            usedBytes = used,
            totalBytes = snapshot.storage.totalBytes,
            freeBytes = snapshot.storage.availableBytes,
            memoryAvailableBytes = snapshot.memory.availableBytes,
            memoryTotalBytes = snapshot.memory.totalBytes,
            batteryPercent = snapshot.battery.levelPercent,
            batteryHealth = snapshot.battery.health,
            batteryTemperatureTenths = snapshot.battery.temperatureTenths,
        )

        _hints.value = buildHints(
            snapshot = snapshot,
            staleCount = staleCount,
            junkSafeBytes = junkSafe,
        )
    }

    private fun buildBasis(score: HealthScore, usageGranted: Boolean): String {
        if (score.dimensions.isEmpty()) return "没有任何可用读数，无法评分"
        val names = score.dimensions.joinToString(" · ") { it.dimension.displayName }
        return if (usageGranted) {
            "依据 $names 加权得出"
        } else {
            "依据 $names 加权得出 · 未授予使用情况访问，应用维度未参与"
        }
    }

    private suspend fun buildHints(
        snapshot: DeviceSnapshot,
        staleCount: Int?,
        junkSafeBytes: Long,
    ): Map<HealthDimension, String> {
        val hints = mutableMapOf<HealthDimension, String>()

        // 存储：只有当确实有可回收量时才说得出"去清理能腾多少"
        val suggestion = SuggestionEngine.suggest(
            lastCleanMs(),
            junkSafeBytes,
            snapshot.nowMs,
        )
        if (suggestion != null && suggestion.worthDoing) {
            hints[HealthDimension.STORAGE] = suggestion.title
        }

        if (staleCount != null && staleCount > 0) {
            hints[HealthDimension.APP] = "$staleCount 个应用超过 30 天未使用"
        }

        return hints
    }

    /**
     * 最近一次成功清理的时间戳（只读）。
     *
     * 首页不写清理历史（那是「清理」tab 的职责），
     * 但"距上次清理多久"是判断要不要去清一次的真实依据，这里只做只读查询。
     */
    private suspend fun lastCleanMs(): Long? =
        runCatching { history.lastCleanEpochMs() }.getOrNull()

    private fun rootPath(): String =
        Environment.getExternalStorageDirectory()?.absolutePath
            ?: app.getExternalFilesDir(null)?.absolutePath
            ?: "/storage/emulated/0"
}
