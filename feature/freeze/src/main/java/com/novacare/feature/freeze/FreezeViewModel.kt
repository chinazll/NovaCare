package com.novacare.feature.freeze

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacare.core.domain.DeviceSnapshotCache
import com.novacare.core.domain.FreezeUseCase
import com.novacare.core.domain.ScanDeviceUseCase
import com.novacare.core.model.FreezeCandidate
import com.novacare.core.model.FreezeResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FreezeViewModel @Inject constructor(
    private val scan: ScanDeviceUseCase,
    private val cache: DeviceSnapshotCache,
    private val freeze: FreezeUseCase,
    private val settings: com.novacare.core.data.SettingsRepository,
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Ready(val candidates: List<FreezeCandidate>, val advancedMode: Boolean) : UiState
        data class Failed(val message: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _results = MutableStateFlow<List<FreezeResult>>(emptyList())
    val results: StateFlow<List<FreezeResult>> = _results.asStateFlow()

    fun load(rootPath: String) {
        viewModelScope.launch {
            runCatching { cache.last ?: scan(rootPath) }
                .onSuccess { snapshot ->
                    cache.put(snapshot)
                    val advanced = settings.settings.first().advancedMode
                    _state.value = UiState.Ready(freeze.candidates(snapshot, false), advanced)
                }
                .onFailure { _state.value = UiState.Failed(it.message ?: "扫描失败") }
        }
    }

    fun freeze(packageName: String) {
        viewModelScope.launch {
            val advanced = settings.settings.first().advancedMode
            val result = freeze.freeze(packageName, advanced)
            _results.value = _results.value + result
        }
    }

    fun unfreeze(packageName: String) {
        viewModelScope.launch {
            val result = freeze.unfreeze(packageName)
            _results.value = _results.value + result
        }
    }
}
