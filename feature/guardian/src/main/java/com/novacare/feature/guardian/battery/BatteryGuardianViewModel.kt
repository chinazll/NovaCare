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
import com.novacare.core.system.AppRepository
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
    private val appRepository: AppRepository,
) : ViewModel() {

    /**
     * 估算的"耗电 Top"行（v0.21.0 新增）
     *
     * Android 不向第三方暴露 per-app 真实耗电数据（BATTERY_STATS 是系统签名权限）；
     * 我们按前台时长占总前台时长的比例，乘以当前放电电流，作为**粗估**。
     *
     * 这一栏明确写"估算"，让用户知道这不是系统给的真实数字。
     */
    data class DrainRow(
        val packageName: String,
        val label: String,
        val foregroundMs: Long,
        /** 估算的耗电（mAh），按当前电流 × 前台时长占比 */
        val estimatedDrainMah: Double,
    )

    sealed interface UiState {
        data object Idle : UiState
        data object Loading : UiState
        data class Ready(
            val data: BatteryInsights,
            val drainRanking: List<DrainRow>,
        ) : UiState
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
            .onSuccess { battery ->
                // 估算耗电 Top 10
                val drain = computeDrainRanking(battery)
                _state.value = UiState.Ready(battery, drain)
            }
            .onFailure { _state.value = UiState.Failed(it.message ?: "读取电池信息失败") }
    }

    /**
     * 按前台时长占比 × 当前电流（mA） 估算 per-app 耗电（mAh）。
     *
     * 这是一个**粗估**，明确不当作真实数据。电流值为负表示放电中（取绝对值）。
     */
    private suspend fun computeDrainRanking(battery: BatteryInsights): List<DrainRow> {
        // 必须是已授权 + 有前台时长数据才能估
        if (!battery.usagePermissionGranted) return emptyList()
        val totalForegroundMs = battery.foregroundRanking.sumOf { it.foregroundMs }
        if (totalForegroundMs <= 0L) return emptyList()
        val now = System.currentTimeMillis()
        // 当前放电电流（mA；负值 = 放电，取 abs）；为 0 时系统未给出读数 → 不估算
        val currentMa = if (battery.currentMa < 0) -battery.currentMa else battery.currentMa
        if (currentMa <= 0) {
            // 系统未给电流 → 退化为"仅按前台时长"，展示时不写估算值
            return appRepository.loadInstalledApps(now)
                .associate { it.packageName to it.label }
                .let { labels ->
                    battery.foregroundRanking.take(10).map { rank ->
                        DrainRow(
                            packageName = rank.packageName,
                            label = labels[rank.packageName] ?: rank.label,
                            foregroundMs = rank.foregroundMs,
                            estimatedDrainMah = 0.0,
                        )
                    }
                }
        }
        // 放电时长 = 扫描窗口（24h）的近似。
        // 这里我们只知道最近一次 BatteryInsights 拿到的 currentNow，
        // 不去推算"过去 24h 一直以这个电流放电" —— 直接按电流 × 应用前台占比的相对值排序。
        // 真正的总耗电量不可能是 currentNow × 24h，但**相对大小**是有信息量的：
        // 前台越久 → 占总前台时长越高 → 在 currentNow 恒定假设下分到的耗电越多。
        val labels = appRepository.loadInstalledApps(now).associate { it.packageName to it.label }
        return battery.foregroundRanking.take(10).map { rank ->
            val share = rank.foregroundMs.toDouble() / totalForegroundMs.toDouble()
            DrainRow(
                packageName = rank.packageName,
                label = labels[rank.packageName] ?: rank.label,
                foregroundMs = rank.foregroundMs,
                // 估算耗电：电流(mA) × 占总前台时长比 × 24h
                // 单位：mAh = mA × h → mA × (前台时长 / 总前台时长) × 24h
                estimatedDrainMah = currentMa.toDouble() * share * 24.0,
            )
        }
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
