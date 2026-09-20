package com.novacare.ui.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================
// NovaNowBar —— One UI 9/9.5 Now Bar / Live Indicator
//
// 【灵魂特征】
// One UI 9 在锁屏底部、状态栏、桌面卡片之间反复出现一根细浮条，上面有
// 持续呼吸的小点 + 一句描述。这是**系统的体征**，告诉人"系统在做什么"。
//
// 上一版首页用的是"应用名 + 内核版本"的角落标题——这是 2010 年的做法。
// 这一版的差异：
//
//   1. 永远在线 —— 不依赖用户打开设置就能看到内核是否健康
//   2. 呼吸指示点 —— 圆形小点的 alpha 缓慢呼吸，表达"这是活的"
//   3. 彩色边缘高光 —— 浮条顶部有一条 1px 的彩色渐变，提示当前状态色调
//   4. 半透明 + 模糊风格 —— 浮在内容上方，不抢主体视觉
//
// 这个组件刻意做得细（44dp），让用户感觉到"它在"，但不需要它"喊"。
// ============================================================

/**
 * NovaCare 系统体征浮条。
 *
 * @param status 状态语义颜色（健康/警示/危险）
 * @param breathing 是否显示呼吸点（默认 true，reduceMotion 时自动隐藏）
 * @param leading 左侧小图标 / emoji / chip
 * @param title 主标题（如 "内核就绪 · v0.7.0"）
 * @param subtitle 副标题（如 "上次清理 2 小时前"），可空
 * @param trailing 右侧操作（如 "设置"），可空
 */
@Composable
fun NovaNowBar(
    title: String,
    subtitle: String? = null,
    status: NowBarStatus = NowBarStatus.Healthy,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
) {
    val reduceMotion = LocalReduceMotion.current
    val colors = NovaCareTheme.colors

    // 状态色：green/yellow/red —— 与设计系统语义色绑定
    val statusColor = when (status) {
        NowBarStatus.Healthy -> colors.healthGood
        NowBarStatus.Warning -> colors.healthFair
        NowBarStatus.Error -> colors.healthPoor
        NowBarStatus.Idle -> colors.hairline
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(280)) + slideInVertically(tween(280)) { it / 8 },
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(22.dp),
            color = colors.floatSurface,
            border = BorderStroke(1.dp, colors.hairline),
            shadowElevation = 6.dp,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    // 顶部彩色渐变高光带：1dp 高，渐变到透明
                    .drawBehind {
                        drawRoundRect(
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    statusColor.copy(alpha = 0.55f),
                                    statusColor.copy(alpha = 0f),
                                ),
                                startY = 0f,
                                endY = 6.dp.toPx(),
                            ),
                            topLeft = Offset(0f, 0f),
                            size = Size(size.width, 6.dp.toPx()),
                            cornerRadius = CornerRadius(22.dp.toPx(), 22.dp.toPx()),
                        )
                    },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 状态呼吸点
                    if (!reduceMotion || status != NowBarStatus.Idle) {
                        BreathingDot(
                            color = statusColor,
                            diameter = 8.dp,
                            modifier = Modifier.padding(end = 10.dp),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .padding(end = 10.dp)
                                .clip(CircleShape)
                                .background(statusColor),
                        )
                    }

                    if (leading != null) {
                        leading()
                        Spacer(Modifier.width(8.dp))
                    }

                    // 标题 + 副标题（一行，主标题字数限制 16 字符）
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                fontSize = 11.sp,
                            )
                        }
                    }

                    if (trailing != null) {
                        Spacer(Modifier.width(8.dp))
                        trailing()
                    }
                }
            }
        }
    }
}

enum class NowBarStatus { Healthy, Warning, Error, Idle }

/**
 * 持续呼吸的小点 —— One UI 标志性指示。
 *
 * 不是淡入淡出（那是通知）—— 是 1.6s 周期的脉冲，
 * 表达"我在持续监听"。
 */
@Composable
private fun BreathingDot(
    color: Color,
    diameter: Dp,
    modifier: Modifier = Modifier,
) {
    val infinite = rememberInfiniteTransition(label = "breath")
    val reduceMotion = LocalReduceMotion.current
    val pulse by infinite.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (reduceMotion) 0 else 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathPulse",
    )

    // 外圈光晕：呼吸时同步扩张
    val haloScale by infinite.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (reduceMotion) 0 else 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "haloScale",
    )

    val animatedColor = animateFloatAsState(
        targetValue = pulse,
        animationSpec = tween(if (reduceMotion) 0 else 200),
        label = "dotAlpha",
    )

    Box(
        modifier = modifier
            .size(diameter * 2.2f)
            .drawBehind {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val r = (this@drawBehind.size.minDimension / 2f) * 0.5f
                val outerR = r * haloScale
                // 外圈光晕
                drawCircle(
                    color = color.copy(alpha = 0.18f * animatedColor.value),
                    radius = outerR,
                    center = Offset(cx, cy),
                )
                // 核心点
                drawCircle(
                    color = color.copy(alpha = animatedColor.value),
                    radius = r,
                    center = Offset(cx, cy),
                )
            },
    )
}

/**
 * NowBar 内置的 chip 操作 —— 用于"设置"、"授权"等次要入口。
 */
@Composable
fun NowBarChip(
    text: String,
    onClick: () -> Unit,
    accent: Color = NovaCareTheme.colors.accent,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .background(accent.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.04.sp,
        )
    }
}