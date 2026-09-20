package com.novacare.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ============================================================
// SpatialLayer —— One UI 9/9.5 的层次语言（不是平面或阴影）
//
// 【灵魂特征：Spatial Layered UI】
// One UI 9 之后所有卡片都不再是"平面 + 阴影"。而是：
//
//   1. 半透明浮层（alpha 70~90%），叠在背景上能看到氛围渐变
//   2. 顶部受光高光描边（模拟从上方打来的光）—— 1px 内描边 + 0.6 alpha
//   3. 底部光晕投影（不是黑阴影，是 accent 色 + blur 的光斑）—— 4dp 高 + 16dp blur
//   4. 卡片内部转角 = 容器转角 - 4dp（让边缘更"软"）
//
// 视觉感觉：每张卡片都是一个**有光源的生命体**，不只是一块颜色。
// ============================================================

/**
 * 空间层次浮层。
 *
 * @param accentLight 卡片环境光颜色（默认从 NovaCareTheme 取 accent 的 18% 透明）
 * @param corner 卡片圆角
 * @param elevated 是否显示底部光晕（默认 true）
 */
@Composable
fun SpatialLayer(
    modifier: Modifier = Modifier,
    accentLight: Color = NovaCareTheme.colors.accent.copy(alpha = 0.10f),
    corner: Dp = 22.dp,
    elevated: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = NovaCareTheme.colors
    val shape = RoundedCornerShape(corner)
    val interaction = remember { MutableInteractionSource() }

    Surface(
        modifier = modifier
            .let { m ->
                if (onClick != null) {
                    m.clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick,
                    )
                } else m
            }
            // 底部光晕投影：accent 颜色的辐射光斑
            .drawBehind {
                if (!elevated) return@drawBehind
                val r = size.minDimension / 2f
                val cx = size.width / 2f
                val cy = size.height
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentLight,
                            accentLight.copy(alpha = 0f),
                        ),
                        center = Offset(cx, cy),
                        radius = r * 1.4f,
                    ),
                    radius = r * 1.4f,
                    center = Offset(cx, cy),
                )
            },
        shape = shape,
        color = colors.floatSurface,
        border = BorderStroke(1.dp, colors.hairline),
        shadowElevation = 10.dp,
        tonalElevation = 0.dp,
    ) {
        // 顶部受光高光：内描边，1px，模拟从上方打来的光
        Box(
            modifier = Modifier
                .drawBehind {
                    // 上 1px 高亮
                    drawRoundRect(
                        color = colors.floatHighlight,
                        topLeft = Offset(0f, 0f),
                        size = Size(size.width, 1.dp.toPx()),
                        cornerRadius = CornerRadius(corner.toPx(), corner.toPx()),
                    )
                },
        ) {
            content()
        }
    }
}