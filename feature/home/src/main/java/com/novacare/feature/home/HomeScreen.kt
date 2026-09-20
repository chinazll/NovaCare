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
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novacare.core.common.formatBytes
import com.novacare.core.system.MissingCapability
import com.novacare.ui.designsystem.AuroraBackground
import com.novacare.ui.designsystem.DraggableAiOrb
import com.novacare.ui.designsystem.EmptyState
import com.novacare.ui.designsystem.EmptyTone
import com.novacare.ui.designsystem.InlineNotice
import com.novacare.ui.designsystem.NovaCareTheme
import com.novacare.ui.designsystem.NovaCard
import com.novacare.ui.designsystem.NovaNowBar
import com.novacare.ui.designsystem.NowBarStatus
import com.novacare.ui.designsystem.NowBarChip
import com.novacare.ui.designsystem.PrimaryAction
import com.novacare.ui.designsystem.SecondaryAction
import com.novacare.ui.designsystem.SpatialLayer
import com.novacare.ui.designsystem.StaggerFlyIn

/**
 * 首页 —— One UI 9/9.5 灵魂语言版本。
 *
 * ============================================================
 * 【灵魂特征，不是表面相似】
 *
 * 这一版做了三个 One UI 9/9.5 真正可感知的差异，不是颜色和圆角：
 *
 *   1. NovaNowBar（系统体征浮条）
 *      顶部永远在线的 44dp 细浮条，替代之前角落的"设置"chip。
 *      - 左侧呼吸点（1.6s 周期 + 同步扩张的光晕）告诉用户"系统在监听"
 *      - 顶部 1px 彩色渐变高光带随状态色变化（健康=绿 / 警示=黄 / 错误=红）
 *      - 主标题 + 副标题的两行结构，左 16dp 内边距让呼吸点与文字自然成组
 *      这是 Samsung Now Bar 的同构语言：从角落位置升级为系统体征。
 *
 *   2. SpatialLayer（空间层次）
 *      Hero 卡（"12.4 GB"）用 SpatialLayer 包裹，而不是普通 Surface：
 *      - 半透明 floatSurface（70% alpha），背景的氛围渐变透过来
 *      - 顶部 1px 受光高光描边（模拟从上方打来的光）
 *      - 底部 accent 色径向光晕投影（不是黑阴影！）
 *      - 形状 RoundedCornerShape(26dp)
 *      这是 One UI 9 的"空间感"——卡片是有光源的生命体。
 *
 *   3. StaggerFlyIn（群组飞入）
 *      每个 item 错开 60ms 飞入，最后一个落位的是主 CTA。
 *      用户视线被从上到下引导一遍，最后落在"一键释放"按钮上。
 *      reduceMotion 开启时全部瞬时显示。
 *
 * 【数字仍然最大】
 * "12.4 GB" 是 hero —— 这是用户的核心疑问。displayMedium (48sp) + W200。
 * 任何花哨的视觉都不能压过这个数字。
 *
 * 【结构降级】
 * 上一版还遗留的两个六宫格 StatCard 全部删除。Hero + 两个动词卡 +
 * 一行设备实况 = 用户需要看到的所有内容。底部一行 SecondaryAction 收纳低频入口。
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

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshCapabilities()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AuroraBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 0.dp, // NowBar 自己管水平 padding
                    end = 0.dp,
                    top = 10.dp,
                    bottom = 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
            // ---- 1. NowBar：顶部系统体征浮条 ----
            item(key = "now-bar") {
                NovaNowBar(
                    title = when {
                        !engineAvailable -> "内核降级 · 仅显示真实读数"
                        engineVersion.isBlank() -> "正在初始化内核"
                        else -> "内核就绪 · $engineVersion"
                    },
                    subtitle = score?.verdict,
                    status = when {
                        !engineAvailable -> NowBarStatus.Error
                        engineVersion.isBlank() -> NowBarStatus.Idle
                        else -> NowBarStatus.Healthy
                    },
                    trailing = {
                        NowBarChip(
                            text = "设置",
                            onClick = { onNavigate("settings") },
                            accent = NovaCareTheme.colors.accent,
                        )
                    },
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // ---- 2. 内核降级 / 权限 ----
            if (!engineAvailable) {
                item(key = "engine-notice") {
                    StaggerFlyIn(index = 0) {
                        InlineNotice(
                            text = "内核不可用，扫描与分析已停用。下方数值仍为系统真实读数，" +
                                "不含任何估算。",
                            tone = EmptyTone.Error,
                            actionText = "重试",
                            onAction = { viewModel.refreshCapabilities() },
                        )
                    }
                }
            }

            if (missing.isNotEmpty()) {
                items(missing.size, key = { "perm-${missing[it].name}" }) { index ->
                    StaggerFlyIn(index = index + 1) {
                        PermissionRow(
                            capability = missing[index],
                            onGrant = { viewModel.grant(missing[index]) },
                        )
                    }
                }
            }

            // ---- 3. Hero：价值前置（SpatialLayer） ----
            item(key = "hero") {
                StaggerFlyIn(index = 3) {
                    ReclaimableHero(
                        overview = overview,
                        state = state,
                        engineAvailable = engineAvailable,
                        onOptimize = viewModel::onOptimizeClick,
                    )
                }
            }

            // ---- 4. 两个动词卡 ----
            item(key = "actions") {
                StaggerFlyIn(index = 4) {
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
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 20.dp),
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
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 20.dp),
                        )
                    }
                }
            }

            // ---- 5. 设备实况（单行细字） ----
            if (overview != null) {
                item(key = "vitals") {
                    StaggerFlyIn(index = 5) {
                        Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                            DeviceVitals(overview = overview!!, engineVersion = engineVersion)
                        }
                    }
                }
            }

            // ---- 6. 任务块 ----
            if (state !is HomeViewModel.UiState.Idle) {
                item(key = "task") {
                    StaggerFlyIn(index = 6) {
                        Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                            TaskBlock(
                                state = state,
                                onConfirm = viewModel::onConfirm,
                                onNavigateToClean = { onNavigate("clean") },
                                onReset = viewModel::reset,
                                onRetry = viewModel::onOptimizeClick,
                            )
                        }
                    }
                }
            }

            // ---- 7. 工具区 ----
            item(key = "tools") {
                StaggerFlyIn(index = 7) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        ToolRow(
                            onAutomation = { onNavigate("automation") },
                            onAssistant = { onNavigate("assistant") },
                            assistantAvailable = engineAvailable,
                        )
                    }
                }
            }

            item(key = "footer") {
                StaggerFlyIn(index = 8) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
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

            // ---- AI orb 悬浮球：One UI 10 Fluid AI 的 agent 入口 ----
            // 浮在所有内容之上，可拖拽、带呼吸光晕，点击进入 AI 助手。
            // 注意：只有当引擎可用时才显示（AI 助手依赖内核读取设备状态）。
            if (engineAvailable) {
                DraggableAiOrb(
                    icon = Icons.Outlined.AutoAwesome,
                    onClick = { onNavigate("assistant") },
                    contentDescription = "AI 助手",
                    modifier = Modifier,
                )
            }
        }
    }
}

// ============================================================
// Hero：价值前置 + SpatialLayer
// ============================================================

@Composable
private fun ReclaimableHero(
    overview: HomeViewModel.Overview?,
    state: HomeViewModel.UiState,
    engineAvailable: Boolean,
    onOptimize: () -> Unit,
) {
    val colors = NovaCareTheme.colors
    val busy = state.isNotIdle()

    val hasGain = (overview?.junkTotalBytes ?: 0L) > 0L
    val accent = if (hasGain) colors.accent else colors.healthGood

    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
        SpatialLayer(
            accentLight = accent.copy(alpha = 0.18f),
            corner = 26.dp,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // 状态指示（与 NowBar 同步的语义色）
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
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.06.sp,
                    )
                }

                Spacer(Modifier.height(12.dp))

                // ---- 巨型数字：48sp + W200 + -0.03sp 字距 ----
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

                Spacer(Modifier.height(6.dp))

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
                    lineHeight = 22.sp,
                )

                Spacer(Modifier.height(18.dp))

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
}

private fun HomeViewModel.UiState.isNotIdle(): Boolean = this !is HomeViewModel.UiState.Idle

// ============================================================
// 动词卡：动作驱动而非指标展示
// ============================================================

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
        shadowElevation = 6.dp,
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

// ============================================================
// 设备实况：单行细字 + 细储容条
// ============================================================

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

// ============================================================
// 任务块
// ============================================================

@Composable
private fun TaskBlock(
    state: HomeViewModel.UiState,
    onConfirm: () -> Unit,
    onNavigateToClean: () -> Unit,
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
            SpatialLayer(corner = 22.dp) {
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
                        text = "预计可释放 ${bytes.formatBytes()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))

                    // ===== 这是真正的入口 =====
                    // 上一版只显示一段说明文字，没有可点元素：用户被告知"应该去做"
                    // 但 UI 不提供路径。一键释放的真实执行入口是 viewModel::onConfirm，
                    // Home 已经持有 plan（在 HomeViewModel.currentPlan 里），所以
                    // 用户在 Home 就能完成完整闭环（扫描→执行→看结果），
                    // 不必先跳转清理页。
                    // 同时给一个"逐项查看"次要入口，让需要精细控制的用户
                    // 仍能跳到清理页。
                    PrimaryAction(
                        text = if (bytes > 0) "释放 ${bytes.formatBytes()}" else "执行清理",
                        subtitle = "将仅清理你已勾选的项目",
                        onClick = onConfirm,
                    )
                    Spacer(Modifier.height(8.dp))
                    SecondaryAction(
                        text = "逐项查看",
                        onClick = { onNavigateToClean() },
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

// ============================================================
// 工具区
// ============================================================

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

// ============================================================
// 权限行
// ============================================================

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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
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

// (end of file)