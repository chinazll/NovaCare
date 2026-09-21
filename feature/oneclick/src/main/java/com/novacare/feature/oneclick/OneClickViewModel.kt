package com.novacare.feature.oneclick

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacare.core.domain.OneClickCheckUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 一键体检 ViewModel
 *
 * 状态机：
 *   - [Idle]    初始态；UI 显示「开始体检」按钮
 *   - [Running] 扫描中；UI 展示进度（已完成 step 数 / 总数）
 *   - [Done]    体检完成；UI 展示分数 + 步骤结果 + 问题清单
 *   - [Failed]  整个调用失败；UI 展示错误信息
 */
@HiltViewModel
class OneClickViewModel @Inject constructor(
    private val useCase: OneClickCheckUseCase,
) : ViewModel() {

    sealed interface UiState {
        data object Idle : UiState
        data class Running(val completed: Int, val total: Int) : UiState
        data class Done(val report: OneClickCheckUseCase.Report) : UiState
        data class Failed(val message: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun runScan(rootPath: String) {
        if (_state.value is UiState.Running) return
        viewModelScope.launch {
            _state.value = UiState.Running(completed = 0, total = OneClickCheckUseCase.Step.values().size)
            runCatching {
                useCase(rootPath) { step ->
                    // 每步完成后立即更新进度
                    val current = _state.value
                    val completed = when (current) {
                        is UiState.Running -> current.completed + 1
                        else -> 1
                    }
                    _state.value = UiState.Running(completed = completed, total = currentTotal())
                }
            }.onSuccess { _state.value = UiState.Done(it) }
                .onFailure { _state.value = UiState.Failed(it.message ?: "体检失败") }
        }
    }

    fun reset() {
        _state.value = UiState.Idle
    }

    private fun currentTotal(): Int = OneClickCheckUseCase.Step.values().size
}
