package com.novacare.feature.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novacare.core.common.formatBytes
import com.novacare.core.system.MissingCapability
import com.novacare.ui.designsystem.AuroraBackground
import com.novacare.ui.designsystem.EmptyState
import com.novacare.ui.designsystem.EmptyTone
import com.novacare.ui.designsystem.InlineNotice
import com.novacare.ui.designsystem.NovaCareTheme
import com.novacare.ui.designsystem.NovaCard
import com.novacare.ui.designsystem.PrimaryAction
import com.novacare.ui.designsystem.SecondaryAction

/**
 * 首页 —— 重做版。
 *
 * ============================================================
 * 【为什么推翻上一版】
 *
 * 上一版首屏是一枚巨大的健康分圆环（208dp，占掉半屏）。
 * 它的根本问题不是不好看，而是**不回答用户的问题**：
 *   - 用户打开清理类 App 想知道的是「我能省出多少空间」，
 *     不是「我的设备得了 78 分」——78 分不产生任何行动。
 *   - 圆环是**装饰性数据可视化**：把「已用/总量」画成弧，
 *     信息量等同于两个数字，却占掉首屏最贵的位置。
 *   - 用户的原话是「学形状不学魂魄」。形状=圆环，魂魄=「一眼知道能省多少、按哪个」。
 *
 * 新版的信息层级（自上而下）：
 *   1. 一句话状态陈述 —— 不是数字，是「你的设备没问题 / 有 12 GB 可释放」
 *   2. 价值前置的巨型数字 —— 直接是「可释放 12.4 GB」，配一个动词按钮
 *   3. 两个动作卡 —— 「清理」「冻结」，带真实数据，不是图标网格
 *   4. 次要信息降级 —— 设备概览收进一行细字，不再 6 张卡平铺
 *
 * 【为什么删掉六宫格】
 *   六个等权重的卡片 = 没有重点。用户需要扫六遍才知道点哪个。
 *   现在只留两个真正的「动词」（清理/冻结），其余（自动化/助手/设置）
 *   已经在底栏或首页底部工具区，不再与主功能争夺注意力。
 * ============================================================
 */
@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val score by viewModel.score.collectAsStateWithLifecycle()
    val missing by viewModel.missing.collectAsStateWithLifecycle()
    val engineAvailable by viewModel.engineAvailable.collectAsStateWithLifecycle()
    val engineVersion by viewModel.engineVersion.collectAsStateWithLifecycle()
    val overview by viewModel.overview.collectAsStateWithLifecycle()

    // 从系统设置页返回时重新检查权限状态
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshCapabilities()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AuroraBackground {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 24.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "header") {
                HomeHeader(
                    engineAvailable = engineAvailable,
                    engineVersion = engineVersion,
                )
            }

            // 内核降级：必须显眼，但不该占据首屏。放在状态陈述之前，
            // 因为它会削弱下面所有数字的可信度——顺序即因果。
            if (!engineAvailable) {
                item(key = "engine-notice") {
                    InlineNotice(
                        text = "内核不可用，扫描与分析已停用。下方数值仍为系统真实读数，" +
                            "不含任何估算。",
                        tone = EmptyTone.Error,
                        actionText = "重试",
                        onAction = { viewModel.refreshCapabilities() },
                    )
                }
            }

            if (missing.isNotEmpty()) {
                items(missing.size, key = { "perm-${missing[it].name}" }) { index ->
                    PermissionRow(
                        capability = missing[index],
                        onGrant = { viewModel.grant(missing[index]) },
                    )
                }
            }

            // ---------- 价值前置：可释放空间 ----------
            item(key = "hero") {
                ReclaimableHero(
                    overview = overview,
                    state = state,
                    engineAvailable = engineAvailable,
                    onOptimize = viewModel::onOptimizeClick,
                )
            }

            // ---------- 两个动词卡 ----------
            item(key = "actions") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionTile(
                        title = "深度清理",
                        detail = overview?.let {
                            if (it.junkSafeBytes > 0) "可释放 ${it.junkSafeBytes.formatBytes()}"
                            else "暂无可清理项"
                        } ?: "尚未扫描",
                        icon = Icons.Outlined.CleaningServices,
                        accent = NovaCareTheme.colors.accent,
                        onClick = { onNavigate("clean") },
                        disabled = !engineAvailable,
                        modifier = Modifier.weight(1f),
                    )
                    ActionTile(
                        title = "冻结应用",
                        detail = overview?.let {
                            if (it.staleAppCount > 0) "${it.staleAppCount} 个长期未用"
                            else "暂无长期未用"
                        } ?: "尚未读取",
                        icon = Icons.Outlined.AcUnit,
                        accent = NovaCareTheme.colors.healthFair,
                        onClick = { onNavigate("freeze") },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // ---------- 状态行（设备实况，单行细字）----------
            if (overview != null) {
                item(key = "vitals") {
                    DeviceVitals(overview = overview!!, engineVersion = engineVersion)
                }
            }

            // ---------- 进行中的任务 ----------
            if (state !is HomeViewModel.UiState.Idle) {
                item(key = "task") {
                    TaskBlock(
                        state = state,
                        onReset = viewModel::reset,
                        onRetry = viewModel::onOptimizeClick,
                    )
                }
            }

            item(key = "tools") {
                Spacer(Modifier.height(2.dp))
                ToolRow(
                    onAutomation = { onNavigate("automation") },
                    onAssistant = { onNavigate("assistant") },
                    assistantAvailable = engineAvailable,
                )
            }

            item(key = "footer") {
                Text(
                    text = "全部处理在本机完成 · 不联网 · 不采集",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

// ------------------------------------------------------------
// 头部
// ------------------------------------------------------------

@Composable
private fun HomeHeader(
    engineAvailable: Boolean,
    engineVersion: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "NovaCare",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = when {
                    !engineAvailable -> "内核降级 · 仅真实读数"
                    engineVersion.isBlank() -> "正在初始化…"
                    else -> engineVersion
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (engineAvailable) {
                    NovaCareTheme.colors.healthGood
                } else {
                    NovaCareTheme.colors.riskCaution
                },
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

// ------------------------------------------------------------
// 价值前置主视觉
// ------------------------------------------------------------

/**
 * 「可释放」主视觉。
 *
 * 【设计意图】
 * 上一版这里是一枚 208dp 的圆环 —— 装饰。新版直接把**用户能拿到的东西**
 * 用全应用最大的字号写出来：「12.4 GB」。数字下方是一句人话解释它从哪来，
 * 再下方是唯一的强调色按钮。
 *
 * 读的顺序是：能省多少 → 凭什么 → 现在做什么。
 * 这三步走完，用户不需要思考就已经完成了操作。
 *
 * 【为什么不用圆环了】
 * 圆环表达「比例」，但这里的核心信息是「绝对量」。
 * 把绝对量塞进比例图形里，是拿工具去套一个它不擅长的形状。
 * 数字够大、够准、有单位 —— 信息传递效率远高于任何图形。
 */
@Composable
private fun ReclaimableHero(
    overview: HomeViewModel.Overview?,
    state: HomeViewModel.UiState,
    engineAvailable: Boolean,
    onOptimize: () -> Unit,
) {
    val colors = NovaCareTheme.colors
    val busy = state !is HomeViewModel.UiState.Idle

    // 有可清理量 → 用健康色（可以优化）；没有 → 中性。绝不用红色恐吓用户。
    val hasGain = (overview?.junkTotalBytes ?: 0L) > 0L
    val accent = if (hasGain) colors.accent else colors.healthGood

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, colors.hairline),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when {
                        !engineAvailable -> "扫描不可用"
                        overview == null -> "正在读取设备"
                        hasGain -> "本次可优化"
                        else -> "设备状态良好"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(10.dp))

            // ---- 巨型数字 ----
            val bigText = when {
                !engineAvailable -> "—"
                overview == null -> "…"
                hasGain -> overview.junkTotalBytes.formatBytes()
                else -> "0 B"
            }
            Text(
                text = bigText,
                style = MaterialTheme.typography.displayMedium,
                color = if (hasGain || overview == null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    colors.healthGood
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = when {
                    !engineAvailable -> "内核不可用，无法评估可释放空间"
                    overview == null -> "正在读取存储与缓存"
                    hasGain -> buildString {
                        append("其中确定安全可清 ")
                        append(overview.junkSafeBytes.formatBytes())
                        if (overview.junkTotalBytes > overview.junkSafeBytes) {
                            append("，需你确认 ")
                            append((overview.junkTotalBytes - overview.junkSafeBytes).formatBytes())
                        }
                    }
                    else -> "暂未发现值得清理的内容，无需操作"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            if (!busy) {
                PrimaryAction(
                    text = if (overview == null) "扫描设备" else "一键释放",
                    subtitle = if (engineAvailable) {
                        "执行前展示完整清单，可逐项取消"
                    } else {
                        "内核不可用，暂时无法执行"
                    },
                    enabled = engineAvailable,
                    onClick = onOptimize,
                )
            }
        }
    }
}

// ------------------------------------------------------------
// 动词卡
// ------------------------------------------------------------

/**
 * 动词动作卡。
 *
 * 与 StatCard 的区别：StatCard 是「指标展示 + 顺便可点」，
 * ActionTile 是「动词 + 结果」，右上角带箭头，语义是「进这里做事」。
 * 每张卡的副标题必须是**真实数据**（可释放多少 / 几个应用），
 * 数据没准备好就直说「尚未扫描」——不填占位符假装有内容。
 */
@Composable
private fun ActionTile(
    title: String,
    detail: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    disabled: Boolean = false,
) {
    val colors = NovaCareTheme.colors
    val interaction = remember { MutableInteractionSource() }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, colors.hairline),
    ) {
        Column(
            modifier = Modifier
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = !disabled,
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent.copy(alpha = if (disabled) 0.10f else 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (disabled) colors.hairline else accent,
                        modifier = Modifier.size(19.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (disabled) "内核不可用" else detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

// ------------------------------------------------------------
// 设备实况（降级为一行细字）
// ------------------------------------------------------------

@Composable
private fun DeviceVitals(
    overview: HomeViewModel.Overview,
    engineVersion: String,
) {
    val usedRatio = if (overview.totalBytes > 0) {
        (overview.usedBytes.toFloat() / overview.totalBytes.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val animatedRatio by animateFloatAsState(
        targetValue = usedRatio,
        animationSpec = tween(600),
        label = "usedRatio",
    )

    NovaCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "存储",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${overview.usedBytes.formatBytes()} / ${overview.totalBytes.formatBytes()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(10.dp))

            // 储容条：细、无圆点、纯函数表达
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(NovaCareTheme.colors.ringTrack),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedRatio)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    NovaCareTheme.colors.accent.copy(alpha = 0.7f),
                                    NovaCareTheme.colors.accent,
                                ),
                            ),
                        ),
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "可用 ${overview.freeBytes.formatBytes()} · " +
                        "已装 ${overview.appCount} 个应用",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (engineVersion.isNotBlank()) {
                    Text(
                        text = engineVersion.substringBefore(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------
// 任务块（扫描 / 执行 / 完成）
// ------------------------------------------------------------

@Composable
private fun TaskBlock(
    state: HomeViewModel.UiState,
    onReset: () -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        is HomeViewModel.UiState.Idle -> Unit

        HomeViewModel.UiState.Scanning -> Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, NovaCareTheme.colors.hairline),
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = NovaCareTheme.colors.accent,
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = "正在扫描",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "分析存储、缓存与长期未用应用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        HomeViewModel.UiState.Executing -> Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, NovaCareTheme.colors.hairline),
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = NovaCareTheme.colors.accent,
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = "正在执行",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "请保持应用在前台",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        is HomeViewModel.UiState.Preview -> {
            val total = state.plan.advices.size
            val bytes = state.plan.advices.sumOf { it.recommendedBytes }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, NovaCareTheme.colors.hairline),
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = NovaCareTheme.colors.healthGood,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "扫描完成 · 发现 $total 项",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "预计可释放 ${bytes.formatBytes()}，前往「清理」逐项确认",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        is HomeViewModel.UiState.Done -> {
            val failed = state.result.failed.size
            EmptyState(
                title = "已释放 ${state.result.freedBytes.formatBytes()}",
                message = buildString {
                    append("成功 ${state.result.succeeded.size} 项")
                    if (failed > 0) append("，失败 $failed 项")
                    if (state.result.recycleBinPath != null) append("\n已移入回收站，7 天内可撤销")
                },
                icon = Icons.Outlined.CheckCircle,
                tone = if (failed == 0) EmptyTone.Success else EmptyTone.Warning,
                actionText = "完成",
                onAction = onReset,
            )
        }

        is HomeViewModel.UiState.Error -> EmptyState(
            title = "未能完成",
            message = state.message,
            icon = Icons.Outlined.CleaningServices,
            tone = EmptyTone.Error,
            actionText = "重试",
            onAction = onRetry,
        )
    }
}

// ------------------------------------------------------------
// 工具区（低频入口，收在底部）
// ------------------------------------------------------------

@Composable
private fun ToolRow(
    onAutomation: () -> Unit,
    onAssistant: () -> Unit,
    assistantAvailable: Boolean,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SecondaryAction(
            text = "自动化",
            onClick = onAutomation,
            modifier = Modifier.weight(1f),
        )
        // 内核不可用时按钮变灰——但灰按钮本身不解释原因，
        // 所以下面补一行说明。禁用态必须附带理由（组件库约定）。
        SecondaryAction(
            text = "AI 助手",
            onClick = onAssistant,
            enabled = assistantAvailable,
            modifier = Modifier.weight(1f),
        )
    }
    if (!assistantAvailable) {
        Text(
            text = "AI 助手需要内核支持，当前不可用",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

// ------------------------------------------------------------
// 权限行
// ------------------------------------------------------------

/**
 * 权限引导行。
 *
 * 上一版是完整的 InlineNotice（三行文字 + 按钮），三项权限就占掉整屏。
 * 新版压成一行：图标 + 标题 + 一句话 + 箭头。信息不丢，密度提高。
 */
@Composable
private fun PermissionRow(
    capability: MissingCapability,
    onGrant: () -> Unit,
) {
    val (title, why) = when (capability) {
        MissingCapability.USAGE_STATS ->
            "使用情况访问" to "识别长期未用应用"

        MissingCapability.ALL_FILES ->
            "所有文件访问" to "完整扫描存储，否则结果偏少"

        MissingCapability.NOTIFICATIONS ->
            "通知权限" to "长时间任务展示进度"
    }

    val colors = NovaCareTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = colors.riskCaution.copy(alpha = 0.09f),
        border = BorderStroke(1.dp, colors.riskCaution.copy(alpha = 0.22f)),
    ) {
        Row(
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onGrant,
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = colors.riskCaution,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = why,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "开启",
                style = MaterialTheme.typography.labelMedium,
                color = colors.riskCaution,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.riskCaution.copy(alpha = 0.14f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}
