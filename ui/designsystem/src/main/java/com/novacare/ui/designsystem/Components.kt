package com.novacare.ui.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.novacare.core.model.CleanRisk

// ============================================================
// NovaCare 组件库
//
// 组件设计原则：
//   1. 每个组件覆盖完整状态（按下 / 聚焦 / 禁用 / 加载 / 错误 / 空）
//   2. 触摸目标 ≥ 48dp（Android 无障碍建议值）
//   3. 颜色只从令牌取，禁止硬编码
//   4. 语义化 contentDescription，屏幕阅读器可用
// ============================================================

/** 触摸目标下限（Android 无障碍指南建议 48dp） */
private val MinTouchTarget = 48.dp

// ------------------------------------------------------------
// 1. 健康环 —— 全应用的记忆点
// ------------------------------------------------------------

/**
 * 健康评分环（首页唯一的视觉主角）。
 *
 * 设计意图：用「环 + 缓慢呼吸的光晕」表达设备状态，而不是常见的三段式
 * 仪表盘 —— 后者信息密度高但情绪冷。分数用极细字重放大到 64sp，成为页面锚点。
 *
 * 动效：分数 0 → 目标值（800ms）；环外光晕 3.2s 周期呼吸。
 *       reduceMotion = true 时全部关闭。
 *
 * @param score 0..100，越界自动收敛
 * @param verdict 一句话结论
 */
@Composable
fun HealthRing(
    score: Int,
    verdict: String,
    modifier: Modifier = Modifier,
    diameter: Dp = 208.dp,
    label: String? = null,
) {
    val safeScore = score.coerceIn(0, 100)
    val colors = NovaCareTheme.colors
    val reduceMotion = LocalReduceMotion.current

    val ringColor = when {
        safeScore >= 85 -> colors.healthGood
        safeScore >= 60 -> colors.healthFair
        else -> colors.healthPoor
    }

    val animatedScore by animateFloatAsState(
        targetValue = safeScore.toFloat(),
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 800),
        label = "healthScore",
    )

    val infinite = rememberInfiniteTransition(label = "aura")
    val breathState = remember { mutableFloatStateOf(0f) }
    val breath by if (reduceMotion) {
        breathState
    } else {
        infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(3200, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "breath",
        )
    }

    Box(
        modifier = modifier
            .size(diameter)
            .semantics {
                contentDescription = "设备健康评分 $safeScore 分，满分 100 分。$verdict"
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.085f
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)

            // 呼吸光晕（轨道外侧，低透明度叠加）
            if (!reduceMotion) {
                val haloStroke = stroke * 2.4f
                drawArc(
                    color = ringColor.copy(alpha = 0.10f + 0.10f * breath),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset - haloStroke / 2f, inset - haloStroke / 2f),
                    size = Size(size.width - stroke + haloStroke, size.height - stroke + haloStroke),
                    style = Stroke(width = haloStroke, cap = StrokeCap.Round),
                )
            }

            // 未填充轨道
            drawArc(
                color = colors.ringTrack,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            // 已填充弧（同色系渐变，制造"光"的质感）
            val sweep = 360f * (animatedScore / 100f)
            if (sweep > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        0f to ringColor.copy(alpha = 0.60f),
                        0.5f to ringColor,
                        1f to ringColor,
                    ),
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = animatedScore.toInt().toString(),
                style = MaterialTheme.typography.displayLarge,
                color = ringColor,
            )
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = verdict,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 40.dp),
            )
        }
    }
}

// ------------------------------------------------------------
// 2. 紧凑环（二级页）
// ------------------------------------------------------------

/**
 * 紧凑型进度环，用于电池 / 存储等二级指标。
 * 与 HealthRing 的区别：无动画、无光晕、尺寸小，强调「读数值」而非「感受状态」。
 */
@Composable
fun MiniRing(
    value: Float,
    label: String,
    centerText: String,
    color: Color,
    modifier: Modifier = Modifier,
    diameter: Dp = 84.dp,
) {
    val colors = NovaCareTheme.colors
    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.10f
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = colors.ringTrack,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * value.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerText,
                style = MaterialTheme.typography.titleMedium,
                color = color,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ------------------------------------------------------------
// 3. 卡片体系
// ------------------------------------------------------------

/**
 * 标准卡片：发丝描边 + 一级浮起。
 *
 * 为什么不用重阴影：深色模式下阴影几乎不可见，而描边在深浅两种模式下
 * 都稳定表达"这是一个独立容器"。这是现代 Android 的主流做法。
 */
@Composable
fun NovaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = NovaCareTheme.colors
    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Button,
            onClick = onClick,
        )
    } else {
        Modifier
    }

    Surface(
        modifier = modifier.then(clickModifier),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, colors.hairline),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            content = content,
        )
    }
}

/**
 * 可点击指标卡（首页四宫格 + 二级页概览）。
 *
 * @param eyebrow 分区眉标（全大写小字）
 * @param disabledReason 非空时进入禁用态并显示原因 —— 功能不可用时
 *                       必须给出解释，而不是让用户对着灰按钮发呆
 */
@Composable
fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    unit: String? = null,
    subtitle: String? = null,
    icon: ImageVector? = null,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    disabledReason: String? = null,
) {
    val enabled = disabledReason == null
    val colors = NovaCareTheme.colors
    val accentColor = accent ?: colors.accent

    val clickModifier = if (onClick != null && enabled) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Button,
            onClick = onClick,
        )
    } else {
        Modifier
    }

    Surface(
        modifier = modifier
            .then(clickModifier)
            .semantics {
                if (!enabled && disabledReason != null) {
                    contentDescription = "$title：$value。$disabledReason"
                }
            },
        shape = MaterialTheme.shapes.large,
        color = if (enabled) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f)
        },
        border = BorderStroke(1.dp, colors.hairline),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (eyebrow != null) {
                    Text(
                        text = eyebrow.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (enabled) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (unit != null) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            }

            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val tertiary = disabledReason ?: subtitle
            if (tertiary != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = tertiary,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (disabledReason != null) {
                        colors.riskCaution
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ------------------------------------------------------------
// 4. 主操作按钮
// ------------------------------------------------------------

/**
 * 主 CTA。全应用只有它使用强调色填充 —— 强调色纪律。
 * 状态覆盖：默认 / 加载 / 禁用 / 带副标题。
 * 高度 56dp（或带副标题时 68dp），圆角取 extraLarge。
 */
@Composable
fun PrimaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    subtitle: String? = null,
) {
    val active = enabled && !loading

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(if (subtitle != null) 68.dp else 56.dp)
            .then(
                if (active) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            )
            .semantics {
                contentDescription = buildString {
                    append(text)
                    if (loading) append("，正在执行")
                    if (subtitle != null) append("，$subtitle")
                }
            },
        shape = MaterialTheme.shapes.extraLarge,
        color = if (active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                RingSpinner(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (active) {
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        },
                    )
                }
            }
        }
    }
}

/** 次级按钮：描边 + 透明底，保持强调色纪律 */
@Composable
fun SecondaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = NovaCareTheme.colors
    Surface(
        modifier = modifier
            .height(MinTouchTarget)
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            ),
        shape = MaterialTheme.shapes.extraLarge,
        color = Color.Transparent,
        border = BorderStroke(
            1.dp,
            if (enabled) colors.accent.copy(alpha = 0.55f) else colors.hairline,
        ),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) {
                    colors.accent
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

// ------------------------------------------------------------
// 5. 状态与反馈
// ------------------------------------------------------------

/** 旋转加载环（比 CircularProgressIndicator 更细更轻） */
@Composable
fun RingSpinner(
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 2.5.dp,
) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
        ),
        label = "spinnerAngle",
    )
    Canvas(modifier = modifier) {
        val stroke = strokeWidth.toPx()
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(
            color = color.copy(alpha = 0.22f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawArc(
            color = color,
            startAngle = angle,
            sweepAngle = 96f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

/** 风险标签：让用户在点「执行」前就知道代价 */
@Composable
fun RiskChip(risk: CleanRisk, modifier: Modifier = Modifier) {
    val colors = NovaCareTheme.colors
    val (label, color) = when (risk) {
        CleanRisk.SAFE -> "安全" to colors.riskSafe
        CleanRisk.CAUTION -> "需确认" to colors.riskCaution
        CleanRisk.RISKY -> "有风险" to colors.riskRisky
    }
    Surface(
        color = color.copy(alpha = 0.14f),
        contentColor = color,
        shape = CircleShape,
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

enum class EmptyTone { Neutral, Warning, Error, Success }

/**
 * 空态 / 不可用态。
 *
 * 设计原则：绝不用假数据填充。但如果是缺权限 / 引擎不可用，
 * 必须给出**可执行的下一步**，而不是一句"暂无数据"就结束。
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: ImageVector? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    tone: EmptyTone = EmptyTone.Neutral,
) {
    val colors = NovaCareTheme.colors
    val toneColor = when (tone) {
        EmptyTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
        EmptyTone.Warning -> colors.riskCaution
        EmptyTone.Error -> colors.riskRisky
        EmptyTone.Success -> colors.healthGood
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(toneColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = toneColor,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            SecondaryAction(text = actionText, onClick = onAction)
        }
    }
}

/** 内嵌提示条：引擎降级 / 缺权限这类需常驻但不该打断流程的说明 */
@Composable
fun InlineNotice(
    text: String,
    modifier: Modifier = Modifier,
    tone: EmptyTone = EmptyTone.Warning,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    icon: ImageVector? = null,
) {
    val colors = NovaCareTheme.colors
    val toneColor = when (tone) {
        EmptyTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
        EmptyTone.Warning -> colors.riskCaution
        EmptyTone.Error -> colors.riskRisky
        EmptyTone.Success -> colors.healthGood
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = toneColor.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, toneColor.copy(alpha = 0.22f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = toneColor,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (actionText != null && onAction != null) {
                Spacer(Modifier.width(10.dp))
                Text(
                    text = actionText,
                    style = MaterialTheme.typography.labelLarge,
                    color = toneColor,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClick = onAction,
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** 分区眉标 + 可选右侧操作 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    onTrailingClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null && onTrailingClick != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium,
                color = NovaCareTheme.colors.accent,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onTrailingClick,
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

/** 键值行（详情页用） */
@Composable
fun KeyValueRow(
    key: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.W600,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
        )
    }
}

/** 细进度条（存储占用 / 任务进度） */
@Composable
fun NovaProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color? = null,
    trackColor: Color? = null,
) {
    val colors = NovaCareTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(trackColor ?: colors.ringTrack),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(CircleShape)
                .background(color ?: colors.accent),
        )
    }
}

/**
 * 首页氛围层：极低透明度的径向渐变。
 *
 * 纯平背景会让界面显得廉价且未完成。用一道几乎看不见的青光
 * 把视线引向中央的健康环，制造纵深。
 */
@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = NovaCareTheme.colors
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(colors.auraTop, colors.auraBottom),
                        radius = 1100f,
                    )
                )
        )
        content()
    }
}
