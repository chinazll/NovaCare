package com.novacare.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novacare.core.common.formatBytes
import com.novacare.core.system.MissingCapability
import com.novacare.ui.designsystem.NovaCareTheme
import com.novacare.ui.designsystem.NovaSuccess
import com.novacare.ui.designsystem.NovaTap
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 首页 —— OneUI 9.5/10 真实设计语言。
 *
 * ============================================================
 * 【信息架构重做 · 单一主张 + 单一操作】
 *
 * 上一版的根本问题：把 6 个功能挤进首页（Hero 数字 + 2 个动作卡 +
 * 设备实况 + 任务块 + 工具行 + 悬浮 AI 球）。结果是「用户不知道
 * 现在该干什么」—— 视觉主角是数字 12.4 GB，但周围全是噪声。
 *
 * OneUI 9.5 首页的核心原则：
 *   - 单一主张：屏幕只回答一个问题（"现在能清出多少空间？"）。
 *   - 单一操作：只有一个 primary CTA。
 *   - 其他信息全部折叠到次级层（顶部细状态条 + 底部一行辅助链接）。
 *
 * 新结构（自上而下）：
 *   1. 顶条（36dp）   时间 + 系统就绪细字，不抢戏
 *   2. Hero 区        大数字「可清理 X GB」（35sp W200）+ 单位 + 状态徽章
 *   3. 主操作区       单按钮：扫描 / 释放 X（一个动作）
 *   4. 进程区         扫描中显示进度条 + 阶段名
 *   5. 完成区         释放成功后给一行小结果 + 「重新扫描」次按钮
 *   6. 权限/引擎提示  仅在有问题时插一行（紧凑，不抢戏）
 *
 * 不再有：
 *   - 悬浮 AI orb（已在 v0.8 移除，这次彻底不再加回来）
 *   - 自动化 / AI 助手二级入口（这两个是设置类，去设置页或底栏）
 *   - 六宫格 StatCard / 6 个动作卡堆叠
 * ============================================================
 */
@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val missing by viewModel.missing.collectAsStateWithLifecycle()
    val engineAvailable by viewModel.engineAvailable.collectAsStateWithLifecycle()
    val engineVersion by viewModel.engineVersion.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshCapabilities()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val view = LocalView.current
    val context = LocalContext.current

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Spacer(Modifier.height(56.dp))

            // ---- 1. 顶条 ----
            TopStatusBar(
                engineAvailable = engineAvailable,
                engineVersion = engineVersion,
            )

            Spacer(Modifier.height(40.dp))

            // ---- 2. Hero · 单一主张 ----
            HeroStatement(
                overview = overview,
                state = state,
                engineAvailable = engineAvailable,
            )

            Spacer(Modifier.height(32.dp))

            // ---- 3. 主操作 / 进程 / 完成 ----
            ActionZone(
                state = state,
                overview = overview,
                engineAvailable = engineAvailable,
                onScan = {
                    NovaTap(view)
                    viewModel.onOptimizeClick()
                },
                onConfirm = {
                    NovaSuccess(context)
                    viewModel.onConfirm()
                },
                onRetry = {
                    NovaTap(view)
                    viewModel.onOptimizeClick()
                },
                onReset = {
                    NovaTap(view)
                    viewModel.reset()
                },
            )

            // ---- 4. 引擎 / 权限提示（仅在有问题时显示，24dp 留白）----
            if (!engineAvailable || missing.isNotEmpty()) {
                Spacer(Modifier.height(32.dp))
                if (!engineAvailable) {
                    EngineWarning { viewModel.refreshCapabilities() }
                }
                missing.forEach { capability ->
                    Spacer(Modifier.height(8.dp))
                    PermissionRow(
                        capability = capability,
                        onGrant = {
                            NovaTap(view)
                            viewModel.grant(capability)
                        },
                    )
                }
            }

            // ---- 5. 次级入口 ----
            Spacer(Modifier.height(40.dp))
            SecondaryRow(
                onNavigate = { route ->
                    NovaTap(view)
                    onNavigate(route)
                },
                engineAvailable = engineAvailable,
            )

            Spacer(Modifier.height(160.dp))
        }
    }
}

// ============================================================
// 顶条（系统状态细字）
// ============================================================
@Composable
private fun TopStatusBar(
    engineAvailable: Boolean,
    engineVersion: String,
) {
    val colors = NovaCareTheme.colors
    val timeText = remember {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(
                    if (engineAvailable) colors.healthGood
                    else colors.riskCaution,
                ),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = when {
                !engineAvailable -> "内核不可用"
                engineVersion.isBlank() -> "内核初始化"
                else -> "内核就绪"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = timeText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ============================================================
// Hero · 单一主张
// ============================================================
@Composable
private fun HeroStatement(
    overview: HomeViewModel.Overview?,
    state: HomeViewModel.UiState,
    engineAvailable: Boolean,
) {
    val colors = NovaCareTheme.colors
    val (numberText, unitText, statusText, numberColor) = when {
        !engineAvailable -> Quadruple("—", "GB", "内核不可用", MaterialTheme.colorScheme.onSurfaceVariant)
        state is HomeViewModel.UiState.Scanning -> Quadruple("…", "GB", "正在扫描", colors.accent)
        overview == null -> Quadruple("…", "GB", "准备就绪", MaterialTheme.colorScheme.onSurface)
        overview.junkTotalBytes > 0L -> Quadruple(
            formatBytesNumber(overview.junkTotalBytes),
            "GB 可清理",
            "已读取设备存储与缓存",
            MaterialTheme.colorScheme.onSurface,
        )
        else -> Quadruple("0", "B 可清理", "设备状态良好", colors.healthGood)
    }

    Column {
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = numberText,
                style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.W200),
                color = numberColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = unitText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

private fun formatBytesNumber(bytes: Long): String {
    val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.1f", gb)
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f", bytes / (1024.0 * 1024.0))
        else -> String.format(Locale.US, "%.0f", bytes / 1024.0)
    }
}

private fun formatTimeAgo(epochMs: Long): String {
    if (epochMs <= 0) return "从未"
    val delta = (System.currentTimeMillis() - epochMs) / 1000
    return when {
        delta < 60 -> "刚刚"
        delta < 3600 -> "${delta / 60} 分钟前"
        delta < 86400 -> "${delta / 3600} 小时前"
        else -> "${delta / 86400} 天前"
    }
}

// ============================================================
// 操作区（idle / scanning / executing / preview / done / error）
// ============================================================
@Composable
private fun ActionZone(
    state: HomeViewModel.UiState,
    overview: HomeViewModel.Overview?,
    engineAvailable: Boolean,
    onScan: () -> Unit,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    onReset: () -> Unit,
) {
    val colors = NovaCareTheme.colors

    when (state) {
        is HomeViewModel.UiState.Idle -> {
            PrimaryButton(
                text = if (overview == null) "扫描设备" else "重新扫描",
                onClick = onScan,
                enabled = engineAvailable,
            )
            if (overview != null) {
                Spacer(Modifier.height(12.dp))
                SecondaryTextButton(
                    text = "查看清理项（${overview.junkSafeBytes.formatBytes()} 可立即清）",
                    onClick = onConfirm,
                    enabled = overview.junkSafeBytes > 0,
                )
            }
        }

        HomeViewModel.UiState.Scanning -> {
            ProgressZone(
                label = "正在扫描",
                detail = "分析存储与缓存",
                progress = null,
            )
        }

        HomeViewModel.UiState.Executing -> {
            ProgressZone(
                label = "正在释放",
                detail = "清理已选项目",
                progress = null,
            )
        }

        is HomeViewModel.UiState.Preview -> {
            val bytes = state.plan.advices.sumOf { it.recommendedBytes }
            val count = state.plan.advices.size
            PrimaryButton(
                text = if (bytes > 0) "释放 ${bytes.formatBytes()}" else "执行清理",
                onClick = onConfirm,
                enabled = engineAvailable,
            )
            Spacer(Modifier.height(12.dp))
            SecondaryTextButton(
                text = "查看 $count 项清单",
                onClick = onConfirm,
            )
        }

        is HomeViewModel.UiState.Done -> {
            val failed = state.result.failed.size
            SuccessZone(
                message = buildString {
                    append("已释放 ${state.result.freedBytes.formatBytes()}")
                    if (failed > 0) append(" · $failed 项失败")
                },
                onDone = onReset,
            )
        }

        is HomeViewModel.UiState.Error -> {
            ErrorZone(
                message = state.message,
                onRetry = onRetry,
            )
        }
    }
}

@Composable
private fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val colors = NovaCareTheme.colors
    val cs = MaterialTheme.colorScheme
    val container = if (enabled) cs.primary else cs.onSurface.copy(alpha = 0.12f)
    val content = if (enabled) cs.onPrimary else cs.onSurface.copy(alpha = 0.38f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(20.dp)),
        color = container,
        contentColor = content,
        onClick = { if (enabled) onClick() },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W600),
            )
        }
    }
}

@Composable
private fun SecondaryTextButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W500),
            color = if (enabled) cs.primary else cs.onSurface.copy(alpha = 0.38f),
        )
    }
}

@Composable
private fun ProgressZone(
    label: String,
    detail: String,
    progress: Float?,
) {
    val cs = MaterialTheme.colorScheme
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = cs.primary,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = cs.onSurface,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(cs.onSurface.copy(alpha = 0.08f)),
        ) {
            if (progress != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(cs.primary),
                )
            }
        }
    }
}

@Composable
private fun SuccessZone(
    message: String,
    onDone: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.CheckCircleOutline,
                contentDescription = null,
                tint = colors.healthGood,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = cs.onSurface,
            )
        }
        Spacer(Modifier.height(12.dp))
        SecondaryTextButton(text = "完成", onClick = onDone)
    }
}

@Composable
private fun ErrorZone(
    message: String,
    onRetry: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors
    Column {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurface,
        )
        Spacer(Modifier.height(12.dp))
        SecondaryTextButton(text = "重试", onClick = onRetry)
    }
}

// ============================================================
// 引擎 / 权限提示
// ============================================================
@Composable
private fun EngineWarning(onRetry: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.riskCaution.copy(alpha = 0.08f))
            .clickable { onRetry() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "内核不可用，仅显示真实读数",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.riskCaution,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "重试",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W600),
            color = colors.riskCaution,
        )
    }
}

@Composable
private fun PermissionRow(
    capability: MissingCapability,
    onGrant: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors
    val (title, why) = when (capability) {
        MissingCapability.USAGE_STATS ->
            "使用情况访问" to "识别长期未用应用"
        MissingCapability.ALL_FILES ->
            "所有文件访问" to "完整扫描存储"
        MissingCapability.NOTIFICATIONS ->
            "通知权限" to "长时间任务展示进度"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cs.onSurface.copy(alpha = 0.04f))
            .clickable { onGrant() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = cs.onSurface,
            )
            Text(
                text = why,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }
        Text(
            text = "开启",
            style = MaterialTheme.typography.labelMedium,
            color = cs.primary,
        )
    }
}

// ============================================================
// 次级入口（一行小字 + chevron）—— 只放真正低频的入口
// ============================================================
@Composable
private fun SecondaryRow(
    onNavigate: (String) -> Unit,
    engineAvailable: Boolean,
) {
    val cs = MaterialTheme.colorScheme
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable { onNavigate("automation") }
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "自动化",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "→",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(enabled = engineAvailable) { onNavigate("assistant") }
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "AI 助手",
                style = MaterialTheme.typography.bodyMedium,
                color = if (engineAvailable) cs.onSurface else cs.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (engineAvailable) "→" else "需内核",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable { onNavigate("settings") }
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "设置",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "→",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

// (end of file)
