package com.novacare.optimizer.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * One UI 9 + Material 3 Expressive 设计系统（2025/2026 顶级）
 *
 * 核心灵魂：
 * 1. 上观看 / 下操作：上半屏展示信息、下半屏操作
 * 2. 六区色调表面：每区独立可着色（Material You 动态取色）
 * 3. 无阴影哲学：26dp 圆角 + 留白分层（One UI 9 标志性）
 * 4. Spring 物理动画：基于 Material 3 Expressive spring spec
 * 5. 35 形状库：M3 Expressive 新增（按需选用）
 * 6. 强调色克制：靛蓝 #3B5BDB 默认
 * 7. 排版：Emphasized 文本（displayLarge 用 700 字重）
 *
 * 参考：SD Maid SE 配色 / Canta 简洁 / One UI 9 真实
 */

private val BrandPrimary = Color(0xFF3B5BDB)
private val BrandPrimaryContainer = Color(0xFFDEE3FF)
private val OnBrandPrimaryContainer = Color(0xFF0D1B6E)

private val LightColors = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = Color.White,
    primaryContainer = BrandPrimaryContainer,
    onPrimaryContainer = OnBrandPrimaryContainer,
    secondary = Color(0xFF5B5F6F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E2EF),
    onSecondaryContainer = Color(0xFF181C22),
    tertiary = Color(0xFF715573),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFCD7FB),
    onTertiaryContainer = Color(0xFF28132E),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF7F7FA),
    onBackground = Color(0xFF1A1B20),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1B20),
    surfaceVariant = Color(0xFFE3E3EA),
    onSurfaceVariant = Color(0xFF44464F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F2F7),
    surfaceContainer = Color(0xFFEDEDF2),
    surfaceContainerHigh = Color(0xFFE7E7EC),
    surfaceContainerHighest = Color(0xFFE1E1E6),
    outline = Color(0xFF757780),
    outlineVariant = Color(0xFFC5C6D0),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA5B4FC),
    onPrimary = Color(0xFF0F1D6B),
    primaryContainer = Color(0xFF2E3A6E),
    onPrimaryContainer = Color(0xFFDEE3FF),
    secondary = Color(0xFFC3C5D9),
    onSecondary = Color(0xFF2C2F3F),
    secondaryContainer = Color(0xFF424659),
    onSecondaryContainer = Color(0xFFDFE1F6),
    tertiary = Color(0xFFDDBCE0),
    onTertiary = Color(0xFF3F2843),
    tertiaryContainer = Color(0xFF573E5B),
    onTertiaryContainer = Color(0xFFFAD7FC),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0B0B0F),
    onBackground = Color(0xFFE4E2E9),
    surface = Color(0xFF141419),
    onSurface = Color(0xFFE4E2E9),
    surfaceVariant = Color(0xFF2F2F38),
    onSurfaceVariant = Color(0xFFC6C6D0),
    surfaceContainerLowest = Color(0xFF0B0B0F),
    surfaceContainerLow = Color(0xFF141419),
    surfaceContainer = Color(0xFF1C1C23),
    surfaceContainerHigh = Color(0xFF25252D),
    surfaceContainerHighest = Color(0xFF2F2F38),
    outline = Color(0xFF90909A),
    outlineVariant = Color(0xFF45464F),
)

/**
 * One UI 9 排版：强调大标题 + 紧凑正文
 */
val OneUiTypography = Typography(
    displayLarge = TextStyle(
        fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp,
        fontWeight = FontWeight.Bold,
    ),
    displayMedium = TextStyle(
        fontSize = 45.sp, lineHeight = 52.sp,
        fontWeight = FontWeight.Bold,
    ),
    displaySmall = TextStyle(
        fontSize = 36.sp, lineHeight = 44.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    headlineLarge = TextStyle(
        fontSize = 32.sp, lineHeight = 40.sp,
        fontWeight = FontWeight.Bold,
    ),
    headlineMedium = TextStyle(
        fontSize = 28.sp, lineHeight = 36.sp,
        fontWeight = FontWeight.Bold,
    ),
    headlineSmall = TextStyle(
        fontSize = 24.sp, lineHeight = 32.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleLarge = TextStyle(
        fontSize = 22.sp, lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleSmall = TextStyle(
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
        fontWeight = FontWeight.Medium,
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp,
        fontWeight = FontWeight.Medium,
    ),
)

/**
 * One UI 9 形状系统
 */
val OneUiShapes = Shapes(
    extraLarge = RoundedCornerShape(28.dp),
    large = RoundedCornerShape(20.dp),
    medium = RoundedCornerShape(16.dp),
    small = RoundedCornerShape(12.dp),
    extraSmall = RoundedCornerShape(8.dp),
)

@Composable
fun NovaCareTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
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

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Android 16 (API 36) 强制 edge-to-edge
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = Color.Transparent.value.toInt()
            window.navigationBarColor = Color.Transparent.value.toInt()
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = OneUiTypography,
        shapes = OneUiShapes,
        content = content,
    )
}

val ScoreGood = Color(0xFF2FA36B)
val ScoreMid = Color(0xFFE8912D)
val ScoreBad = Color(0xFFE05252)

object OneUiSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
}