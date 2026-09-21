package com.novacare.feature.guardian.battery

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacare.core.domain.BatteryInsights
import com.novacare.core.domain.BatteryInsightsUseCase
import com.novacare.core.domain.GuardianAction
import com.novacare.core.domain.GuardianAdvice
import com.novacare.core.system.SystemPermissions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 电池守护 ViewModel
 *
 * 数据全部真实：电量/温度/电压/电流来自 BatteryManager，前台时长来自
 * UsageStatsManager，健康评分来自 Rust 内核。**没有一项是估算的。**
 *
 * 建议的动作一律是"跳到系统设置页让用户自己确认"——
 * 第三方应用既不能开省电模式，也不能限制别的应用耗电。
 */
@HiltViewModel
class BatteryGuardianViewModel @Inject constructor(
    private val insightsUseCase: BatteryInsightsUseCase,
    private val permissions: SystemPermissions,
) : ViewModel() {

    sealed interface UiState {
        data object Idle : UiState
        data object Loading : UiState
        data class Ready(val data: BatteryInsights) : UiState
        data class Failed(val message: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                loadInternal()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun load(force: Boolean = true) {
        if (!force && _state.value is UiState.Ready) return
        viewModelScope.launch { loadInternal() }
    }

    private suspend fun loadInternal() {
        if (_state.value !is UiState.Ready) _state.value = UiState.Loading
        runCatching { insightsUseCase() }
            .onSuccess { _state.value = UiState.Ready(it) }
            .onFailure { _state.value = UiState.Failed(it.message ?: "读取电池信息失败") }
    }

    fun statusLabel(status: String): String = insightsUseCase.statusLabel(status)
    fun healthLabel(health: String): String = insightsUseCase.healthLabel(health)

    /**
     * 执行建议里的动作。
     *
     * 注意：这里**没有**任何"替用户改系统设置"的行为 ——
     * Android 不允许第三方开省电模式/限制后台，能做的只有把用户送到正确的页面。
     */
    fun runAction(advice: GuardianAdvice) {
        val intent = when (advice.action) {
            GuardianAction.NONE -> return
            GuardianAction.BATTERY_SAVER_SETTINGS ->
                Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)

            GuardianAction.APP_DETAILS -> {
                val pkg = advice.targetPackage ?: return
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:$pkg"))
            }

            GuardianAction.USAGE_ACCESS_SETTINGS -> permissions.usageStatsSettingsIntent()
            GuardianAction.ALL_FILES_SETTINGS -> permissions.allFilesAccessIntent()
            GuardianAction.APPLICATION_SETTINGS -> Intent(Settings.ACTION_APPLICATION_SETTINGS)
        }
        permissions.launchSettings(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private companion object {
        const val POLL_INTERVAL_MS = 20_000L
    }
}
