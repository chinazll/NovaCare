package com.novacare.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * 真玻璃浮层（OneUI 10）。
 *
 * 用"半透明着色 + 顶部发丝高光 + 大扩散柔光阴影"三层叠加表达玻璃，
 * 不靠明度差、不靠重阴影。这是设计系统文档里"浮层语言"的落地实现，
 * 让卡片/输入栏真正的"浮"起来而不是"贴"在背景上。
 *
 * 诚实声明：Compose 没有原生 backdrop-blur，这里做的是"磨砂玻璃观感"
 * （透出底色 + 高光 + 柔影），而非真实背景模糊。要做背景模糊需 backdrop API，
 * 本版不冒充。
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    tint: Color = NovaCareTheme.colors.floatSurface,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = NovaCareTheme.colors
    Box(
        modifier = modifier
            .shadow(
                elevation = 24.dp,
                shape = shape,
                ambientColor = c.ambientShadow,
                spotColor = c.spotShadow,
                clip = false,
            )
            .background(color = tint, shape = shape),
    ) {
        // 用户内容
        content()
        // 顶部发丝高光：受光面，制造玻璃边缘的"光打在边上"观感
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .align(Alignment.TopCenter)
                .background(c.floatHighlight),
        )
    }
}
