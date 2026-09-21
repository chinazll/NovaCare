package com.novacare.feature.oneclick

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// 临时占位（v0.21.0-alpha 第二步替换为完整实现）
@Composable
fun OneClickScreen(
    onOpenClean: () -> Unit,
    onOpenFreeze: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) { Text("一键体检 · 占位（即将实现）") }
}
