package com.novacare.feature.clean

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacare.core.data.SettingsRepository
import com.novacare.core.domain.BuildOptimizePlanUseCase
import com.novacare.core.domain.DeviceSnapshot
import com.novacare.core.domain.DeviceSnapshotCache
import com.novacare.core.domain.ExecutePlanUseCase
import com.novacare.core.domain.ScanDeviceUseCase
import com.novacare.core.domain.key
import com.novacare.core.engine.NovaEngine
import com.novacare.core.model.CleanPlan
import com.novacare.core.model.CleanResult
import com.novacare.core.model.CleanRisk
import com.novacare.core.model.JunkKind
import com.novacare.core.model.StorageCategory
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
 * 清理页（L2）—— 状态机
 *
 * [空闲] --扫描--> [扫描中] --完成--> [结果] --执行--> [执行中] --> [完成]
 *
 * 与首页的区别：首页给结论（一键优化），这里给**可控的细节** ——
 * 每一条建议来自哪个目录、多大、删了有什么代价、风险几级。
 *
 * 事故复盘（"UI 根本没有"）：上一版把 `state` 与 `lastResult` 并列成两个互不相干的
 * StateFlow，执行完把 plan.advices 清成空列表，然后另起一行小字报结果 ——
 * 用户既看不到清单，也看不到结果，只看到一个空页面。
 * 现在把执行结果收敛进状态机，并且**保留原清单**，让"执行了什么"可回看。
 */
@HiltViewModel
class CleanViewModel @Inject constructor(
    private val scan: ScanDeviceUseCase,
    private val buildPlan: BuildOptimizePlanUseCase,
    private val executePlan: ExecutePlanUseCase,
    private val cache: DeviceSnapshotCache,
    private val settings: SettingsRepository,
    private val engine: NovaEngine,
    private val permissions: SystemPermissions,
) : ViewModel() {

    sealed interface UiState {
        /** 尚未扫描（首屏入口态） */
        data object Idle : UiState

        /** 正在扫描 */
        data object Scanning : UiState

        /**
         * 已得到可执行的清单。
         *
         * @param storageCategories 内核给出的存储分类构成；引擎不可用时为空
         * @param engineAvailable 引擎不可用时 **不会** 伪造分类数据，UI 需如实说明
         * @param advancedMode 高级模式：开启后会引导去设置页清第三方缓存而不只是提示
         */
        data class Results(
            val plan: CleanPlan,
            val engineAvailable: Boolean,
            val advancedMode: Boolean,
            val storageCategories: List<StorageCategory>,
            val storageTotalBytes: Long,
            val storageUsedBytes: Long,
            val usagePermissionGranted: Boolean,
        ) : UiState

        /** 扫描失败 */
        data class Failed(val message: String) : UiState

        /** 正在执行清理 */
        data object Executing : UiState

        /** 执行完成 */
        data class Done(
            val result: CleanResult,
            val advancedMode: Boolean,
        ) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    /** 高级模式：勾选后「有风险」项也会进入清单 */
    private val _includeRisky = MutableStateFlow(false)
    val includeRisky: StateFlow<Boolean> = _includeRisky.asStateFlow()

    /** 引擎是否可用（首屏在扫描前就要显示降级提示） */
    private val _engineAvailable = MutableStateFlow(true)
    val engineAvailable: StateFlow<Boolean> = _engineAvailable.asStateFlow()

    private val _moveToRecycleBin = MutableStateFlow(true)
    val moveToRecycleBin: StateFlow<Boolean> = _moveToRecycleBin.asStateFlow()

    private var lastSnapshot: DeviceSnapshot? = null

    // ------------------------------------------------------------
    // 扫描
    // ------------------------------------------------------------

    /** 扫描入口。force = true 时忽略缓存（用户明确要求重扫） */
    fun scanNow(rootPath: String, force: Boolean = false) {
        viewModelScope.launch {
            _state.value = UiState.Scanning
            val started = System.currentTimeMillis()
            val snapshot = runCatching {
                if (!force) cache.last ?: scan(rootPath) else scan(rootPath)
            }.getOrElse { error ->
                _state.value = UiState.Failed(error.message ?: "扫描失败，请重试")
                return@launch
            }
            cache.put(snapshot)
            lastSnapshot = snapshot
            val duration = (System.currentTimeMillis() - started).coerceAtLeast(1L)
            publish(snapshot, duration)
        }
    }

    private fun publish(snapshot: DeviceSnapshot, durationMs: Long) {
        val plan = buildPlan(snapshot, durationMs, _includeRisky.value)
        _selected.value = plan.defaultSelected
        _engineAvailable.value = snapshot.engineAvailable
        _state.value = UiState.Results(
            plan = plan,
            engineAvailable = snapshot.engineAvailable,
            advancedMode = false,
            storageCategories = snapshot.storageDetail?.categories.orEmpty(),
            storageTotalBytes = snapshot.storage.totalBytes,
            storageUsedBytes = (snapshot.storage.totalBytes - snapshot.storage.availableBytes)
                .coerceAtLeast(0L),
            usagePermissionGranted = snapshot.usagePermissionGranted,
        )
        // 高级模式是异步读的，单独补一次，避免阻塞首帧
        viewModelScope.launch {
            val advanced = runCatching { settings.settings.first().advancedMode }.getOrDefault(false)
            val current = _state.value
            if (current is UiState.Results) {
                _state.value = current.copy(advancedMode = advanced)
            }
        }
    }

    // ------------------------------------------------------------
    // 勾选
    // ------------------------------------------------------------

    fun toggle(key: String) {
        val current = _selected.value.toMutableSet()
        if (!current.remove(key)) current.add(key)
        _selected.value = current
    }

    fun selectAll(selectAll: Boolean) {
        val plan = (_state.value as? UiState.Results)?.plan ?: return
        _selected.value = if (selectAll) {
            plan.advices.map { it.key() }.toSet()
        } else {
            emptySet()
        }
    }

    /**
     * 切换「包含需确认 / 有风险项」。
     *
     * 该开关不触发重新扫描 —— 内核报告里本来就带齐了所有项目的风险等级，
     * 过滤只发生在构建计划的这一步。这样切换是瞬时的，无需再等一次扫描。
     */
    fun setIncludeRisky(include: Boolean) {
        _includeRisky.value = include
        val snapshot = lastSnapshot ?: cache.last ?: return
        publish(snapshot, (_state.value as? UiState.Results)?.plan?.scanDurationMs ?: 0L)
    }

    fun setMoveToRecycleBin(enabled: Boolean) {
        _moveToRecycleBin.value = enabled
    }

    // ------------------------------------------------------------
    // 执行
    // ------------------------------------------------------------

    fun execute() {
        val current = _state.value as? UiState.Results ?: return
        if (_selected.value.isEmpty()) return
        viewModelScope.launch {
            _state.value = UiState.Executing
            val advanced = runCatching { settings.settings.first().advancedMode }.getOrDefault(false)
            val result = runCatching {
                executePlan(current.plan, _selected.value, advanced)
            }.getOrElse { error ->
                _state.value = UiState.Failed(error.message ?: "执行清理失败，请重试")
                return@launch
            }
            runCatching { settings.setLastClean(System.currentTimeMillis()) }
            // 执行后缓存作废：下一次进入必须重新扫描，否则会看到已清理的旧清单
            cache.clear()
            lastSnapshot = null
            _selected.value = emptySet()
            _state.value = UiState.Done(result, advanced)
        }
    }

    /** 从「完成」返回「结果概览」，允许用户重扫或再次查看清单 */
    fun rescan(rootPath: String) = scanNow(rootPath, force = true)

    fun dismissResult() {
        _state.value = UiState.Idle
    }

    /** 由 UI 调用：跳转对应的系统授权页 */
    fun grant(capability: MissingCapability) = permissions.launchGrantFor(capability)

    /** 引擎可用性（首屏在扫描前也能给出真实结论，不假装正常） */
    fun refreshEngineState() {
        _engineAvailable.value = engine.isAvailable
    }
}

/** 单个清理项的风险等级文案（与 RiskChip 的分级保持一致） */
val CleanRisk.explain: String
    get() = when (this) {
        CleanRisk.SAFE -> "删除后无感知"
        CleanRisk.CAUTION -> "可能影响正在使用该应用的功能"
        CleanRisk.RISKY -> "可能造成不可恢复的数据损失"
    }

/** 垃圾类型的中文名（列表分组标题用） */
val JunkKind.displayName: String
    get() = when (this) {
        JunkKind.CACHE -> "应用缓存"
        JunkKind.THUMBNAIL -> "缩略图"
        JunkKind.LOG -> "日志文件"
        JunkKind.TMP -> "临时文件"
        JunkKind.RESIDUAL -> "卸载残留"
        JunkKind.DUPLICATE -> "重复文件"
        JunkKind.EMPTY_DIR -> "空目录"
    }

/** 垃圾类型的默认勾选建议（与内核的 isSafeByDefault 对齐，但表述给用户看） */
val JunkKind.selectionHint: String
    get() = when (this) {
        JunkKind.CACHE -> "系统会按需重建，可放心清理"
        JunkKind.THUMBNAIL -> "相册会重新生成，首次滚动可能略慢"
        JunkKind.LOG -> "仅用于问题排查，清除后无法追溯历史崩溃"
        JunkKind.TMP -> "临时文件，可放心清理"
        JunkKind.RESIDUAL -> "对应应用已卸载，其数据目录不会再用到"
        JunkKind.DUPLICATE -> "保留一份即可，请确认保留哪一份"
        JunkKind.EMPTY_DIR -> "不含任何文件，删除无影响"
    }
