package com.novacare.optimizer.ui.battery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DeviceThermostat
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacare.optimizer.core.DeviceRepository
import com.novacare.optimizer.core.HealthScorer
import com.novacare.optimizer.ui.components.*
import com.novacare.optimizer.ui.theme.OneUiSpacing
import com.novacare.optimizer.ui.theme.ScoreBad
import com.novacare.optimizer.ui.theme.ScoreGood
import com.novacare.optimizer.ui.theme.ScoreMid
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
    val healthScore: Int = 100,
    val errorMessage: String? = null,
)

@HiltViewModel
class BatteryViewModel @Inject constructor(
    private val repo: DeviceRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(BatteryState())
    val state: StateFlow<BatteryState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            try {
                val status = repo.getDeviceStatus()
                val score = HealthScorer().run {
                    val r = evaluate(
                        usedStorageRatio = 0f, usedRamRatio = 0f,
                        batteryLevel = status.batteryLevel,
                        batteryTemp = status.batteryTemperature,
                        heavyCacheApps = 0, frozenCount = 0,
                    )
                    r.batteryScore
                }
                _state.update {
                    it.copy(
                        level = status.batteryLevel,
                        temperature = status.batteryTemperature,
                        isCharging = status.isCharging,
                        healthScore = score,
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
    val color = when {
        state.healthScore >= 80 -> ScoreGood
        state.healthScore >= 50 -> ScoreMid
        else -> ScoreBad
    }

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
            // 电量卡
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
            // 详情卡
            OneUiCard(modifier = Modifier.fillMaxWidth()) {
                OneUiListRow(
                    title = "电池温度",
                    subtitle = "%.1f ℃ · %s".format(
                        state.temperature,
                        if (state.temperature < 38f) "正常" else "偏高"
                    ),
                    leading = Icons.Rounded.DeviceThermostat,
                    leadingTint = if (state.temperature < 38f) ScoreGood else ScoreMid,
                    showChevron = false,
                )
                OneUiListRow(
                    title = "健康度",
                    subtitle = "${state.healthScore} / 100",
                    leading = Icons.Rounded.NightsStay,
                    leadingTint = color,
                    showChevron = false,
                )
            }
            // 建议
            OneUiCard(modifier = Modifier.fillMaxWidth()) {
                Text("优化建议", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(OneUiSpacing.sm))
                val tips = buildList {
                    if (state.temperature >= 38f) add("电池温度偏高，建议暂停游戏等高负载任务")
                    if (state.level < 20) add("电量偏低，建议开启省电模式")
                    if (state.isCharging && state.level >= 80) add("电量已 80%+，可断开充电器延长电池寿命")
                    if (size == 0) add("电池状态良好，继续保持")
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