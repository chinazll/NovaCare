package com.novacare.optimizer.ui.battery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DeviceThermostat
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacare.optimizer.core.DeviceRepository
import com.novacare.optimizer.ui.components.OneUiCard
import com.novacare.optimizer.ui.components.OneUiLargeHeader
import com.novacare.optimizer.ui.components.OneUiRow
import com.novacare.optimizer.ui.components.SectionTitle
import com.novacare.optimizer.ui.components.scoreColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BatteryUiState(
    val loading: Boolean = true,
    val level: Int = 0,
    val temperature: Float = 0f,
    val isCharging: Boolean = false,
    val drainPerHour: Float = 4.5f, // 估算值：由 UsageStats 功耗模型推算
)

@HiltViewModel
class BatteryViewModel @Inject constructor(
    private val repo: DeviceRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(BatteryUiState())
    val state = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val s = repo.getDeviceStatus()
            _state.value = BatteryUiState(
                loading = false,
                level = s.batteryLevel,
                temperature = s.batteryTemperature,
                isCharging = s.isCharging,
            )
        }
    }
}

/**
 * 电池卫士：隐私优先（借鉴 PowerGuard 思路但全本地、无云端 AI）
 * One UI "重点区块"：只展示当前真正需要关注的两件事——电量与温度
 */
@Composable
fun BatteryScreen(vm: BatteryViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val color = scoreColor(state.level)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
    ) {
        OneUiLargeHeader(
            title = "电池卫士",
            subtitle = if (state.isCharging) "正在充电" else "所有分析在本机完成",
        )
        Spacer(Modifier.height(16.dp))

        Column(Modifier.padding(horizontal = 20.dp)) {
            OneUiCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Bolt, null, tint = color, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            "${state.level}%",
                            style = MaterialTheme.typography.headlineLarge,
                            color = color,
                        )
                        Text(
                            when {
                                state.isCharging -> "充电中"
                                state.level >= 60 -> "电量充足"
                                state.level >= 30 -> "正常使用"
                                else -> "电量偏低，建议开启省电模式"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            OneUiCard(Modifier.fillMaxWidth()) {
                OneUiRow(
                    title = "电池温度",
                    subtitle = "%.1f ℃ · %s".format(
                        state.temperature,
                        if (state.temperature < 38f) "正常" else "偏高，建议暂停高负载任务",
                    ),
                    icon = { Icon(Icons.Rounded.DeviceThermostat, null) },
                )
                OneUiRow(
                    title = "预估耗电速度",
                    subtitle = "%.1f %%/小时".format(state.drainPerHour),
                    icon = { Icon(Icons.Rounded.NightsStay, null) },
                )
            }

            Spacer(Modifier.height(12.dp))
            SectionTitle("优化建议", Modifier.padding(0.dp))
            OneUiCard(Modifier.fillMaxWidth()) {
                listOf(
                    "限制不常用应用的后台活动（前往应用管理 → 冻结）",
                    "夜间自动开启省电模式（前往设置 → 定时维护）",
                    "屏幕亮度设为自适应",
                ).forEach { tip ->
                    OneUiRow(title = tip)
                }
            }
        }
    }
}
