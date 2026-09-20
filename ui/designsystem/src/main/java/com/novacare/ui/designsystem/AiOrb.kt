package com.novacare.ui.designsystem

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 签名级 AI 灵珠（OneUI 10 "soul"）。
 *
 * 一枚会呼吸的辉光球：内核实色 + 外圈柔光脉冲。它不是装饰，是全应用里
 * "AI 在场"的唯一视觉锚点（对应首页那枚会呼吸的健康环，全应用只有两处
 * 动效主角）。
 *
 * 减弱动效时退回静态球，不脉冲。
 *
 * 说明：真·磁吸跟手（指针贴着灵珠走）需要 pointer 输入反馈，这里做的是
 * 持续呼吸 + 按压回弹，已能在视觉上建立"活的 AI"感知，不冒充未实现的
 * 指针吸附。
 */
@Composable
fun AiOrb(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    color: Color = NovaCareTheme.colors.accent,
) {
    val reduce = LocalReduceMotion.current
    val transition = rememberInfiniteTransition(label = "ai-orb")
    val pulse = if (reduce) {
        1f
    } else {
        transition.animateFloat(
            initialValue = 0.82f,
            targetValue = 1.14f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1600, delayMillis = 200),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "ai-orb-pulse",
        ).value
    }

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        // 外圈柔光脉冲（呼吸）
        Box(
            modifier = Modifier
                .size(size)
                .blur(6.dp, BlurredEdgeTreatment.Unbounded)
                .scale(pulse)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(color.copy(alpha = 0.5f), color.copy(alpha = 0f)),
                    ),
                    shape = CircleShape,
                ),
        )
        // 内核实色球（受光面高光）
        Box(
            modifier = Modifier
                .size(size * 0.5f)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.92f), color),
                    ),
                ),
        )
    }
}
