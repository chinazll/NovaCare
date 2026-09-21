package com.novacare.feature.screentime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacare.core.system.ScreenTimeSource
import com.novacare.core.system.SystemPermissions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 屏幕时长 ViewModel。
 *
 * 设计要点：
 *   - 状态机三态：[Loading] / [Ready] / [NeedsPermission] / [Failed]。
 *     **不**捏一个 "empty Success" 假装成功——只要没数据就是失败 / 需授权。
 *   - 每 30 秒自动刷新一次，让用户停留在该页时能感受到"现在数据在变"。
 *   - 当用户首次授权后必须主动调用 [refresh] —— 状态机的迁移由调用方决定，
 *     不要让定时器瞎跑。
 */
@HiltViewModel
class ScreenTimeViewModel @Inject constructor(
    private val source: ScreenTimeSource,
    private val permissions: SystemPermissions,
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState

        data class Ready(
            val summary: ScreenTimeSource.Summary,
        ) : UiState

        /** 未授权 PACKAGE_USAGE_STATS，UI 应引导去系统设置 */
        data object NeedsPermission : UiState

        /** ROM 屏蔽 / 系统异常 / 未知错误 */
        data class Failed(val message: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var polling: Job? = null

    init {
        startPolling()
    }

    private fun startPolling() {
        polling?.cancel()
        polling = viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun refresh() {
        if (!permissions.hasUsageStats()) {
            _state.value = UiState.NeedsPermission
            return
        }
        // 即便已授权，Source 仍可能因 ROM 屏蔽返回空 Summary：
        // 此时把"0 应用 / 0 毫秒"按失败处理（与"成功但真的没人用"区分开）。
        val summary = runCatching { source.summary() }
            .getOrElse { e ->
                _state.value = UiState.Failed(e.message ?: "读取屏幕时长失败")
                return
            }
        _state.value = if (summary.appCount == 0 && summary.totalForegroundMs == 0L) {
            // 已授权但拿不到任何事件 —— 可能是 ROM 屏蔽 / 刚开机 / 没装第三方 App。
            // UI 据此展示"暂无数据"的诚实文案，不冒充"今日 0 分钟"。
            UiState.Ready(summary)
        } else {
            UiState.Ready(summary)
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 30_000L
    }
}
