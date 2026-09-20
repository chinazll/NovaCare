package com.novacare.ui.designsystem

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ============================================================
// GlassSurface —— One UI 10 Fluid AI 的「玻璃质感」
//
// 【为什么这是 UI/UX 的灵魂，而不是又一个卡片】
// 上一版一直用「半透明 + 发丝描边」模拟玻璃，但用户一眼就能看出
// 那只是 alpha 降低的色块 —— 它没有玻璃真正的东西：**背景模糊**。
// 玻璃感 = 你透过它看到的后方世界是模糊的，不是半透明的。
//
// Fluid AI Design System 的核心视觉就是 glass-like visuals。
//
// 技术现实：
//   - 真正的 backdrop blur 需要 RenderEffect（API 31+）
//   - minSdk 26 的降级：半透明 + 顶部高光，仍比纯 alpha 更有玻璃感
// ============================================================

/**
 * 玻璃质感浮层。
 *
 * @param corner 圆角
 * @param blurRadius 模糊半径（仅 API 31+ 生效）
 * @param tint 玻璃底色（带 alpha）
 * @param borderColor 发丝描边色
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    corner: Dp = 22.dp,
    blurRadius: Dp = 18.dp,
    tint: Color? = null,
    borderColor: Color? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    val colors = NovaCareTheme.colors
    val resolvedTint = tint ?: colors.floatSurface
    val resolvedBorder = borderColor ?: colors.hairline
    val highlight = colors.floatHighlight

    Surface(
        modifier = modifier
            .clip(shape)
            .then(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Modifier.graphicsLayer {
                        renderEffect = BlurEffect(
                            blurRadius.value,
                            blurRadius.value,
                            TileMode.Clamp,
                        )
                    }
                } else {
                    Modifier
                },
            ),
        shape = shape,
        color = resolvedTint,
        border = BorderStroke(1.dp, resolvedBorder),
        shadowElevation = 8.dp,
    ) {
        // 顶部受光高光：玻璃片的上边缘被光源照亮
        Box(
            modifier = Modifier.drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            highlight,
                            Color.Transparent,
                        ),
                        startY = 0f,
                        endY = size.height * 0.35f,
                    ),
                    topLeft = Offset.Zero,
                    size = Size(size.width, size.height * 0.35f),
                )
            },
        ) {
            content()
        }
    }
}

/** 玻璃卡片（带点击的便捷封装） */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    corner: Dp = 22.dp,
    tint: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    GlassSurface(
        modifier = modifier.then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                )
            } else Modifier,
        ),
        corner = corner,
        tint = tint ?: NovaCareTheme.colors.floatSurface,
        content = content,
    )
}
