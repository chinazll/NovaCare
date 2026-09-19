package com.novacare.optimizer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.novacare.optimizer.ui.theme.OneUiShapes
import com.novacare.optimizer.ui.theme.OneUiSpacing
import com.novacare.optimizer.ui.theme.ScoreBad
import com.novacare.optimizer.ui.theme.ScoreGood
import com.novacare.optimizer.ui.theme.ScoreMid
import androidx.compose.material3.ripple

/**
 * One UI 9 + M3 Expressive 组件库
 *
 * 灵魂组件：
 * - OneUiCard 26dp 大圆角 / 无投影（One UI 9 去阴影）
 * - OneUiLargeHeader 大标题观看区
 * - PillButton 胶囊按钮（拇指区）
 * - ScoreRing 设备管家式得分环
 * - OneUiListRow 列表行
 * - PrimaryActionButton 主操作按钮
 * - SquircleIconBg squircle 图标背景
 */

// ============= 卡片 =============

/**
 * One UI 9 标志性卡片：26dp 圆角 + 无阴影 + 留白分层
 */
@Composable
fun OneUiCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = OneUiShapes.extraLarge
    val elevation = 0.dp  // One UI 9 灵魂：不靠阴影靠留白
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedContainer by animateColorAsState(
        targetValue = if (isPressed) containerColor.copy(alpha = 0.96f) else containerColor,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "cardPress",
    )
    Surface(
        modifier = modifier
            .clip(shape)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = rememberRipple(),
                    onClick = onClick,
                ) else Modifier
            ),
        shape = shape,
        color = animatedContainer,
        tonalElevation = elevation,
        shadowElevation = elevation,
    ) {
        Column(Modifier.padding(OneUiSpacing.xl), content = content)
    }
}

// ============= 大标题 =============

/**
 * 大标题页头：One UI 9 观看区，28sp Bold
 */
@Composable
fun OneUiLargeHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = OneUiSpacing.xl, vertical = OneUiSpacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            actions()
        }
    }
}

// ============= 按钮 =============

/**
 * 胶囊按钮：One UI 9 永远无棱角；高 52dp 适合拇指
 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    val animatedContainer by animateColorAsState(
        targetValue = if (enabled) containerColor else containerColor.copy(alpha = 0.38f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "btnBg",
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(26.dp),  // 999dp 胶囊
        colors = ButtonDefaults.buttonColors(
            containerColor = animatedContainer,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.38f),
            disabledContentColor = contentColor.copy(alpha = 0.62f),
        ),
        contentPadding = PaddingValues(horizontal = 28.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * 描边按钮：用于次要操作
 */
@Composable
fun PillOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

// ============= 得分环 =============

/**
 * 设备管家式得分环
 */
@Composable
fun ScoreRing(
    score: Int,
    modifier: Modifier = Modifier,
    diameter: Dp = 168.dp,
    ringWidth: Dp = 14.dp,
    label: String = "设备得分",
) {
    val animated by animateIntAsState(
        targetValue = score.coerceIn(0, 100),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "score",
    )
    val color = scoreColor(score)
    val progress = animated / 100f

    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = ringWidth.toPx(), cap = StrokeCap.Round)
            // 背景环
            drawArc(
                color = color.copy(alpha = 0.12f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke,
            )
            // 进度环（渐变）
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(color.copy(alpha = 0.7f), color, color),
                    center = Offset(size.width / 2, size.height / 2),
                ),
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = stroke,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$animated",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

fun scoreColor(score: Int): Color = when {
    score >= 80 -> ScoreGood
    score >= 50 -> ScoreMid
    else -> ScoreBad
}

// ============= 图标背景 =============

/**
 * Squircle 图标背景：16dp 曲率与 One UI 系统图标一致
 */
@Composable
fun SquircleIconBg(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = MaterialTheme.colorScheme.primary,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(16.dp)
    val box = Modifier
        .size(size)
        .clip(shape)
        .background(tint.copy(alpha = 0.14f))
    Box(
        modifier = if (onClick != null) box.clickable(onClick = onClick) else box,
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

// ============= 列表行 =============

/**
 * 标准列表行：squircle 图标 + 主/副文本 + 尾部
 */
@Composable
fun OneUiListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: ImageVector? = null,
    leadingTint: Color = MaterialTheme.colorScheme.primary,
    trailing: (@Composable () -> Unit)? = null,
    showChevron: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = rememberRipple(),
                    onClick = onClick,
                ) else Modifier
            )
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            SquircleIconBg(icon = leading, tint = leadingTint, size = 44.dp)
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else if (showChevron && onClick != null) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ============= 分割线 =============

@Composable
fun OneUiDivider(
    modifier: Modifier = Modifier,
    paddingHorizontal: Dp = OneUiSpacing.xl,
) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = paddingHorizontal),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

// ============= 区块标题 =============

@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = OneUiSpacing.xl, vertical = OneUiSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (action != null) action()
    }
}

// ============= 空状态 =============

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = OneUiSpacing.xxl, vertical = OneUiSpacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SquircleIconBg(
            icon = icon,
            tint = MaterialTheme.colorScheme.primary,
            size = 64.dp,
        )
        Spacer(Modifier.height(OneUiSpacing.lg))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(OneUiSpacing.xs))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}