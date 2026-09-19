package com.novacare.optimizer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas

/**
 * One UI 9 组件库 —— 灵魂实现
 *
 * - OneUiCard: 26dp 大圆角、无投影（One UI 9 去多层阴影）、点击有涟漪
 * - OneUiLargeHeader: 大标题页头，滚动收缩（上观看/下操作的骨架）
 * - PillButton: 全胶囊按钮，置于拇指区
 * - ScoreRing: 设备管家式环形得分（蓝→绿渐变，数字滚动动效）
 * - OneUiRow: 标准列表行（squircle 图标背景 + 主/副文本 + 尾部操作）
 */

@Composable
fun OneUiCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.extraLarge // 26dp
    if (onClick != null) {
        Surface(
            modifier = modifier.clip(shape).clickable(onClick = onClick),
            shape = shape,
            color = containerColor,
            tonalElevation = 0.dp,   // 灵魂：不靠阴影靠留白
            shadowElevation = 0.dp,
        ) { Column(Modifier.padding(20.dp), content = content) }
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = containerColor,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) { Column(Modifier.padding(20.dp), content = content) }
    }
}

/**
 * One UI 灵魂骨架：大标题在上半屏观看区，滚动时收缩
 */
@Composable
fun OneUiLargeHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge, // 28sp 大标题
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

/**
 * 胶囊按钮（One UI 按钮永远无棱角）
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
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        contentPadding = PaddingValues(horizontal = 28.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * 设备管家得分环：渐变圆环 + 数字滚动（One UI Device Care 灵魂组件）
 */
@Composable
fun ScoreRing(
    score: Int,
    modifier: Modifier = Modifier,
    diameter: Dp = 160.dp,
    ringWidth: Dp = 12.dp,
    color: Color = scoreColor(score),
) {
    val animated by animateIntAsState(
        targetValue = score,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "score",
    )
    val progress = animated.coerceIn(0, 100) / 100f
    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = ringWidth.toPx(), cap = StrokeCap.Round)
            drawArc(
                color = color.copy(alpha = 0.15f),
                startAngle = -90f, sweepAngle = 360f, useCenter = false,
                style = stroke,
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(color.copy(alpha = 0.6f), color)),
                startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
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
            Text(
                text = "设备得分",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

fun scoreColor(score: Int): Color = when {
    score >= 80 -> Color(0xFF2FA36B)
    score >= 50 -> Color(0xFFE8912D)
    else -> Color(0xFFE05252)
}

/**
 * 标准列表行：squircle 图标背景（16dp 圆角 = One UI 图标曲率）
 */
@Composable
fun OneUiRow(
    title: String,
    subtitle: String? = null,
    icon: (@Composable () -> Unit)? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interaction,
                    indication = ripple(),
                    onClick = onClick,
                ) else Modifier
            )
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(16.dp)) // squircle 曲率
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) { icon() }
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

/** 区块标题：One UI "重点区块"语法 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}
