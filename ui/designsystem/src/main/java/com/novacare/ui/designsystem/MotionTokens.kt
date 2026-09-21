package com.novacare.ui.designsystem

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.IntOffset

/**
 * OneUI 10 流体动效令牌。
 *
 * 设计纪律：所有可交互元素的"出现 / 进入 / 反馈"统一走这里的 spring 规格，
 * 不再用 Material3 默认的短 tween。spring 的过冲(overshoot)是 OneUI 手感的
 * 关键——它让元素"落到位"而不是"滑到位"。
 *
 * 两套类型参数：
 *   - Float 版（emphasized / standard / decisive）用于 `animateFloatAsState`、`animateDpAsState` 等
 *     标量值动画。
 *   - IntOffset 版（emphasizedOffset / standardOffset / decisiveOffset）用于
 *     `slideInHorizontally / Vertically(...)` 等接收 `FiniteAnimationSpec<IntOffset>` 的 API。
 *
 * 尊重 [LocalReduceMotion]：无障碍用户关闭动效时，组件应改用瞬时切换
 * （见各组件里的 `if (reduceMotion()) snapTo else MotionTokens.xxx` 惯用法）。
 */
object MotionTokens {
    /** 强调反馈（按钮、确认）：明显过冲，落感强 */
    val emphasized: FiniteAnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy, // 0.6
        stiffness = Spring.StiffnessMedium,          // ~600
    )
    val emphasizedOffset: FiniteAnimationSpec<IntOffset> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    /** 标准过渡（卡片进入、页面切换）：轻微过冲，顺滑 */
    val standard: FiniteAnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy, // 0.8
        stiffness = Spring.StiffnessMediumLow,          // ~400
    )
    val standardOffset: FiniteAnimationSpec<IntOffset> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** 退场 / 收起：克制，几乎不过冲 */
    val decisive: FiniteAnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy, // 1.0
        stiffness = Spring.StiffnessHigh,           // ~1000
    )
    val decisiveOffset: FiniteAnimationSpec<IntOffset> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh,
    )
}

/** 当前是否应减弱动效（无障碍偏好） */
@Composable
@ReadOnlyComposable
fun reduceMotion(): Boolean = LocalReduceMotion.current