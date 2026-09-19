package com.novacare.feature.clean

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacare.core.domain.BuildOptimizePlanUseCase
import com.novacare.core.domain.DeviceSnapshotCache
import com.novacare.core.domain.ExecutePlanUseCase
import com.novacare.core.domain.ScanDeviceUseCase
import com.novacare.core.model.CleanPlan
import com.novacare.core.model.CleanRisk
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CleanViewModel @Inject constructor(
    private val scan: ScanDeviceUseCase,
    private val buildPlan: BuildOptimizePlanUseCase,
    private val executePlan: ExecutePlanUseCase,
    private val cache: DeviceSnapshotCache,
    private val settings: com.novacare.core.data.SettingsRepository,
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Ready(val plan: CleanPlan, val engineAvailable: Boolean) : UiState
        data class Failed(val message: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    private val _includeRisky = MutableStateFlow(false)
    val includeRisky: StateFlow<Boolean> = _includeRisky.asStateFlow()

    fun load(rootPath: String) {
        viewModelScope.launch {
            runCatching {
                cache.last ?: scan(rootPath)
            }.onSuccess { snapshot ->
                cache.put(snapshot)
                val plan = buildPlan(snapshot, 0L, _includeRisky.value)
                _selected.value = plan.defaultSelected
                _state.value = UiState.Ready(plan, snapshot.engineAvailable)
            }.onFailure {
                _state.value = UiState.Failed(it.message ?: "扫描失败")
            }
        }
    }

    fun toggle(key: String) {
        val current = _selected.value.toMutableSet()
        if (!current.remove(key)) current.add(key)
        _selected.value = current
    }

    fun setIncludeRisky(include: Boolean) {
        _includeRisky.value = include
        cache.last?.let { snapshot ->
            val plan = buildPlan(snapshot, 0L, include)
            _selected.value = plan.defaultSelected
            _state.value = UiState.Ready(plan, snapshot.engineAvailable)
        }
    }

    fun execute() {
        val state = _state.value as? UiState.Ready ?: return
        viewModelScope.launch {
            val advanced = settings.settings.first().advancedMode
            val result = executePlan(state.plan, _selected.value, advanced)
            _state.value = UiState.Ready(state.plan.copy(advices = emptyList()), state.engineAvailable)
            _lastResult.value = result
        }
    }

    private val _lastResult = MutableStateFlow<com.novacare.core.model.CleanResult?>(null)
    val lastResult: StateFlow<com.novacare.core.model.CleanResult?> = _lastResult.asStateFlow()
}

fun com.novacare.core.model.CleanAdvice.isRisky(): Boolean = risk == CleanRisk.RISKY
