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
import com.novacare.core.engine.NovaEngine
import com.novacare.core.model.CleanPlan
import com.novacare.core.model.CleanResult
import com.novacare.core.model.HealthScore
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
 * 蓝图硬指标：从打开到完成 ≤ 2 次点击、≤ 10 秒。
 * 因此扫描只在点击后跑一次，结果缓存在 [DeviceSnapshotCache]，其它页面直接复用。
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
) : ViewModel() {

    sealed interface UiState {
        data object Idle : UiState
        data object Scanning : UiState
        data class Preview(val plan: CleanPlan, val score: HealthScore) : UiState
        data object Executing : UiState
        data class Done(val result: CleanResult, val score: HealthScore) : UiState
        data class Error(val message: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _score = MutableStateFlow<HealthScore?>(null)
    val score: StateFlow<HealthScore?> = _score.asStateFlow()

    private val _summary = MutableStateFlow("")
    val summary: StateFlow<String> = _summary.asStateFlow()

    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    private var currentPlan: CleanPlan? = null

    init {
        viewModelScope.launch {
            val snapshot = cache.last ?: scan(rootPath())
            cache.put(snapshot)
            refreshScore(snapshot)
        }
    }

    fun onOptimizeClick() {
        viewModelScope.launch {
            _state.value = UiState.Scanning
            val started = System.currentTimeMillis()
            runCatching {
                cache.last ?: scan(rootPath())
            }.onSuccess { snapshot ->
                cache.put(snapshot)
                val duration = System.currentTimeMillis() - started
                val plan = buildPlan(snapshot, duration)
                currentPlan = plan
                _selected.value = plan.defaultSelected
                _score.value = refreshScore(snapshot)
                _state.value = UiState.Preview(plan, requireNotNull(_score.value))
            }.onFailure { e ->
                _state.value = UiState.Error(e.message ?: "扫描失败")
            }
        }
    }

    fun toggle(key: String) {
        val current = _selected.value.toMutableSet()
        if (!current.remove(key)) current.add(key)
        _selected.value = current
    }

    fun onConfirm() {
        val plan = currentPlan ?: return
        viewModelScope.launch {
            _state.value = UiState.Executing
            val advanced = settings.settings.first().advancedMode
            val result = executePlan(plan, _selected.value, advanced)
            cache.last?.let { _score.value = refreshScore(it) }
            _state.value = UiState.Done(result, requireNotNull(_score.value))
        }
    }

    fun reset() {
        _state.value = UiState.Idle
        currentPlan = null
        _selected.value = emptySet()
    }

    private fun refreshScore(snapshot: DeviceSnapshot): HealthScore {
        val batteryScore = runCatching { null }.getOrNull()
        val score = healthScore(snapshot, batteryScore)
        _score.value = score
        val reclaimable = snapshot.junk?.safeBytes ?: 0L
        val suggestion = SuggestionEngine.suggest(lastCleanMs(), reclaimable, snapshot.nowMs)
        _summary.value = buildString {
            if (!snapshot.engineAvailable) append("Rust 引擎不可用，以下为系统真实读数（不做估算）")
            else append("已用 ${(snapshot.storage.totalBytes - snapshot.storage.availableBytes).formatBytes()}")
            if (!snapshot.usagePermissionGranted) append(" · 未授权使用情况访问，使用频率相关结论置信度较低")
            if (suggestion != null) append(" · ${suggestion.title}")
        }
        return score
    }

    private fun lastCleanMs(): Long? = null

    fun engineVersion(): String = engine.version()

    private fun rootPath(): String =
        Environment.getExternalStorageDirectory()?.absolutePath
            ?: app.getExternalFilesDir(null)?.absolutePath
            ?: "/storage/emulated/0"
}
