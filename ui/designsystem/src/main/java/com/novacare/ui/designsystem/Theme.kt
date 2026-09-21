package com.novacare.ui.designsystem

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================
// NovaCare 设计系统 —— 令牌层
//
// 【定调说明 / Direction Lock】
//   受众：普通 Android 用户（非极客）。他们要的是「打开就知道该按哪」。
//   调性：OneUI 9 招牌蓝家族 —— 大圆角浮层、单主张强调色、
//         极浅的氛围渐变、发丝描边代替重阴影、玻璃感来自 alpha 而非模糊。
//   记忆点：首页那枚会呼吸的健康环 + 底部胶囊 dock。
//   强调色系按 OneUI 9 实测取色：浅色 #2F6FED，深色 #89BBFF。
//
// 【配色纪律 60 / 30 / 10】
//   60% 基底  —— background / surface 系列（冷调近黑，非纯黑）
//   30% 承载  —— surfaceContainer 卡片与面板
//   10% 强调  —— OneUI 9 招牌蓝，只给「主操作按钮」与「健康环」
//   语义色（好/中/差、安全/需确认/有风险）独立于强调色，绝不混用。
//
// 【为什么不用 Material You 动态取色】
//   动态取色会让品牌色被壁纸劫持 —— 健康环的颜色语义会失真，
//   同一个分数在不同壁纸下变成不同颜色。因此固定强调色，但完整支持深/浅模式。
// ============================================================

/**
 * NovaCare 扩展色彩令牌。
 *
 * Material3 的 ColorScheme 不覆盖「健康度三档」「风险分级」「环轨底色」
 * 这些业务语义色。用 CompositionLocal 补充，组件内禁止硬编码色值。
 */
@Immutable
data class NovaCareColors(
    /** 品牌强调色（电光青）—— 全应用唯一强调色 */
    val accent: Color,
    val accentDim: Color,
    /** 健康度三档（语义色，与强调色分离） */
    val healthGood: Color,
    val healthFair: Color,
    val healthPoor: Color,
    /** 清理风险分级 */
    val riskSafe: Color,
    val riskCaution: Color,
    val riskRisky: Color,
    /** 健康环未填充轨道 */
    val ringTrack: Color,
    /** 发丝描边：制造层次而不靠重阴影 */
    val hairline: Color,
    /** 氛围层渐变（极低透明度，铺在背景顶层） */
    val auraTop: Color,
    val auraBottom: Color,

    // ---- 浮动层令牌（One UI 9/9.5 风格）----
    // 关键差异：新语言用「半透明浮层 + 大扩散柔和阴影 + 发丝描边」三者叠加
    // 来表达层级，而不是单靠明度差。玻璃感来自 alpha，不来自模糊。
    /** 浮层承载面：半透明，叠在背景上会透出氛围渐变 */
    val floatSurface: Color,
    /** 浮层被按下时的加深色 */
    val floatSurfacePressed: Color,
    /** dock / 浮层的高光边（顶部内描边，模拟受光） */
    val floatHighlight: Color,
    /** 柔和外阴影色（大 blurRadius、低 alpha，绝非黑色硬阴影） */
    val softShadow: Color,
    /** 环境光阴影（ambient）：柔和大散射,One UI 卡片"漂浮感"的来源 */
    val ambientShadow: Color,
    /** 点光源阴影（spot）：清晰锐利的点光投影,One UI 卡片立体感的来源 */
    val spotShadow: Color,
)

private val LightPalette = NovaCareColors(
    // OneUI 9 招牌蓝（浅色）：实测 #2F6FED,比 Material 默认偏冷、更"夜空蓝"
    accent = Color(0xFF2F6FED),
    accentDim = Color(0xFFD5E1FB),
    healthGood = Color(0xFF1B7A46),
    healthFair = Color(0xFFA86A00),
    healthPoor = Color(0xFFB3261E),
    riskSafe = Color(0xFF1B7A46),
    riskCaution = Color(0xFFA86A00),
    riskRisky = Color(0xFFB3261E),
    ringTrack = Color(0xFFE1E7EB),
    hairline = Color(0x14000000),
    auraTop = Color(0x1A2F6FED),
    auraBottom = Color(0x0A1B4FCB),
    floatSurface = Color(0xF2FFFFFF),
    floatSurfacePressed = Color(0xFFFFFFFF),
    floatHighlight = Color(0x99FFFFFF),
    softShadow = Color(0x1F0A2A33),
    ambientShadow = Color(0x14000000), // 环境光散射：极淡,大模糊半径
    spotShadow = Color(0x1F0A2A33),    // 点光源投影：稍浓,清晰锐利
)

private val DarkPalette = NovaCareColors(
    // OneUI 9 招牌蓝（深色）：实测 #89BBFF —— 比浅色更亮、给深色底足够对比度
    accent = Color(0xFF89BBFF),
    accentDim = Color(0xFF1B3A7A),
    healthGood = Color(0xFF6FE0A0),
    healthFair = Color(0xFFFFC46B),
    healthPoor = Color(0xFFFF8A80),
    riskSafe = Color(0xFF6FE0A0),
    riskCaution = Color(0xFFFFC46B),
    riskRisky = Color(0xFFFF8A80),
    ringTrack = Color(0xFF22303A),
    hairline = Color(0x1AFFFFFF),
    auraTop = Color(0x2E89BBFF),
    auraBottom = Color(0x00101820),
    floatSurface = Color(0xE61C242B),
    floatSurfacePressed = Color(0xF22A343C),
    floatHighlight = Color(0x1FFFFFFF),
    softShadow = Color(0x66000000),
    ambientShadow = Color(0x55000000), // 深色下 ambient 仍需可见但不过重
    spotShadow = Color(0x80000000),    // spot 在深色下更深,提供清晰的边缘投影
)

val LocalNovaCareColors = staticCompositionLocalOf { DarkPalette }

/** 无障碍：`prefers-reduced-motion` 的 Compose 等价物 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** 便捷访问：`NovaCareTheme.colors.healthGood` */
object NovaCareTheme {
    val colors: NovaCareColors
        @Composable @ReadOnlyComposable
        get() = LocalNovaCareColors.current
}

// ---- Material3 角色映射 ----

private val LightScheme = lightColorScheme(
    // OneUI 9 招牌蓝（浅色）：实测 #2F6FED —— 比 Material 默认偏冷、更"夜空蓝"
    primary = Color(0xFF2F6FED),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD5E1FB),
    onPrimaryContainer = Color(0xFF0B1F4D),
    secondary = Color(0xFF5A6275),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0E4F1),
    onSecondaryContainer = Color(0xFF161B2A),
    tertiary = Color(0xFF6E5B7E),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF15171C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF15171C),
    surfaceVariant = Color(0xFFE1E5EE),
    onSurfaceVariant = Color(0xFF44495A),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F3F9),
    surfaceContainer = Color(0xFFEAEDF4),
    surfaceContainerHigh = Color(0xFFE4E7EF),
    surfaceContainerHighest = Color(0xFFDDE1EB),
    outline = Color(0xFF73778A),
    outlineVariant = Color(0xFFC3C8D3),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val DarkScheme = darkColorScheme(
    // OneUI 9 招牌蓝（深色）：实测 #89BBFF —— 比浅色更亮、给深色底足够对比度
    primary = Color(0xFF89BBFF),
    onPrimary = Color(0xFF0B2447),
    primaryContainer = Color(0xFF1B3A7A),
    onPrimaryContainer = Color(0xFFD5E1FB),
    secondary = Color(0xFFC0C5DD),
    onSecondary = Color(0xFF292F40),
    secondaryContainer = Color(0xFF3F455A),
    onSecondaryContainer = Color(0xFFE0E4F1),
    tertiary = Color(0xFFD4C3E3),
    onTertiary = Color(0xFF3B2D4C),
    // 深色底：带冷调的近黑，不用纯黑（OLED 纯黑会让浮层边界消失）
    background = Color(0xFF12151A),
    onBackground = Color(0xFFE6E8EE),
    // 卡片浮起一级 —— 用亮度分层替代重阴影（现代 Android 的做法）
    surface = Color(0xFF181C22),
    onSurface = Color(0xFFE6E8EE),
    surfaceVariant = Color(0xFF44495A),
    onSurfaceVariant = Color(0xFFC3C8D3),
    surfaceContainerLowest = Color(0xFF0C0F13),
    surfaceContainerLow = Color(0xFF161A20),
    surfaceContainer = Color(0xFF1C2127),
    surfaceContainerHigh = Color(0xFF24292F),
    surfaceContainerHighest = Color(0xFF2E343B),
    outline = Color(0xFF8B91A2),
    outlineVariant = Color(0xFF44495A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

// ============================================================
// 字体
//
// 约束：Android 上无法像 Web 那样随意加载展示字（包体积 + 版权 + 中文缺字）。
// 用 FontFamily.SansSerif（设备厂商字体 —— One UI 上是 Samsung Sans，
// MIUI 上是 Mi Sans）作为正文，这是「系统原生感」的正确选择。
//
// 差异化靠**字重 / 字号 / 字距 / 行高**的精确控制实现，
// 比硬塞 Web 字体更可靠，且不破坏系统中文渲染。
// ============================================================

// ============================================================
// OneUI 严格 7 档字号 (实测：11/13/15/17/22/28/35 sp)
//
// 关键差异（vs Material3 默认 14 档）：
//   - 中间档全部被删。OneUI 不存在 19sp / 21sp / 25sp 这种"我以为差不多"的档位。
//   - Display = 35sp W200 lineHeight 38 letterSpacing -0.02：仅 hero 数字 / super-hero 标题
//   - Headline = 22sp W600 lineHeight 28 -0.01：屏标题 / 章节标题
//   - Title = 17sp W600 lineHeight 26：卡片标题 / 主按钮文字
//   - BodyLarge = 15sp Normal lineHeight 24 +0.01：正文（中文 1.6 行高）
//   - Body = 13sp Normal lineHeight 22 +0.015：列表副文 / 说明
//   - Caption = 11sp Normal lineHeight 16 +0.04：微文 / 元信息
//   - Label = 11sp W600 +0.08 字距：分区眉标 / 状态 chip
// ============================================================
private val NovaTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.W200,
        fontSize = 35.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.02).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.W300,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.015).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.W300,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.01).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.W600,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.01).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.W600,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.W600,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.005.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.W600,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.W600,
        fontSize = 17.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.005.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.W500,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.01.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.01.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.015.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.04.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.W600,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.005.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.W500,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.04.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.W600,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.08.sp,
    ),
)

// 圆角体系：OneUI 4 档 + 胶囊
//   xs=8  小元素（缩略图、勾选框）
//   sm=14 按钮 / 输入框 / 列表项底色
//   md=20 标准卡片
//   lg=26 大卡片 / 浮层
//   胶囊 9999 仅 dock / 强调按钮
private val NovaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

// ============================================================
// 动效令牌
//
// 【为什么必须是弹簧而不是 tween】
//   上一版全用 tween（定时曲线），观感是「播完动画」——被动、机械、有终点感。
//   下一代语言的核心是**物理感**：元素有质量，被拖拽/释放/吸附。
//   弹簧没有固定时长，它由目标与初速度决定，这才是「活」的来源。
//
// 【阻尼纪律】三条曲线，各司其职，不许混用：
//   - Flowing  : 常态位移（页面转场、卡片进场）—— 微回弹，克制
//   - Snappy   : 交互元素（dock 指示器、开关、勾选框）—— 干脆、几乎不过冲
//   - Expressive: 强调瞬间（主按钮、dock 指示器跨越）—— 明显回弹，有性格
//
// reduceMotion 开启时，调用方需退回 tween(0) 或瞬时 —— 见 `motionAware` 辅助。
// ============================================================

/** 常态位移：有质量但不喧哗 */
fun <T> flowingSpring(): SpringSpec<T> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy * 1.2f,
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = null,
)

/** 交互元素：干脆吸附，几乎不过冲 */
fun <T> snappySpring(): SpringSpec<T> = spring(
    dampingRatio = 0.86f,
    stiffness = Spring.StiffnessMedium,
    visibilityThreshold = null,
)

/** 强调瞬间：明显回弹 —— 只在「主角动作」上用，用多就腻 */
fun <T> expressiveSpring(): SpringSpec<T> = spring(
    dampingRatio = 0.62f,
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = null,
)

/**
 * 应用主题入口。
 *
 * @param darkTheme 默认跟随系统
 * @param reduceMotion 无障碍降级：关闭环动画与入场序列
 */
@Composable
fun NovaCareTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val novaColors = if (darkTheme) DarkPalette else LightPalette
    CompositionLocalProvider(
        LocalNovaCareColors provides novaColors,
        LocalReduceMotion provides reduceMotion,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = NovaTypography,
            shapes = NovaShapes,
            content = content,
        )
    }
}
