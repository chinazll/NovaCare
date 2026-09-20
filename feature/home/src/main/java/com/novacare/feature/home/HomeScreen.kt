package com.novacare.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.novacare.core.common.formatBytes
import com.novacare.core.domain.key
import com.novacare.core.system.MissingCapability
import com.novacare.ui.designsystem.AuroraBackground
import com.novacare.ui.designsystem.EmptyState
import com.novacare.ui.designsystem.EmptyTone
import com.novacare.ui.designsystem.HealthRing
import com.novacare.ui.designsystem.InlineNotice
import com.novacare.ui.designsystem.NovaCareTheme
import com.novacare.ui.designsystem.PrimaryAction
import com.novacare.ui.designsystem.RiskChip
import com.novacare.ui.designsystem.SectionHeader
import com.novacare.ui.designsystem.StatCard

/**
 * 首页（L1）。
 *
 * 布局节奏（自上而下信息密度递增）：
 *   1. 状态栏 —— 应用名 + 内核版本
 *   2. 健康环 —— 视觉主角，占据首屏上半部
 *   3. 引擎降级 / 授权引导 —— 缺什么就显示什么，每张卡都可直接跳转
 *   4. 四宫格 —— 存储 / 可清理 / 长期未用 / AI 助手 / 自动化 / 高级模式
 *   5. 主 CTA —— 唯一使用强调色的元素
 *
 * 关键设计决策：
 *   权限引导卡不是"错误提示"，而是**可操作的第一步**。
 *   上一版没有任何权限请求入口，用户装上后功能全部静默降级。
 *   现在把"缺权限"变成首屏的一部分，且每个都带跳转按钮。
 */
@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val score by viewModel.score.collectAsState()
    val missing by viewModel.missing.collectAsState()
    val engineAvailable by viewModel.engineAvailable.collectAsState()
    val engineVersion by viewModel.engineVersion.collectAsState()
    val overview by viewModel.overview.collectAsState()
    val selected by viewModel.selected.collectAsState()

    // 从系统设置页返回时重新检查权限状态
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshCapabilities()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 入场序列：健康环先入场，其余错峰跟随
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }

    AuroraBackground {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 28.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---------- 1. 状态栏 ----------
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "NovaCare",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = when {
                                !engineAvailable -> "内核降级模式 · 仅显示真实读数"
                                engineVersion.isBlank() -> "正在初始化内核…"
                                else -> engineVersion
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (engineAvailable) {
                                NovaCareTheme.colors.healthGood
                            } else {
                                NovaCareTheme.colors.riskCaution
                            },
                        )
                    }
                    Text(
                        text = "设置",
                        style = MaterialTheme.typography.labelMedium,
                        color = NovaCareTheme.colors.accent,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onNavigate("settings") },
                            )
                            .background(NovaCareTheme.colors.accent.copy(alpha = 0.10f))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }

            // ---------- 2. 健康环（视觉主角）----------
            item(key = "ring") {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedVisibility(
                        visible = revealed,
                        enter = fadeIn(tween(700)) + slideInVertically(
                            animationSpec = tween(700),
                            initialOffsetY = { it / 6 },
                        ),
                    ) {
                        HealthRing(
                            score = score?.total ?: 0,
                            verdict = score?.verdict ?: "正在读取设备状态…",
                            label = "健康分",
                        )
                    }
                }
            }

            // ---------- 3. 内核降级 / 授权引导 ----------
            if (!engineAvailable) {
                item(key = "engine-notice") {
                    InlineNotice(
                        text = "内核不可用：扫描与分析功能受限。当前所有数值均为系统真实读数，" +
                            "不含任何估算值。",
                        tone = EmptyTone.Error,
                        actionText = "重试",
                        onAction = { viewModel.refreshCapabilities() },
                    )
                }
            }

            if (missing.isNotEmpty()) {
                item(key = "perm-header") {
                    SectionHeader(title = "还需要授权", trailing = "${missing.size} 项")
                }
                items(missing.size, key = { "perm-${missing[it].name}" }) { index ->
                    PermissionCard(
                        capability = missing[index],
                        onGrant = { viewModel.grant(missing[index]) },
                    )
                }
            }

            // ---------- 4. 设备概览 ----------
            item(key = "stat-header") { SectionHeader(title = "设备概览") }

            item(key = "stats") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(
                            title = "存储空间",
                            value = overview?.usedBytes?.formatBytes() ?: "—",
                            eyebrow = "已用",
                            subtitle = overview?.let {
                                "共 ${it.totalBytes.formatBytes()} · 可用 ${it.freeBytes.formatBytes()}"
                            } ?: "正在读取",
                            icon = Icons.Outlined.Storage,
                            onClick = { onNavigate("clean") },
                            modifier = Modifier.weight(1f),
                        )
                        StatCard(
                            title = "可安全清理",
                            value = overview?.junkSafeBytes?.formatBytes() ?: "—",
                            eyebrow = "内核扫描",
                            subtitle = when {
                                !engineAvailable -> "内核不可用，无法扫描"
                                overview == null -> "点下方按钮开始扫描"
                                else -> "含需确认项共 ${overview!!.junkTotalBytes.formatBytes()}"
                            },
                            icon = Icons.Outlined.CleaningServices,
                            accent = NovaCareTheme.colors.healthGood,
                            onClick = { onNavigate("clean") },
                            disabledReason = if (!engineAvailable) "内核不可用" else null,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(
                            title = "长期未用",
                            value = overview?.staleAppCount?.toString() ?: "—",
                            unit = "个",
                            eyebrow = "≥30 天",
                            subtitle = overview?.let { "共 ${it.appCount} 个已装应用" }
                                ?: "正在读取",
                            icon = Icons.Outlined.Memory,
                            onClick = { onNavigate("freeze") },
                            modifier = Modifier.weight(1f),
                        )
                        StatCard(
                            title = "AI 助手",
                            value = "对话",
                            eyebrow = "说人话",
                            subtitle = "描述你想做什么",
                            icon = Icons.Outlined.AutoAwesome,
                            onClick = { onNavigate("assistant") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(
                            title = "自动化",
                            value = "规则",
                            eyebrow = "定时",
                            subtitle = "到点自动执行",
                            icon = Icons.Outlined.Bolt,
                            onClick = { onNavigate("automation") },
                            modifier = Modifier.weight(1f),
                        )
                        StatCard(
                            title = "高级模式",
                            value = "设置",
                            eyebrow = "风险项",
                            subtitle = "开启后可清理需确认项",
                            icon = Icons.Outlined.Tune,
                            onClick = { onNavigate("settings") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // ---------- 5. 主操作区 ----------
            item(key = "action") {
                Spacer(Modifier.height(4.dp))
                when (val s = state) {
                    is HomeViewModel.UiState.Idle -> PrimaryAction(
                        text = "智能优化",
                        subtitle = "只清理确定安全的内容，执行前先给你看清单",
                        onClick = viewModel::onOptimizeClick,
                    )

                    HomeViewModel.UiState.Scanning -> PrimaryAction(
                        text = "正在扫描…",
                        subtitle = "分析存储与缓存",
                        enabled = false,
                        loading = true,
                        onClick = {},
                    )

                    is HomeViewModel.UiState.Preview -> PreviewBlock(
                        state = s,
                        selected = selected,
                        onToggle = viewModel::toggle,
                        onConfirm = viewModel::onConfirm,
                    )

                    HomeViewModel.UiState.Executing -> PrimaryAction(
                        text = "正在执行…",
                        subtitle = "请勿关闭应用",
                        enabled = false,
                        loading = true,
                        onClick = {},
                    )

                    is HomeViewModel.UiState.Done -> DoneBlock(state = s, onReset = viewModel::reset)

                    is HomeViewModel.UiState.Error -> EmptyState(
                        title = "扫描未能完成",
                        message = s.message,
                        icon = Icons.Outlined.CleaningServices,
                        tone = EmptyTone.Error,
                        actionText = "重试",
                        onAction = viewModel::onOptimizeClick,
                    )
                }
            }

            item(key = "footer") {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "所有分析均在本机完成，不上传任何数据",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 预览态：勾选清单 + 执行按钮 */
@Composable
private fun PreviewBlock(
    state: HomeViewModel.UiState.Preview,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    val plan = state.plan
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (plan.advices.isEmpty()) {
            EmptyState(
                title = "没有发现需要清理的项目",
                message = "这台设备的存储状况良好。若确认仍有大量缓存未被识别，" +
                    "请检查是否已授予「所有文件访问」权限。",
                icon = Icons.Outlined.CheckCircle,
                tone = EmptyTone.Success,
            )
        } else {
            SectionHeader(title = "建议清理", trailing = "${plan.advices.size} 项")
            plan.advices.forEach { advice ->
                val key = advice.key()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = key in selected,
                        onCheckedChange = { onToggle(key) },
                    )
                    Spacer(Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = advice.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = advice.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    RiskChip(advice.risk)
                }
            }

            val selectedBytes = plan.advices
                .filter { it.key() in selected }
                .sumOf { it.recommendedBytes }

            PrimaryAction(
                text = "执行清理",
                subtitle = "已选 ${selected.size} 项 · 预计释放 ${selectedBytes.formatBytes()}",
                onClick = onConfirm,
            )
        }
    }
}

/** 完成态 */
@Composable
private fun DoneBlock(
    state: HomeViewModel.UiState.Done,
    onReset: () -> Unit,
) {
    val result = state.result
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        EmptyState(
            title = "已释放 ${result.freedBytes.formatBytes()}",
            message = buildString {
                append("成功 ${result.succeeded.size} 项")
                if (result.failed.isNotEmpty()) append("，失败 ${result.failed.size} 项")
                if (result.recycleBinPath != null) append("\n已移入回收站，7 天内可撤销")
            },
            icon = Icons.Outlined.CheckCircle,
            tone = if (result.failed.isEmpty()) EmptyTone.Success else EmptyTone.Warning,
        )
        PrimaryAction(text = "完成", onClick = onReset)
    }
}

/** 权限引导卡：说明「为什么需要」+ 直达设置页 */
@Composable
private fun PermissionCard(
    capability: MissingCapability,
    onGrant: () -> Unit,
) {
    val (title, why) = when (capability) {
        MissingCapability.USAGE_STATS ->
            "使用情况访问" to "用于识别长期未使用的应用，以便安全冻结。不上传任何使用记录。"

        MissingCapability.ALL_FILES ->
            "所有文件访问" to "让内核能完整扫描存储。未授权时扫描结果会明显偏少。"

        MissingCapability.NOTIFICATIONS ->
            "通知权限" to "用于在长时间清理时展示进度与结果。"
    }
    InlineNotice(
        text = "$title —— $why",
        tone = EmptyTone.Warning,
        actionText = "去开启",
        onAction = onGrant,
    )
}
