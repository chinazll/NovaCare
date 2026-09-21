package com.novacare.feature.traffic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacare.core.system.TrafficSource
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
 * 流量 ViewModel。
 *
 * 数据来源：
 *   - [TrafficSource.summary] —— 聚合 + per-UID + per-interface；
 *   - [TrafficSource.selfTraffic] —— 当前进程（NovaCare 自己）的实时上下行。
 *
 * 状态机：四态 —— Loading / Ready / Empty / Failed。
 * Empty 与 Ready 区分：源读到了至少一个真实数值（哪怕只有 1 字节）才算 Ready，
 * 否则视为 Empty（"刚开机 / 从未被网络使用过"）。
 */
@HiltViewModel
class TrafficViewModel @Inject constructor(
    private val source: TrafficSource,
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data object Empty : UiState
        data class Ready(
            val summary: TrafficSource.Summary,
            val self: TrafficSource.UidTraffic,
        ) : UiState
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
        runCatching {
            val summary = source.summary()
            val self = source.selfTraffic()
            // 设备开机以来**没有任何**网络活动 → Empty（不是 Ready）。
            // 注意：即使本应用没用流量，wlan0 也可能刚被系统用过几百字节 —
            // ——那种情况按 Ready 展示"几乎是 0"，不要让用户误以为网卡坏了。
            val anyTraffic = summary.totalBytes > 0L ||
                summary.interfaces.isNotEmpty() ||
                summary.topApps.isNotEmpty()
            _state.value = if (anyTraffic) {
                UiState.Ready(summary, self)
            } else {
                UiState.Empty
            }
        }.onFailure { e ->
            _state.value = UiState.Failed(e.message ?: "读取流量失败")
        }
    }

    private companion object {
        /** 流量变化较慢，60s 一次足矣；用户停留时能看到差值即"知道在变" */
        const val POLL_INTERVAL_MS = 60_000L
    }
}
