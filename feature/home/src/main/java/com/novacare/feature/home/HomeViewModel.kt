package com.novacare.feature.home

import android.app.Application
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacare.core.ai.SuggestionEngine
import com.novacare.core.common.formatBytes
import com.novacare.core.data.SettingsRepository
import com.novacare.core.domain.BuildOptimizePlanUseCase
import com.novacare.core.domain.DeviceSnapshot
import com.novacare.core.domain.DeviceSnapshotCache
import com.novacare.core.domain.ExecutePlanUseCase
import com.novacare.core.domain.HealthScoreUseCase
import com.novacare.core.domain.ScanDeviceUseCase
import com.novacare.core.domain.key
import com.novacare.core.engine.NovaEngine
import com.novacare.core.model.CleanPlan
import com.novacare.core.model.CleanResult
import com.novacare.core.model.HealthScore
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
 * 首页 —— 一键优化的状态机
 *
 * [空闲] --点击--> [扫描中] --完成--> [结果预览] --执行--> [执行中] --> [完成]
 *
 * 事故复盘（P0，"什么功能都没有"）：
 *   1. 引擎不可用（ProGuard 裁掉 JNA/UniFFI + 缺 libjnidispatch.so）→
 *      scanJunk / analyzeStorage 全部返回 null
 *   2. `snapshot.junk?.items?.filter{...}` 里的 `?.` 让 null 静默变成空列表
 *   3. advices 为空 → UI 走 EmptyState("没有发现可安全清理的项目")
 *   4. 分数只由 storage/memory/battery 三维算出，永远不为 null
 *   5. 用户看到一个**有分数、有按钮、但永远说"没东西可清"**的界面
 *
 * 现在：把「引擎降级」和「权限缺失」提升为一等状态，UI 上显式解释 + 给跳转入口，
 * 绝不让 null 静默变成"没有东西可清"。
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val app: Application,
    private val scan: ScanDeviceUseCase,
    private val buildPlan: BuildOptimizePlanUseCase,
    private val executePlan: ExecutePlanUseCase,
    private val healthScore: HealthScoreUseCase,
    private val cache: DeviceSnapshotCache,
    private val engine: NovaEngine,
    private val settings: SettingsRepository,
    private val permissions: SystemPermissions,
) : ViewModel() {

    sealed interface UiState {
        data object Idle : UiState
        data object Scanning : UiState
        data class Preview(val plan: CleanPlan, val score: HealthScore) : UiState
        data object Executing : UiState
        data class Done(val result: CleanResult, val score: HealthScore) : UiState
        data class Error(val message: String) : UiState
    }

    /** 首页概览读数（打开即显示，不必先点按钮） */
    data class Overview(
        val usedBytes: Long,
        val totalBytes: Long,
        val freeBytes: Long,
        val junkSafeBytes: Long,
        val junkTotalBytes: Long,
        val staleAppCount: Int,
        val appCount: Int,
    )

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _score = MutableStateFlow<HealthScore?>(null)
    val score: StateFlow<HealthScore?> = _score.asStateFlow()

    private val _summary = MutableStateFlow("")
    val summary: StateFlow<String> = _summary.asStateFlow()

    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    /** 引擎不可用 —— UI 必须显式告知，不能假装正常 */
    private val _engineAvailable = MutableStateFlow(true)
    val engineAvailable: StateFlow<Boolean> = _engineAvailable.asStateFlow()

    private val _engineVersion = MutableStateFlow("")
    val engineVersion: StateFlow<String> = _engineVersion.asStateFlow()

    /** 缺失的能力（权限引导卡的数据源） */
    private val _missing = MutableStateFlow<List<MissingCapability>>(emptyList())
    val missing: StateFlow<List<MissingCapability>> = _missing.asStateFlow()

    private val _hasScanned = MutableStateFlow(false)
    val hasScanned: StateFlow<Boolean> = _hasScanned.asStateFlow()

    private val _overview = MutableStateFlow<Overview?>(null)
    val overview: StateFlow<Overview?> = _overview.asStateFlow()

    private var currentPlan: CleanPlan? = null

    init {
        refreshCapabilities()
        // 首页先给读数，不要求用户先点按钮 —— 打开就有信息
        viewModelScope.launch {
            val snapshot = cache.last ?: runCatching { scan(rootPath()) }.getOrNull()
            if (snapshot != null) {
                cache.put(snapshot)
                refreshScore(snapshot)
            } else {
                _engineAvailable.value = engine.isAvailable
                _engineVersion.value = engine.version()
            }
        }
    }

    /** 权限状态可能在用户去设置页后变化，回到前台时重查 */
    fun refreshCapabilities() {
        _missing.value = permissions.missingCapabilities()
        _engineAvailable.value = engine.isAvailable
        _engineVersion.value = engine.version()
    }

    fun onOptimizeClick() {
        viewModelScope.launch {
            _state.value = UiState.Scanning
            val started = System.currentTimeMillis()
            runCatching {
                scan(rootPath())
            }.onSuccess { snapshot ->
                cache.put(snapshot)
                _hasScanned.value = true
                val duration = System.currentTimeMillis() - started
                val plan = buildPlan(snapshot, duration)
                currentPlan = plan
                _selected.value = plan.defaultSelected
                _state.value = UiState.Preview(plan, refreshScore(snapshot))
                refreshCapabilities()
            }.onFailure { e ->
                _state.value = UiState.Error(e.message ?: "扫描失败，请重试")
                refreshCapabilities()
            }
        }
    }

    fun toggle(key: String) {
        val current = _selected.value.toMutableSet()
        if (!current.remove(key)) current.add(key)
        _selected.value = current
    }

    fun selectAll(selectAll: Boolean) {
        val plan = currentPlan ?: return
        _selected.value = if (selectAll) {
            plan.advices.map { it.key() }.toSet()
        } else {
            emptySet()
        }
    }

    fun onConfirm() {
        val plan = currentPlan ?: return
        viewModelScope.launch {
            _state.value = UiState.Executing
            val advanced = settings.settings.first().advancedMode
            val result = executePlan(plan, _selected.value, advanced)
            // 执行后重扫，让分数反映真实变化而不是乐观估计
            val fresh = runCatching { scan(rootPath()) }.getOrNull()
            if (fresh != null) {
                cache.put(fresh)
                _state.value = UiState.Done(result, refreshScore(fresh))
            } else {
                _state.value = UiState.Done(
                    result,
                    _score.value ?: HealthScore(0, emptyList(), "数据不足"),
                )
            }
        }
    }

    fun reset() {
        _state.value = UiState.Idle
        currentPlan = null
        _selected.value = emptySet()
    }

    /** 由 UI 调用：跳转授权页 */
    fun grant(capability: MissingCapability) = permissions.launchGrantFor(capability)

    private fun refreshScore(snapshot: DeviceSnapshot): HealthScore {
        // 电池健康度依赖内核；不可用时 HealthScoreUseCase 会降级为真实电量而非伪造
        val score = healthScore(snapshot, batteryScore = null)
        _score.value = score

        _engineAvailable.value = snapshot.engineAvailable
        _engineVersion.value = engine.version()

        val used = snapshot.storage.totalBytes - snapshot.storage.availableBytes
        _overview.value = Overview(
            usedBytes = used,
            totalBytes = snapshot.storage.totalBytes,
            freeBytes = snapshot.storage.availableBytes,
            junkSafeBytes = snapshot.junk?.safeBytes ?: 0L,
            junkTotalBytes = snapshot.junk?.totalBytes ?: 0L,
            staleAppCount = snapshot.apps.count {
                val days = it.daysSinceLastUse(snapshot.nowMs)
                days == null || days >= 30
            },
            appCount = snapshot.apps.size,
        )

        val suggestion = SuggestionEngine.suggest(
            lastCleanMs(),
            snapshot.junk?.safeBytes ?: 0L,
            snapshot.nowMs,
        )
        _summary.value = buildString {
            if (!snapshot.engineAvailable) {
                append("内核不可用，当前仅显示系统真实读数")
            } else {
                append("已用 ")
                append(used.formatBytes())
                append(" / ")
                append(snapshot.storage.totalBytes.formatBytes())
            }
            if (suggestion != null) {
                append(" · ")
                append(suggestion.title)
            }
        }
        return score
    }

    private fun lastCleanMs(): Long? = null

    private fun rootPath(): String =
        Environment.getExternalStorageDirectory()?.absolutePath
            ?: app.getExternalFilesDir(null)?.absolutePath
            ?: "/storage/emulated/0"
}
