package com.novacare.feature.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacare.core.domain.AssistantOutcome
import com.novacare.core.domain.AssistantUseCase
import com.novacare.core.domain.DeviceSnapshotCache
import com.novacare.core.domain.ScanDeviceUseCase
import com.novacare.core.model.ParsedIntent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val scan: ScanDeviceUseCase,
    private val cache: DeviceSnapshotCache,
    private val assistant: AssistantUseCase,
) : ViewModel() {

    data class Turn(val input: String, val intent: ParsedIntent?, val outcome: AssistantOutcome?)

    private val _turns = MutableStateFlow<List<Turn>>(emptyList())
    val turns: StateFlow<List<Turn>> = _turns.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun submit(text: String, rootPath: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _busy.value = true
            val snapshot = cache.last ?: scan(rootPath).also { cache.put(it) }
            val intent = assistant.parse(text, snapshot)
            val outcome = assistant.apply(intent, snapshot, advancedMode = false)
            _turns.value = _turns.value + Turn(text, intent, outcome)
            _busy.value = false
        }
    }
}
