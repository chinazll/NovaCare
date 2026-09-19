package com.novacare.optimizer.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One UI 9 设计灵魂 · 主题系统
 *
 * 灵魂要点：
 * 1. 六区色调表面（Tonal Surfaces）——One UI 9 独有的六区独立着色
 * 2. 强调色克制，大面积中性色
 * 3. 胶囊按钮 / 26dp 大圆角卡片 / squircle 图标背景
 * 4. 支持 Material You 动态取色
 */

// ---- 六区色调表面（亮色）----
private val LightBackground = Color(0xFFF7F7FA)        // Zone 1 系统背景
private val LightSurfaceLow = Color(0xFFF2F2F7)        // Zone 2 导航区
private val LightSurface = Color(0xFFFFFFFF)           // Zone 3 控制区（卡片）
private val LightSurfaceHigh = Color(0xFFEDEDF2)       // Zone 4 通知卡
private val LightSurfaceHighest = Color(0xFFE3E3EA)    // Zone 5 组件容器
private val LightPrimary = Color(0xFF3B5BDB)           // Zone 6 强调：靛蓝
private val LightPrimaryContainer = Color(0xFFDEE3FF)

// ---- 六区色调表面（暗色）----
private val DarkBackground = Color(0xFF0B0B0F)
private val DarkSurfaceLow = Color(0xFF141419)
private val DarkSurface = Color(0xFF1C1C23)
private val DarkSurfaceHigh = Color(0xFF25252D)
private val DarkSurfaceHighest = Color(0xFF2F2F38)
private val DarkPrimary = Color(0xFFA5B4FC)
private val DarkPrimaryContainer = Color(0xFF2E3A6E)

// ---- 语义色：One UI 设备管家语言 ----
val ScoreGood = Color(0xFF2FA36B)      // 得分良好（绿）
val ScoreMid = Color(0xFFE8912D)       // 需要注意（橙）
val ScoreBad = Color(0xFFE05252)       // 需要优化（红）
val InfoBlue = Color(0xFF3B82F6)

private val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = Color(0xFF0D1B6E),
    secondary = Color(0xFF565E71),
    secondaryContainer = Color(0xFFDAE2F9),
    onSecondaryContainer = Color(0xFF141B2C),
    tertiary = Color(0xFF715573),
    background = LightBackground,
    onBackground = Color(0xFF1A1B20),
    surface = LightSurface,
    onSurface = Color(0xFF1A1B20),
    surfaceVariant = LightSurfaceHighest,
    onSurfaceVariant = Color(0xFF44464F),
    surfaceContainerLow = LightSurfaceLow,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurfaceHigh,
    surfaceContainerHighest = LightSurfaceHighest,
    outline = Color(0xFF757780),
    outlineVariant = Color(0xFFC5C6D0),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Color(0xFF0F1D6B),
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = Color(0xFFDEE3FF),
    secondary = Color(0xFFBEC6DC),
    secondaryContainer = Color(0xFF383F52),
    onSecondaryContainer = Color(0xFFDAE2F9),
    tertiary = Color(0xFFDDBCE0),
    background = DarkBackground,
    onBackground = Color(0xFFE4E2E9),
    surface = DarkSurface,
    onSurface = Color(0xFFE4E2E9),
    surfaceVariant = DarkSurfaceHighest,
    onSurfaceVariant = Color(0xFFC6C6D0),
    surfaceContainerLow = DarkSurfaceLow,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceHigh,
    surfaceContainerHighest = DarkSurfaceHighest,
    outline = Color(0xFF90909A),
    outlineVariant = Color(0xFF45464F),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
)

/**
 * One UI 9 字阶：大标题靠字号与字重建层级，正文克制
 */
val OneUiTypography = Typography(
    displayLarge = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
    headlineLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

/**
 * One UI 9 形状语法：
 * - 卡片 26dp / 小卡 20dp / squircle 图标 16dp / 全胶囊
 */
val OneUiShapes = Shapes(
    extraLarge = RoundedCornerShape(26.dp),   // 大卡片
    large = RoundedCornerShape(20.dp),        // 小卡片 / 底部弹窗
    medium = RoundedCornerShape(16.dp),       // squircle 图标背景
    small = RoundedCornerShape(12.dp),
)

@Composable
fun NovaCareTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true, // Material You 动态取色：跟随壁纸
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = OneUiTypography,
        shapes = OneUiShapes,
        content = content,
    )
}
