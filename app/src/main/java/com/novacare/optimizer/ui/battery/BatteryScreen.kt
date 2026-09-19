package com.novacare.optimizer.ui.battery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DeviceThermostat
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacare.optimizer.core.DeviceRepository
import com.novacare.optimizer.ui.components.*
import com.novacare.optimizer.ui.theme.NovaSemanticColors
import com.novacare.optimizer.ui.theme.OneUiSpacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BatteryState(
    val level: Int = 100,
    val temperature: Float = 25f,
    val isCharging: Boolean = false,
    val voltage: Int = 0,
    val healthScore: Int = 100,
    /** 充电周期；-1 = 未知（真实周期需 root，本项目不伪造） */
    val cycleCount: Int = -1,
    val suggestions: List<String> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class BatteryViewModel @Inject constructor(
    private val repo: DeviceRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(BatteryState())
    val state: StateFlow<BatteryState> = _state.asStateFlow()

    init { refresh() }

    /**
     * 修复 P1-7 + P1-8：
     * - P1-7：此前这里 `HealthScorer()` 直接 new 了一个声明为 @Singleton 的对象，
     *   绕过了 Hilt 依赖注入，破坏 DI 一致性。
     * - P1-8：此前 Kotlin 侧用一套自己的阈值算电池分，与 Rust `battery_monitor` 的
     *   算法完全不同（Kotlin：<60% 得 100 分；Rust：<20% 扣 30 分），
     *   同一个「电池健康度」会出现两个矛盾数字。
     *
     * 现在电池分析**统一走 Rust 引擎**，Kotlin 侧不再有任何重复算法。
     */
    fun refresh() {
        viewModelScope.launch {
            try {
                val status = repo.getDeviceStatus()
                val report = repo.analyzeBattery()
                _state.update {
                    it.copy(
                        level = status.batteryLevel,
                        temperature = status.batteryTemperature,
                        isCharging = status.isCharging,
                        voltage = report?.voltage ?: 0,
                        // 引擎不可用时退回中性值，绝不显示为 0 分造成恐慌
                        healthScore = report?.healthScore ?: status.batteryHealthScore,
                        cycleCount = report?.cycleCount ?: -1,
                        suggestions = report?.suggestions ?: emptyList(),
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = "读取失败：${e.message ?: "未知"}") }
            }
        }
    }
}

@Composable
fun BatteryScreen(vm: BatteryViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val color = NovaSemanticColors.scoreColor(state.healthScore)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(bottom = OneUiSpacing.xxxl),
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        OneUiLargeHeader(
            title = "电池卫士",
            subtitle = state.errorMessage ?: if (state.isCharging) "正在充电" else "实时监测",
        )
        Spacer(Modifier.height(OneUiSpacing.lg))

        Column(
            modifier = Modifier.padding(horizontal = OneUiSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(OneUiSpacing.md),
        ) {
            OneUiCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Bolt,
                        null,
                        tint = color,
                        modifier = Modifier.size(36.dp),
                    )
                    Spacer(Modifier.width(OneUiSpacing.lg))
                    Column {
                        Text(
                            "${state.level}%",
                            style = MaterialTheme.typography.displaySmall,
                            color = color,
                        )
                        Text(
                            if (state.isCharging) "充电中" else "未充电",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            OneUiCard(modifier = Modifier.fillMaxWidth()) {
                OneUiListRow(
                    title = "电池温度",
                    subtitle = "%.1f ℃ · %s".format(
                        state.temperature,
                        if (state.temperature < 38f) "正常" else "偏高"
                    ),
                    leading = Icons.Rounded.DeviceThermostat,
                    leadingTint = if (state.temperature < 38f) NovaSemanticColors.Success
                    else NovaSemanticColors.Warning,
                    showChevron = false,
                )
                OneUiListRow(
                    title = "健康度",
                    subtitle = "${state.healthScore} / 100",
                    leading = Icons.Rounded.Timeline,
                    leadingTint = color,
                    showChevron = false,
                )
                OneUiListRow(
                    title = "充电周期",
                    // 明确显示「未知」，而不是伪造一个 0
                    subtitle = if (state.cycleCount >= 0) "${state.cycleCount} 次" else "未知（需 root 读取，本项目不猜测）",
                    leading = Icons.Rounded.Bolt,
                    leadingTint = MaterialTheme.colorScheme.outline,
                    showChevron = false,
                )
            }

            OneUiCard(modifier = Modifier.fillMaxWidth()) {
                Text("优化建议", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(OneUiSpacing.sm))
                val tips = state.suggestions.ifEmpty {
                    listOf("电池状态良好，继续保持")
                }
                tips.forEach { tip ->
                    Text(
                        "• $tip",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}
