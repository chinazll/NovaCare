package com.novacare.ui.designsystem

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ============================================================
// OneUI 令牌系统 —— 直接抄自 chinazll/flashskip 的 OneUI 9 规范
//
// 来源：OneUiFloatingPillNav.kt:103 等多处。
// 这一份令牌覆盖的是 NovaCare 之前没有的整套规范：
//   圆角 26/20/14/12（卡片/控件）+ 28（胶囊/浮层）
//   间距 24/16/10/16/10/8（块/卡内/列表/屏边/章节/卡间）
//   阴影 2（卡）/ 8（浮层）
//   字号 17sp body（行高 26）13sp label（行高 21） 15sp titleSmall 等
//   色板 三星招牌蓝 #2F6FED、深色 #12151A 偏冷
// ============================================================

/** 圆角令牌（OneUI 9 真实取值） */
object OneUiRadius {
    /** Material extraLarge = 26dp — 大卡片、对话框、底部抽屉 */
    val ExtraLarge: Dp = 26.dp

    /** Material large = 20dp — 中等卡片、分组块 */
    val Large: Dp = 20.dp

    /** Material medium = 14dp — 按钮、输入框、小控件 */
    val Medium: Dp = 14.dp

    /** Material small = 12dp — 缩略图、图标底色块 */
    val Small: Dp = 12.dp

    /** 胶囊（浮动导航 dock / 浮层）— 28dp */
    val Pill: Dp = 28.dp
}

/** 间距令牌（OneUI 9 真实取值） */
object OneUiSpacing {
    /** 分组块之间 24dp */
    val BlockGap: Dp = 24.dp

    /** 卡片内四周 16dp */
    val CardInner: Dp = 16.dp

    /** 列表项之间 10dp */
    val ListItemGap: Dp = 10.dp

    /** 屏幕左右边距 16dp */
    val ScreenEdge: Dp = 16.dp

    /** Section 标题与卡片之间 10dp */
    val SectionTitleGap: Dp = 10.dp

    /** 同一 Section 内卡片之间 8dp */
    val CardGap: Dp = 8.dp

    /** 设置行上下内边距 16dp */
    val ListRowVertical: Dp = 16.dp

    /** 列表底部留白（让位胶囊）112dp */
    val EmptyHeight: Dp = 112.dp
}

/** 阴影 / Elevation 令牌 */
object OneUiElevation {
    /** 普通卡片 2dp */
    val Card: Dp = 2.dp

    /** 悬浮元素（胶囊、FAB）8dp */
    val Floating: Dp = 8.dp
}

/** 底部胶囊导航的尺寸令牌（抄自 OneUiFloatingPillNav.kt） */
object OneUiPillNavSpec {
    /** 胶囊本体高度 56dp */
    val Height: Dp = 56.dp

    /** tab 间距 6dp */
    val ItemGap: Dp = 6.dp

    /** 胶囊水平内边距 4dp */
    val InnerPadding: Dp = 4.dp

    /** 高亮块横向内边距 12dp */
    val ItemPaddingHorizontal: Dp = 12.dp

    /** 高亮块纵向内边距 6dp */
    val ItemPaddingVertical: Dp = 6.dp

    /** 未选中命中区宽度 60dp（idle 缩窄） */
    val ItemWidthIdle: Dp = 60.dp

    /** 基础命中区宽度 68dp */
    val ItemWidth: Dp = 68.dp

    /** 选中命中区宽度 90dp（Fluid AI：选中"占更宽位置"） */
    val ItemWidthSelected: Dp = 90.dp

    /** 胶囊宽度上限 */
    val MaxWidth: Dp = 460.dp

    /** 图标尺寸 22dp（选中/未选中同尺寸） */
    val IconSize: Dp = 22.dp

    /** 图标与文字竖向间距 2dp */
    val LabelSpacing: Dp = 2.dp

    /** 胶囊距屏幕下边距 12dp（不含 navBar inset） */
    val BottomGap: Dp = 12.dp

    /** 胶囊距屏幕左右边距（继承 OneUiSpacing.ScreenEdge=16dp） */
    val SidePadding = OneUiSpacing.ScreenEdge

    /** 入场上托位移（px） */
    const val EnterRisePx: Float = 60f

    /** 双层阴影：接触阴影（紧贴胶囊） */
    val TouchShadowElevation: Dp = 2.dp
    const val TouchShadowAmbientAlpha: Float = 0.30f
    const val TouchShadowSpotAlpha: Float = 0.18f

    /** 双层阴影：投射阴影（向上飘起的空气感） */
    const val CastShadowAmbientAlpha: Float = 0.50f
    const val CastShadowSpotAlpha: Float = 0.50f

    /** 胶囊总占用（含底部 12dp gap）= Height + BottomGap*2 = 80dp */
    val TotalHeight: Dp = (Height + BottomGap * 2)
}

/** Compose 全局可访问的 Active 令牌（CompositionLocal，便于覆盖） */
val LocalOneUiThemeSpec = staticCompositionLocalOf<OneUiActiveSpec> { OneUiActiveSpec.Default }

/** 当前生效的 OneUI 9 数值（以后可扩展为 1.5 / 10 主题切换） */
data class OneUiActiveSpec(
    val radius: OneUiRadius = OneUiRadius,
    val spacing: OneUiSpacing = OneUiSpacing,
    val elevation: OneUiElevation = OneUiElevation,
    val pill: OneUiPillNavSpec = OneUiPillNavSpec,
) {
    companion object {
        val Default = OneUiActiveSpec()
    }
}

/** 弹簧参数（直接抄 OneUi 8/9 Expressive Motion 选型） */
object OneUiSpring {
    /** 按压：spring(0.86f, 700f) — 高阻尼、几无回弹 */
    fun <T> press(): androidx.compose.animation.core.SpringSpec<T> =
        androidx.compose.animation.core.spring(
            dampingRatio = 0.86f,
            stiffness = 700f,
            visibilityThreshold = null,
        )

    /** 入场：spring(0.72f, StiffnessLow) — 略带回弹 */
    fun <T> enter(): androidx.compose.animation.core.SpringSpec<T> =
        androidx.compose.animation.core.spring(
            dampingRatio = 0.72f,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
            visibilityThreshold = null,
        )

    /** tab 选中宽度：spring(DampingRatioNoBouncy, StiffnessMediumLow) */
    fun <T> tabSelect(): androidx.compose.animation.core.SpringSpec<T> =
        androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
            visibilityThreshold = null,
        )
}

/** 动画时长（直接抄 OneUi 9） */
object OneUiMotion {
    /** 长过渡（颜色/尺寸）500ms */
    const val Long: Int = 500

    /** 系统 cross-fade 170ms */
    const val SystemFade: Int = 170

    /** OneUI tab 切换 240ms */
    const val TabSwitch: Int = 240

    /** 标准 easing = FastOutSlowInEasing */
    const val StandardEasingName: String = "FastOutSlowInEasing"
}
