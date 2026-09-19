package com.novacare.optimizer.ui.home

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacare.optimizer.core.DeviceRepository
import com.novacare.optimizer.core.HealthScorer
import com.novacare.optimizer.ui.components.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val loading: Boolean = true,
    val score: Int = 0,
    val verdict: String = "",
    val storage: Pair<Float, Float> = 0f to 1f,   // used, total (GB)
    val ram: Pair<Long, Long> = 0L to 1L,
    val battery: Int = 100,
    val optimizing: Boolean = false,
    val optimizeMessage: String? = null,
    val needsUsageStatsPermission: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: DeviceRepository,
    private val scorer: HealthScorer,
    private val permissions: com.novacare.optimizer.core.PermissionHelper,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state = _state.asStateFlow()

    init {
        refresh()
        _state.value = _state.value.copy(needsUsageStatsPermission = !permissions.hasUsageStatsPermission())
    }

    fun refresh() {
        viewModelScope.launch {
            val s = repo.getDeviceStatus()
            val result = scorer.evaluate(
                usedStorageRatio = s.usedStorageGb / s.totalStorageGb,
                usedRamRatio = s.usedRamMb.toFloat() / s.totalRamMb,
                batteryLevel = s.batteryLevel,
                batteryTemp = s.batteryTemperature,
                heavyCacheApps = 2, // 由应用扫描页共享，此处快速估算
                frozenCount = 0,
            )
            _state.value = HomeUiState(
                loading = false,
                score = result.totalScore,
                verdict = result.verdict,
                storage = s.usedStorageGb to s.totalStorageGb,
                ram = s.usedRamMb to s.totalRamMb,
                battery = s.batteryLevel,
            )
        }
    }

    /** 一键优化：清缓存 → 内存提示 → 刷新得分 */
    fun optimize() {
        viewModelScope.launch {
            _state.value = _state.value.copy(optimizing = true, optimizeMessage = "正在扫描垃圾文件…")
            val junk = repo.scanJunk()
            _state.value = _state.value.copy(optimizeMessage = "正在清理 ${junk.size} 项…")
            val freedMb = repo.cleanJunk(junk.filter { it.isSafe }) / 1024f
            _state.value = _state.value.copy(optimizeMessage = "已释放 %.2f GB".format(freedMb))
            kotlinx.coroutines.delay(600)
            _state.value = _state.value.copy(optimizing = false)
            refresh()
        }
    }
}

/**
 * 首页：One UI 设备管家（Device Care）灵魂重构版
 * 结构：观看区（得分环）→ 四维模块 → 操作区（优化按钮下沉到拇指区）
 */
@Composable
fun HomeScreen(vm: HomeViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val scroll = rememberScrollState()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scroll)
            .padding(bottom = 32.dp),
    ) {
        // ---- 观看区：大标题 + 得分环 ----
        OneUiLargeHeader(title = "设备管家", subtitle = "全部分析在本机完成，不上传任何数据")
        Spacer(Modifier.height(8.dp))

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ScoreRing(score = state.score)
        }
        Text(
            text = state.verdict,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = scoreColor(state.score),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 12.dp),
        )

        Spacer(Modifier.height(24.dp))

        // 权限申请卡片（透明披露原则）
        if (state.needsUsageStatsPermission) {
            val localContext = androidx.compose.ui.platform.LocalContext.current
            Column(Modifier.padding(horizontal = 20.dp)) {
                OneUiCard(Modifier.fillMaxWidth()) {
                    Text(
                        "需要使用情况权限",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "用于识别'不常用应用'。本权限仅读取使用统计，不会上传任何数据。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.material3.TextButton(
                        onClick = {
                            try {
                                localContext.startActivity(
                                    android.content.Intent(
                                        android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS
                                    ).apply {
                                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                )
                            } catch (_: Exception) { /* 跳转失败静默 */ }
                        },
                    ) {
                        Text("去设置授权")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // ---- 四维模块（One UI 卡片语法：26dp 圆角 + 留白分隔）----
        SectionTitle("健康状况")
        Column(
            Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HealthCard(
                title = "存储",
                value = "%.1f GB / %.0f GB".format(state.storage.first, state.storage.second),
                icon = Icons.Rounded.Storage,
                tint = Color(0xFF5B8DEF),
                ratio = state.storage.first / state.storage.second,
                onClick = { /* 导航至存储管家 */ },
            )
            HealthCard(
                title = "内存",
                value = "%d MB / %d MB".format(state.ram.first, state.ram.second),
                icon = Icons.Rounded.Memory,
                tint = Color(0xFF9B6BDF),
                ratio = state.ram.first.toFloat() / state.ram.second,
                onClick = { /* 导航至内存管理 */ },
            )
            HealthCard(
                title = "电池",
                value = "${state.battery}%",
                icon = Icons.Rounded.BatteryChargingFull,
                tint = Color(0xFF2FA36B),
                ratio = state.battery / 100f,
                onClick = { /* 导航至电池卫士 */ },
            )
            HealthCard(
                title = "应用防护",
                value = "垃圾与臃肿应用检测",
                icon = Icons.Rounded.Security,
                tint = Color(0xFFE8912D),
                ratio = null,
                onClick = { /* 导航至应用管理 */ },
            )
        }

        Spacer(Modifier.height(28.dp))

        // ---- 操作区：按钮下沉，拇指可及 ----
        if (state.optimizeMessage != null) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Text(
                    text = state.optimizeMessage!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 12.dp),
                )
            }
        }
        Box(Modifier.fillMaxWidth()) {
            PillButton(
                text = if (state.optimizing) "正在优化…" else "立即优化",
                onClick = { if (!state.optimizing) vm.optimize() },
                enabled = !state.optimizing,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            )
        }
    }
}

@Composable
private fun HealthCard(
    title: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    ratio: Float?,
    onClick: () -> Unit,
) {
    OneUiCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(52.dp)
                    .background(tint.copy(alpha = 0.14f), MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = title, tint = tint, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
            )
        }
        if (ratio != null) {
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { ratio.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                trackColor = tint.copy(alpha = 0.12f),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
    }
}
