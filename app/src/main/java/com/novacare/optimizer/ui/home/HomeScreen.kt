package com.novacare.optimizer.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacare.optimizer.core.DeviceRepository
import com.novacare.optimizer.core.HealthScorer
import com.novacare.optimizer.ui.components.*
import com.novacare.optimizer.ui.theme.OneUiSpacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ===== MVI 模式 =====

data class HomeState(
    val loading: Boolean = true,
    val score: Int = 0,
    val verdict: String = "正在评估",
    val storage: HealthItem = HealthItem(),
    val ram: HealthItem = HealthItem(),
    val battery: HealthItem = HealthItem(),
    val appSafety: HealthItem = HealthItem(),
    val optimizing: Boolean = false,
    val optimizeMessage: String? = null,
    val errorMessage: String? = null,
)

data class HealthItem(
    val title: String = "",
    val value: String = "--",
    val ratio: Float = 0f,
    val color: Color = Color.White,
    val icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Rounded.Storage,
    val tint: Color = Color.White,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: DeviceRepository,
    private val scorer: HealthScorer,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, errorMessage = null) }
            try {
                val status = repo.getDeviceStatus()
                val apps = repo.scanApps()
                val result = scorer.evaluate(
                    usedStorageRatio = (status.usedStorageGb / status.totalStorageGb.coerceAtLeast(0.001f)),
                    usedRamRatio = (status.usedRamMb.toFloat() / status.totalRamMb.coerceAtLeast(1L)),
                    batteryLevel = status.batteryLevel,
                    batteryTemp = status.batteryTemperature,
                    heavyCacheApps = apps.count { it.cacheSizeMb > 200f },
                    frozenCount = 0,
                )
                _state.update {
                    it.copy(
                        loading = false,
                        score = result.totalScore,
                        verdict = result.verdict,
                        storage = HealthItem(
                            title = "存储",
                            value = "%.1f / %.0f GB".format(status.usedStorageGb, status.totalStorageGb),
                            ratio = (status.usedStorageGb / status.totalStorageGb.coerceAtLeast(0.001f)).coerceIn(0f, 1f),
                            color = scoreColor(result.storageScore),
                            icon = Icons.Rounded.Storage,
                            tint = Color(0xFF5B8DEF),
                        ),
                        ram = HealthItem(
                            title = "内存",
                            value = "%d MB / %d MB".format(status.usedRamMb, status.totalRamMb),
                            ratio = (status.usedRamMb.toFloat() / status.totalRamMb.coerceAtLeast(1L)).coerceIn(0f, 1f),
                            color = scoreColor(result.ramScore),
                            icon = Icons.Rounded.Memory,
                            tint = Color(0xFF9B6BDF),
                        ),
                        battery = HealthItem(
                            title = "电池",
                            value = "${status.batteryLevel}%",
                            ratio = status.batteryLevel / 100f,
                            color = scoreColor(result.batteryScore),
                            icon = Icons.Rounded.BatteryChargingFull,
                            tint = Color(0xFF2FA36B),
                        ),
                        appSafety = HealthItem(
                            title = "应用防护",
                            value = if (apps.isEmpty()) "无异常" else "${apps.size} 个应用已扫描",
                            ratio = (apps.size.coerceAtMost(100) / 100f),
                            color = scoreColor(result.appScore),
                            icon = Icons.Rounded.Security,
                            tint = Color(0xFFE8912D),
                        ),
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        errorMessage = "读取设备信息失败：${e.message ?: "未知错误"}",
                    )
                }
            }
        }
    }

    fun oneTapOptimize() {
        viewModelScope.launch {
            _state.update { it.copy(optimizing = true, optimizeMessage = "正在扫描垃圾文件…") }
            try {
                val junk = repo.scanJunk()
                _state.update { it.copy(optimizeMessage = "发现 ${junk.size} 项可清理…") }
                val safeJunk = junk.filter { it.isSafe }
                val freedBytes = repo.cleanJunk(safeJunk)
                val freedMb = freedBytes / 1024f / 1024f
                _state.update { it.copy(optimizeMessage = "已释放 %.1f MB".format(freedMb)) }
                kotlinx.coroutines.delay(900)
                _state.update { it.copy(optimizing = false, optimizeMessage = null) }
                refresh()
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        optimizing = false,
                        optimizeMessage = "优化失败：${e.message ?: "未知错误"}",
                    )
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit = {},
    vm: HomeViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scroll)
            .padding(bottom = OneUiSpacing.xxxl),
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

        OneUiLargeHeader(
            title = "设备管家",
            subtitle = "全部分析在本机完成 · 零网络",
        )

        Spacer(Modifier.height(OneUiSpacing.lg))
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            ScoreRing(
                score = if (state.loading) 0 else state.score,
                label = "设备得分",
            )
        }
        Spacer(Modifier.height(OneUiSpacing.md))
        Text(
            text = if (state.loading) "正在评估..." else state.verdict,
            style = MaterialTheme.typography.titleMedium,
            color = if (state.loading) MaterialTheme.colorScheme.onSurfaceVariant else scoreColor(state.score),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(OneUiSpacing.xxl))

        state.errorMessage?.let { msg ->
            Box(Modifier.padding(horizontal = OneUiSpacing.xl)) {
                OneUiCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        msg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            Spacer(Modifier.height(OneUiSpacing.md))
        }

        SectionTitle("健康状况")
        Column(
            modifier = Modifier.padding(horizontal = OneUiSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(OneUiSpacing.md),
        ) {
            HealthRow(item = state.storage, onClick = { onNavigate("storage") })
            HealthRow(item = state.ram, onClick = { onNavigate("storage") })
            HealthRow(item = state.battery, onClick = { onNavigate("battery") })
            HealthRow(item = state.appSafety, onClick = { onNavigate("apps") })
        }

        Spacer(Modifier.height(OneUiSpacing.xxl))

        AnimatedVisibility(
            visible = state.optimizeMessage != null,
            enter = fadeIn() + expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OneUiSpacing.xl)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(OneUiSpacing.lg),
            ) {
                Text(
                    text = state.optimizeMessage ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        if (state.optimizeMessage != null) Spacer(Modifier.height(OneUiSpacing.md))

        PillButton(
            text = when {
                state.optimizing -> "正在优化..."
                state.score == 0 -> "扫描设备"
                else -> "立即优化"
            },
            onClick = { vm.oneTapOptimize() },
            enabled = !state.optimizing,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OneUiSpacing.xl),
        )

        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable
private fun HealthRow(
    item: HealthItem,
    onClick: () -> Unit,
) {
    OneUiCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SquircleIconBg(
                icon = item.icon,
                tint = item.tint,
                size = 48.dp,
            )
            Spacer(Modifier.width(OneUiSpacing.lg))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(OneUiSpacing.md))
        LinearProgressIndicator(
            progress = { item.ratio },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = item.color,
            trackColor = item.color.copy(alpha = 0.12f),
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
    }
}