package com.novacare.feature.freeze

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacare.core.data.SettingsRepository
import com.novacare.core.domain.DeviceSnapshot
import com.novacare.core.domain.DeviceSnapshotCache
import com.novacare.core.domain.FreezeUseCase
import com.novacare.core.domain.ScanDeviceUseCase
import com.novacare.core.engine.NovaEngine
import com.novacare.core.model.AppUsageStats
import com.novacare.core.model.FreezeCandidate
import com.novacare.core.model.FreezeMethod
import com.novacare.core.model.FreezeResult
import com.novacare.core.model.FreezeRisk
import com.novacare.core.model.StandbyBucket
import com.novacare.core.system.MissingCapability
import com.novacare.core.system.SystemPermissions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 冻结页（L2）—— 状态机
 *
 * [空闲] --扫描--> [扫描中] --完成--> [就绪] --冻结/解冻--> [执行中] --> [结果]
 *
 * 事故复盘（"UI 根本没有"，且更严重的是**功能也不成立**）：
 *   1. 上一版把权限缺失（没有「使用情况访问」）当成"没有发现不常用应用"来展示 ——
 *      系统里明明有 200 个应用，界面却告诉用户"没有发现不常用应用"。这是谎报。
 *   2. `results` 与 `state` 是两个互不相干的流，冻结完只在一个角落打印三行文字。
 *   3. 没有"已冻结"的概念 —— 冻结成功后列表纹丝不动，用户完全无法判断是否生效。
 *
 * 现在：
 *   - 权限缺失、引擎不可用、系统未返回待机分级，全部提升为一等状态并如实说明；
 *   - 冻结/解冻结果写回 [FreezeCandidate]，列表状态即时反映"已冻结 / 未冻结"；
 *   - 执行前有确认步骤，执行后有可回看的结果清单。
 */
@HiltViewModel
class FreezeViewModel @Inject constructor(
    private val scan: ScanDeviceUseCase,
    private val cache: DeviceSnapshotCache,
    private val freeze: FreezeUseCase,
    private val settings: SettingsRepository,
    private val engine: NovaEngine,
    private val permissions: SystemPermissions,
) : ViewModel() {

    sealed interface UiState {
        data object Idle : UiState
        data object Scanning : UiState

        /**
         * 就绪。
         *
         * @param candidates 有依据的候选（结合使用情况与系统待机分级筛选过）
         * @param allApps 全部已装应用；**即使候选为空也应展示真实总数**，
         *                否则"没有发现"会被误读成"设备上没装应用"
         * @param usagePermissionGranted false 时 daysUnused 全为 null，必须显式告知
         * @param shizukuAvailable true 才能一键冻结；否则走官方引导路径
         */
        data class Ready(
            val candidates: List<FreezeCandidate>,
            val allApps: List<FreezeCandidate>,
            val advancedMode: Boolean,
            val shizukuAvailable: Boolean,
            val usagePermissionGranted: Boolean,
            val engineAvailable: Boolean,
            val includeSystem: Boolean,
        ) : UiState

        data class Failed(val message: String) : UiState

        /** 正在执行冻结 / 解冻 */
        data class Applying(val packageName: String, val label: String, val freezing: Boolean) : UiState

        /** 执行完成 */
        data class Applied(
            val result: FreezeResult,
            val label: String,
            val freezing: Boolean,
        ) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** 已被本次会话冻结的包名（Shizuku 路径可确知；官方引导路径不可确知，不写入） */
    private val _frozen = MutableStateFlow<Set<String>>(emptySet())
    val frozen: StateFlow<Set<String>> = _frozen.asStateFlow()

    /** 用户勾选的包名 */
    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    private val _includeSystem = MutableStateFlow(false)
    val includeSystem: StateFlow<Boolean> = _includeSystem.asStateFlow()

    /** 待确认的操作（执行前的确认步骤） */
    private val _pending = MutableStateFlow<PendingAction?>(null)
    val pending: StateFlow<PendingAction?> = _pending.asStateFlow()

    data class PendingAction(val packageNames: List<String>, val labels: List<String>, val freezing: Boolean)

    private var lastSnapshot: DeviceSnapshot? = null

    // ------------------------------------------------------------
    // 扫描
    // ------------------------------------------------------------

    fun load(rootPath: String, force: Boolean = false) {
        viewModelScope.launch {
            _state.value = UiState.Scanning
            val snapshot = runCatching {
                if (force) scan(rootPath) else cache.last ?: scan(rootPath)
            }.getOrElse { error ->
                _state.value = UiState.Failed(error.message ?: "读取应用列表失败，请重试")
                return@launch
            }
            cache.put(snapshot)
            lastSnapshot = snapshot
            publish(snapshot)
        }
    }

    private fun publish(snapshot: DeviceSnapshot) {
        viewModelScope.launch {
            val advanced = settings.settings.first().advancedMode
            val shizuku = permissions.isShizukuAvailable()
            val includeSystem = _includeSystem.value

            val allApps = freeze.candidates(snapshot, includeSystem = true)
            val candidates = freeze.candidates(snapshot, includeSystem = includeSystem)

            // 已冻结的包在候选列表里原样保留，由 UI 依据 _frozen 渲染状态 ——
            // 不从这里剔除，否则用户可以"冻结后立刻解冻"这条路径就断了
            _state.value = UiState.Ready(
                candidates = candidates,
                allApps = allApps,
                advancedMode = advanced,
                shizukuAvailable = shizuku,
                usagePermissionGranted = snapshot.usagePermissionGranted,
                engineAvailable = snapshot.engineAvailable,
                includeSystem = includeSystem,
            )
            _selected.value = candidates
                .filter { it.risk == FreezeRisk.SAFE }
                .map { it.app.packageName }
                .toSet()
        }
    }

    fun setIncludeSystem(include: Boolean) {
        _includeSystem.value = include
        val snapshot = lastSnapshot ?: cache.last ?: return
        publish(snapshot)
    }

    fun refreshCapabilities() {
        val snapshot = lastSnapshot ?: cache.last ?: return
        publish(snapshot)
    }

    // ------------------------------------------------------------
    // 选择
    // ------------------------------------------------------------

    fun toggleSelected(packageName: String) {
        val current = _selected.value.toMutableSet()
        if (!current.remove(packageName)) current.add(packageName)
        _selected.value = current
    }

    fun selectAll(selectAll: Boolean) {
        val ready = _state.value as? UiState.Ready ?: return
        _selected.value = if (selectAll) {
            ready.candidates
                .filter { it.risk != FreezeRisk.RISKY }
                .map { it.app.packageName }
                .toSet()
        } else {
            emptySet()
        }
    }

    // ------------------------------------------------------------
    // 冻结 / 解冻（含确认步骤）
    // ------------------------------------------------------------

    /** 单条操作：直接进入确认，不立即执行 */
    fun requestFreeze(packageName: String) {
        val ready = _state.value as? UiState.Ready ?: return
        val label = ready.candidates.firstOrNull { it.app.packageName == packageName }
            ?.app?.label ?: packageName
        _pending.value = PendingAction(listOf(packageName), listOf(label), freezing = true)
    }

    fun requestUnfreeze(packageName: String) {
        val ready = _state.value as? UiState.Ready ?: return
        val label = ready.candidates.firstOrNull { it.app.packageName == packageName }
            ?.app?.label ?: packageName
        _pending.value = PendingAction(listOf(packageName), listOf(label), freezing = false)
    }

    /** 批量操作：对当前勾选项进入确认 */
    fun requestBatchFreeze() {
        val ready = _state.value as? UiState.Ready ?: return
        val keys = _selected.value
        if (keys.isEmpty()) return
        val labels = ready.candidates
            .filter { it.app.packageName in keys }
            .map { it.app.label }
        if (labels.isEmpty()) return
        _pending.value = PendingAction(keys.toList(), labels, freezing = true)
    }

    fun cancelPending() {
        _pending.value = null
    }

    /** 确认后真正执行 */
    fun confirmPending() {
        val action = _pending.value ?: return
        _pending.value = null
        viewModelScope.launch {
            val advanced = settings.settings.first().advancedMode
            var last: FreezeResult? = null
            var lastLabel = action.labels.firstOrNull().orEmpty()

            for ((index, pkg) in action.packageNames.withIndex()) {
                _state.value = UiState.Applying(
                    packageName = pkg,
                    label = action.labels.getOrNull(index) ?: pkg,
                    freezing = action.freezing,
                )
                val result = if (action.freezing) {
                    freeze.freeze(pkg, advanced)
                } else {
                    freeze.unfreeze(pkg)
                }
                // 只有 Shizuku 路径才能确知系统状态已变更；
                // 官方引导路径只是"打开了设置页"，不写入已冻结集合 —— 不谎报成功
                if (result.success && result.method == FreezeMethod.SHIZUKU_SUSPEND) {
                    _frozen.value = if (action.freezing) {
                        _frozen.value + pkg
                    } else {
                        _frozen.value - pkg
                    }
                }
                last = result
                lastLabel = action.labels.getOrNull(index) ?: pkg
            }

            cache.clear()
            lastSnapshot?.let { snapshot ->
                _state.value = UiState.Ready(
                    candidates = freeze.candidates(snapshot, _includeSystem.value),
                    allApps = freeze.candidates(snapshot, includeSystem = true),
                    advancedMode = advanced,
                    shizukuAvailable = permissions.isShizukuAvailable(),
                    usagePermissionGranted = snapshot.usagePermissionGranted,
                    engineAvailable = snapshot.engineAvailable,
                    includeSystem = _includeSystem.value,
                )
            }
            _selected.value = emptySet()
            if (last != null) {
                _state.value = UiState.Applied(last, lastLabel, action.freezing)
            }
        }
    }

    fun dismissResult() {
        val snapshot = lastSnapshot ?: cache.last
        if (snapshot != null) {
            publish(snapshot)
        } else {
            _state.value = UiState.Idle
        }
    }

    /** 由 UI 调用：跳系统授权页 */
    fun grant(capability: MissingCapability) = permissions.launchGrantFor(capability)

    fun refreshEngineState() {
        val snapshot = lastSnapshot
        if (snapshot == null) {
            val ready = _state.value as? UiState.Ready ?: return
            _state.value = ready.copy(engineAvailable = engine.isAvailable)
        } else {
            publish(snapshot)
        }
    }
}

// ============================================================
// 展示层文案映射
// ============================================================

val FreezeRisk.displayName: String
    get() = when (this) {
        FreezeRisk.SAFE -> "可安全冻结"
        FreezeRisk.CAUTION -> "需确认"
        FreezeRisk.RISKY -> "不建议冻结"
    }

val FreezeRisk.explain: String
    get() = when (this) {
        FreezeRisk.SAFE -> "冻结后系统会停止它的后台活动，需要时手动打开即可恢复"
        FreezeRisk.CAUTION -> "该应用近期仍有活动，冻结可能影响通知或后台同步"
        FreezeRisk.RISKY -> "属于系统组件，冻结后可能导致部分系统功能异常"
    }

val StandbyBucket.displayName: String
    get() = when (this) {
        StandbyBucket.ACTIVE -> "活跃"
        StandbyBucket.WORKING_SET -> "常用"
        StandbyBucket.FREQUENT -> "较常用"
        StandbyBucket.RARE -> "很少使用"
        StandbyBucket.RESTRICTED -> "受限制"
        StandbyBucket.NEVER -> "从未使用"
        StandbyBucket.UNKNOWN -> "未分级"
    }

val FreezeMethod.displayName: String
    get() = when (this) {
        FreezeMethod.OFFICIAL_GUIDE -> "官方引导"
        FreezeMethod.SHIZUKU_SUSPEND -> "Shizuku 一键冻结"
        FreezeMethod.SYSTEM_HIBERNATION -> "系统休眠"
    }

/** 候选的应用信息 + 使用统计的统一读取入口（UI 只碰这里） */
val FreezeCandidate.daysLabel: String
    get() {
        val days = daysUnused ?: return "无使用记录"
        return when {
            days <= 0 -> "今天用过"
            days < 30 -> "$days 天前用过"
            days < 365 -> "${days / 30} 个月前用过"
            else -> "${days / 365} 年前用过"
        }
    }

/** 供 UI 读取 usage 映射中缺失统计时的说明 */
fun usageNote(stats: AppUsageStats?): String =
    if (stats == null) "系统未返回该应用的待机分级" else "待机分级：${stats.standbyBucket.displayName}"
