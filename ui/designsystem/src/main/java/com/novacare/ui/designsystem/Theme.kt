package com.novacare.ui.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF00658F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC7E7FF),
    secondary = Color(0xFF4E6359),
    surface = Color(0xFFFBFDF8),
    background = Color(0xFFFBFDF8),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FCEFF),
    onPrimary = Color(0xFF00344C),
    primaryContainer = Color(0xFF004B6B),
    secondary = Color(0xFFB6CCBF),
)

/**
 * NovaCare 设计系统主题
 *
 * 设计约束来自蓝图 §4.5：L1 首页只给「评分 + 一句话结论 + 一个按钮」，
 * 因此设计系统只提供少量高信息密度组件，不提供花哨装饰。
 */
@Composable
fun NovaCareTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
