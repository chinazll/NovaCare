package com.novacare.feature.clean

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacare.core.common.formatBytes
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 「释放」按钮为什么点不了。
 *
 * 事故复盘（本次 P0）：过去 UI 只知道 `selected.isEmpty()`，于是把按钮变灰 + 一句
 * 「请选择要清理的项目」。用户看到扫描明明有结果、按钮却点不动，页面上没有任何一句话
 * 说明是「没授予使用情况访问权限」还是「内核不可用」，只能判定为释放功能坏了。
 * 现在每一种不可用都必须有：原因 + 下一步动作。
 */
enum class ReleaseAction {
    /** 一键勾选建议项（清单有内容，只是没勾） */
    SELECT_SUGGESTED,

    /** 跳转使用情况访问授权页 */
    GRANT_USAGE_STATS,

    /** 重新扫描 */
    RESCAN,

    /** 无动作可给（例如仍在扫描中） */
    NONE,
}

/**
 * 底部「释放」区的完整可用性。
 *
 * 由 ViewModel 统一算出来交给 UI 渲染，UI 不再自己拼「要不要变灰」的判断。
 */
data class ReleaseAvailability(
    /** true = 有选中项，点击即真正执行释放 */
    val canRelease: Boolean,
    /** 主按钮文案（体现当前状态，而不是永远一句「释放」） */
    val title: String,
    /** 原因说明；canRelease 为 true 时必须为 null */
    val explain: String?,
    val action: ReleaseAction,
    /** 不可用时的动作按钮文案；action 为 NONE 时为 null */
    val actionLabel: String?,
)

private val NeutralRelease = ReleaseAvailability(
    canRelease = false,
    title = "释放",
    explain = null,
    action = ReleaseAction.NONE,
    actionLabel = null,
)

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

    /** 用户是否手动改过勾选 —— 决定重建清单时是保留选择还是回到默认建议 */
    private var userEditedSelection = false

    /** 一次性提示（例如「点了释放但没勾任何项」）；UI 消费后必须调 [consumeMessage] */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    /**
     * 底部释放区的可用性：把「为什么点不了」算清楚，交给 UI 照抄渲染。
     *
     * 用 Eagerly 而不是 WhileSubscribed：[execute] 需要读它的 `.value` 兜底文案，
     * 惰性订阅会导致没人收集时读到初始值。
     */
    val releaseAvailability: StateFlow<ReleaseAvailability> =
        combine(_state, _selected) { currentState, selectedKeys ->
            computeAvailability(currentState, selectedKeys)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, NeutralRelease)

    private fun computeAvailability(
        currentState: UiState,
        selectedKeys: Set<String>,
    ): ReleaseAvailability {
        val results = currentState as? UiState.Results ?: return NeutralRelease
        val plan = results.plan

        // 只认当前清单里真实存在的键：避免残留脏 key 让「已选 X / Y」虚高
        val validKeys = plan.advices.map { it.key() }.toSet()
        val chosen = selectedKeys.filter { it in validKeys }.toSet()

        if (chosen.isNotEmpty()) {
            val chosenBytes = plan.advices
                .filter { it.key() in chosen }
                .sumOf { it.recommendedBytes }
            return ReleaseAvailability(
                canRelease = true,
                title = "释放 ${chosenBytes.formatBytes()}",
                explain = null,
                action = ReleaseAction.NONE,
                actionLabel = null,
            )
        }

        // 清单里有东西，只是没勾 —— 这不是故障，如实说明并给一键勾选入口
        if (plan.advices.isNotEmpty()) {
            val caution = plan.advices.count { it.risk == CleanRisk.CAUTION }
            val suggested = plan.advices.count { it.risk != CleanRisk.RISKY }
            val explain = buildString {
                append("清单里有 ${plan.advices.size} 项可选")
                if (caution > 0) {
                    append("，其中 $caution 项标记为「需确认」，因此没有替你预先勾选")
                }
                append("。勾选后即可释放")
                if (plan.keptCount > 0) {
                    append("；另有 ${plan.keptCount} 项因缺少使用数据被判为「建议保留」")
                }
                append("。")
            }
            return ReleaseAvailability(
                canRelease = false,
                title = "选择要释放的项目",
                explain = explain,
                action = ReleaseAction.SELECT_SUGGESTED,
                actionLabel = if (suggested > 0) "选择建议的 $suggested 项" else "全部选中",
            )
        }

        return when {
            !results.usagePermissionGranted -> ReleaseAvailability(
                canRelease = false,
                title = "需要使用情况访问权限",
                explain = buildString {
                    append("未授予「使用情况访问」权限，读不到应用最近使用时间，")
                    append("所以不敢替你判断哪些缓存能安全释放 —— 宁可不推荐，也不乱删。")
                    if (plan.keptCount > 0) {
                        append("本次有 ${plan.keptCount} 项应用缓存因此被判为「建议保留」，没有出现在清单里。")
                    }
                    append("授权后重新扫描即可生成清单。")
                },
                action = ReleaseAction.GRANT_USAGE_STATS,
                actionLabel = "去授权",
            )

            !results.engineAvailable -> ReleaseAvailability(
                canRelease = false,
                title = "清理引擎不可用",
                explain = buildString {
                    append("清理内核未加载，无法扫描文件级垃圾，本次能力已降级。")
                    append("此处不推测、也不展示没有经过真实扫描的数字。")
                },
                action = ReleaseAction.RESCAN,
                actionLabel = "重新扫描",
            )

            else -> ReleaseAvailability(
                canRelease = false,
                title = "没有发现可释放的内容",
                explain = "扫描已完成，当前确实没有可以安全释放的项目。",
                action = ReleaseAction.RESCAN,
                actionLabel = "重新扫描",
            )
        }
    }

    /** 勾选「建议项」= 当前清单里所有非 RISKY 的项（含 CAUTION，因为用户主动点了才算） */
    fun selectSuggested() {
        val plan = (_state.value as? UiState.Results)?.plan ?: return
        userEditedSelection = true
        _selected.value = plan.advices
            .filter { it.risk != CleanRisk.RISKY }
            .map { it.key() }
            .toSet()
    }

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
            // 新一轮扫描 = 新一轮建议，之前手勾选的作废
            userEditedSelection = false
            val duration = (System.currentTimeMillis() - started).coerceAtLeast(1L)
            publish(snapshot, duration)
        }
    }

    /**
     * @param keepSelection 非 null 时保留用户手勾的选择（用于重建清单），否则回到 [CleanPlan.defaultSelected]
     */
    private fun publish(
        snapshot: DeviceSnapshot,
        durationMs: Long,
        keepSelection: Set<String>? = null,
    ) {
        val plan = buildPlan(snapshot, durationMs, _includeRisky.value)
        val validKeys = plan.advices.map { it.key() }.toSet()
        // 选择集必须与当前清单对齐，杜绝残留脏 key 让「已选 X / Y」和环形图失真
        _selected.value = if (keepSelection != null) {
            keepSelection.filter { it in validKeys }.toSet()
        } else {
            plan.defaultSelected.filter { it in validKeys }.toSet()
        }
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
        val plan = (_state.value as? UiState.Results)?.plan ?: return
        val validKeys = plan.advices.map { it.key() }.toSet()
        if (key !in validKeys) return
        userEditedSelection = true
        val current = _selected.value.filter { it in validKeys }.toMutableSet()
        if (!current.remove(key)) current.add(key)
        _selected.value = current
    }

    fun selectAll(selectAll: Boolean) {
        val plan = (_state.value as? UiState.Results)?.plan ?: return
        userEditedSelection = true
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
        // 重建清单会重算 advices —— 过去这里把 _selected 直接重置回 defaultSelected，
        // 用户「勾了半天，一按『含需确认』全没了」。现在按是否手动改过来决定。
        val preferred = _selected.value.takeIf { userEditedSelection }
        publish(
            snapshot,
            (_state.value as? UiState.Results)?.plan?.scanDurationMs ?: 0L,
            preferred,
        )
    }

    fun setMoveToRecycleBin(enabled: Boolean) {
        _moveToRecycleBin.value = enabled
    }

    // ------------------------------------------------------------
    // 执行
    // ------------------------------------------------------------

    fun execute() {
        val current = _state.value as? UiState.Results ?: run {
            // 不是结果态（还在扫描 / 已失败）也不许静默：告诉用户现在没有可执行清单
            _message.value = "当前没有可执行的清理清单，请先完成一次扫描"
            return
        }
        val validKeys = current.plan.advices.map { it.key() }.toSet()
        val chosen = _selected.value.filter { it in validKeys }.toSet()
        if (chosen.isEmpty()) {
            // 事故复盘：这里是 `return` 静默吞掉点击 —— 用户点了按钮毫无反馈，
            // 只会判定「释放坏了」。现在显式提示，并把真实原因（缺权限 / 无可选）说清楚。
            _message.value = releaseAvailability.value.explain
                ?: "请先在清单里勾选要释放的项目"
            return
        }
        _selected.value = chosen
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

    /**
     * 页面回到前台时调用。
     *
     * 「去授权」是跳系统设置页，用户开完权限按返回键回到本页时 Compose 不会重组，
     * 快照还停在「未授权」，页面会继续显示「需要使用情况访问权限」——
     * 用户刚授完权却还是看到这句话，只会判定 App 有 bug。
     * 这里如实检测权限变化并重扫一遍。
     */
    fun onResume(rootPath: String) {
        val results = _state.value as? UiState.Results ?: return
        if (results.usagePermissionGranted) return
        if (!permissions.hasUsageStats()) return
        lastSnapshot = null
        cache.clear()
        scanNow(rootPath, force = true)
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
