package com.novacare.ui.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape

/**
 * v0.21+ 简化：原 GlassPanel 是半透明玻璃浮层；新版本改为普通 Material 3 Surface。
 * OneUI 9 不使用毛玻璃浮层，所以这个组件退化为 Surface alias。
 *
 * 保留组件名以便不破坏已有调用点（CleanScreen、FreezeScreen、AutomationScreen 等）。
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    tint: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = tint,
        content = { Box(content = content) },
    )
}