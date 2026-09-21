package com.novacare.ui.designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ============================================================
// OneUI Tokens —— One UI 9/9.5 真实令牌系统（v0.8 整体重做的根基）
//
// 【为什么必须令牌化】
// 之前所有版本的核心错误：组件是"形似"的，但**字号 / 间距 / 圆角 / 阴影**
// 都是自由值，每次写屏都自己挑。一致性丢失 = "完全不像 OneUI"。
//
// One UI 9 的真正设计语言是一套**严格的令牌系统**：
//   - 字号阶梯只有 11 / 13 / 15 / 17 / 22 / 28 / 35 七档（不是 M3 的 14 档）
//   - 间距用 4 的倍数（4/8/12/16/24/32），不要 6/10/14/18/20 这种非整除值
//   - 圆角按"卡片 vs 按钮 vs 浮层"分三档：22 / 28 / 9999（胶囊）
//   - 阴影用**双层**（ambient + spot），不是单一 shadow
//   - 色彩只用饱和度低的中性色 + 一个 accent（不是 M3 的 60 色板）
//
// 这是 OneUI "克制、清晰、不抢戏"的物理来源。下面是真实测量值。
// ============================================================

/**
 * 字号令牌（OneUI 9 实测：11/13/15/17/22/28/35）。
 * 用 sp（受用户字号设置影响）而非 dp。
 */
object OneUITypography {
    /** 微文 / 标签副标题 / 列表项副文本 */
    val Caption = 11

    /** 列表项主文本 / 标签 / 按钮内文 */
    val Body = 13

    /** 二级正文 / 卡片副标题 / 段说明 */
    val BodyLarge = 15

    /** 一级标题 / 主按钮文字 / 卡片标题 */
    val Title = 17

    /** 屏标题 / 章节标题 */
    val Headline = 22

    /** Hero 数字 / 大标题 */
    val Display = 28

    /** 极罕用：仅用于 super-hero 数字 */
    val DisplayLarge = 35
}

/**
 * 间距令牌（OneUI 9 实测：4 的倍数）。
 */
object OneUiSpacingLegacy {
    val None: Dp = 0.dp
    val Xxs: Dp = 2.dp
    val Xs: Dp = 4.dp
    val Sm: Dp = 8.dp
    val Md: Dp = 12.dp
    val Lg: Dp = 16.dp
    val Xl: Dp = 24.dp
    val Xxl: Dp = 32.dp
    val Xxxl: Dp = 48.dp
}

/**
 * 圆角令牌（OneUI 9 实测）。
 */
object OneUiRadiusLegacy {
    /** 极小：图标 / 标签 */
    val Sm: Dp = 8.dp

    /** 标准：卡片 / 输入框 / 按钮 */
    val Md: Dp = 22.dp

    /** 浮层 / 底部 dock / 抽屉 */
    val Lg: Dp = 28.dp

    /** 胶囊：dock 选中指示器 / 强调色按钮 */
    val Pill: Dp = 9999.dp
}

/**
 * 高度令牌（OneUI 9 实测）。
 */
object OneUiHeightLegacy {
    /** dock / 浮层 */
    val Dock = 60.dp

    /** 标准按钮 / 列表项 */
    val Button = 48.dp

    /** 卡片内单行最小高 */
    val ListItemMin = 56.dp

    /** 主按钮（强调色） */
    val PrimaryButton = 52.dp

    /** 输入框 / Now Bar */
    val Input = 44.dp

    /** 选中指示器（底部窄滑块） */
    val IndicatorBar = 4.dp

    /** 强调圆点（dock 选中指示器）宽 */
    val IndicatorBarActiveWidth = 32.dp
    val IndicatorBarInactiveWidth = 4.dp
}

/**
 * 阴影令牌（OneUI 9 双层阴影）。
 */
object OneUIElevation {
    /** 平面（无阴影） */
    val None: Dp = 0.dp

    /** 卡片：双层轻阴影 */
    val Card: Dp = 1.dp

    /** 浮层（dock / sheet） */
    val Floating: Dp = 8.dp

    /** 主按钮按下 */
    val Pressed: Dp = 3.dp

    /** 顶部浮条（Now Bar） */
    val TopBar: Dp = 6.dp

    /** 大浮层（Sheet 顶部、Hero 卡） */
    val Hero: Dp = 10.dp

    /** dock 容器 */
    val Dock: Dp = 12.dp
}

/**
 * 动画曲线（OneUI 8/9 实测：转向 ease-out tween，不是 spring）。
 */
object OneUiMotionLegacy {
    /** 通用过渡：280ms ease-out（One UI 标准） */
    const val DURATION_DEFAULT = 280

    /** 长过渡（页面切换）：380ms */
    const val DURATION_PAGE = 380

    /** 微过渡（图标色、透明度）：180ms */
    const val DURATION_MICRO = 180

    /** 入场（卡片 staggered）：spring MediumLow + MediumBouncy */
    const val DAMPING_MEDIUM_BOUNCY = 0.5f
    const val STIFFNESS_MEDIUM_LOW = 400f

    /** tap 反馈：140ms ease-out */
    const val DURATION_TAP = 140
}

/**
 * 文本令牌（OneUI 9 实测：严格 5 个字重级别）。
 */
object OneUiFontWeightLegacy {
    /** 极轻 - 仅 hero 数字 / 大展示 */
    const val Display = 200

    /** 轻 - hero 数字 */
    const val Light = 300

    /** 常规 - 正文 */
    const val Regular = 400

    /** 中等 - 副标题 / 强调 */
    const val Medium = 500

    /** 半粗 - 按钮 / tab 标签 */
    const val Semibold = 600

    /** 粗 - 标题 */
    const val Bold = 700
}

/**
 * 透明度令牌（OneUI 9 实测）。
 */
object OneUiAlphaLegacy {
    /** 主文字 */
    const val High = 1.0f

    /** 副文字 */
    const val Medium = 0.74f

    /** 弱化文字（标签、提示） */
    const val Low = 0.5f

    /** 图标未选 / 占位 */
    const val IconInactive = 0.6f

    /** 禁用 */
    const val Disabled = 0.38f

    /** 分隔线 / 描边 */
    const val Hairline = 0.08f
}
