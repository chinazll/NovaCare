package com.novacare.ui.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

// ============================================================
// StaggerFlyIn —— One UI 9/9.5 群组飞入（stagger animation）
//
// 【灵魂特征】
// One UI 9 的标志性入场：多个组件按窗口顺序飞入，每个错开 40-80ms，
// 飞入轨迹不是直的，是带自然减速的弧线。最后一个落位的总是主 CTA ——
// 因为主 CTA 是用户应该看到的最后一个东西。
//
// 上一版的入场是「整页同时 fadeIn」—— 这是 2015 年的做法。
// 群组飞入给用户**注意力引导**：从上到下，你的视线被脚本带过一遍，
// 最后落在最重要的元素上。",
//
// 【为什么用 LaunchedEffect 而不是直接 visible=true】
//  - 同一列表同时显示多组 stagger 时，每组的 reveal 时间不互相等待
//  - 用户从首页切到清理页：按钮的存在感 + 群组节奏感同时保留
// ============================================================

/**
 * 错峰飞入的容器。
 *
 * @param index 当前元素在群组中的索引（0 = 最先入场）
 * @param staggerMs 与上一个元素的间隔（默认 50ms）
 * @param durationMs 单个元素入场动画时长（默认 360ms）
 * @param contentOffsetY 入场时 Y 方向初始偏移（默认 28dp）
 */
@Composable
fun StaggerFlyIn(
    index: Int,
    modifier: Modifier = Modifier,
    staggerMs: Long = 50L,
    durationMs: Int = 360,
    contentOffsetY: Int = 28,
    content: @Composable () -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // 用 delay 而非 ticker —— 同一 LaunchedEffect 只会执行一次，
        // 但页码重组后进入新页面会重新跑（key=Unit 实际是 Composable 离开屏幕再回来才重跑，
        // 这里的 stagger 由 index × staggerMs 决定，对同一列表是稳定的）。
        if (index > 0) {
            delay(index * staggerMs)
        }
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = if (reduceMotion) {
            fadeIn(tween(0))
        } else {
            fadeIn(tween(durationMs, delayMillis = 0)) +
                slideInVertically(
                    animationSpec = tween(durationMs),
                    initialOffsetY = { it / (40 / contentOffsetY.coerceAtLeast(1)) },
                )
        },
        modifier = modifier,
    ) {
        content()
    }
}

/**
 * 一次性飞入整组（用于页面整屏刷新的场景）。
 *
 * 用法：把页面所有顶层 `item { ... }` 替换为 `StaggerGroup(index = i) { ... }`。
 * i 来自 `items.size - 1 - index` 让最外层最后入场（即主 CTA）。
 */
@Composable
fun StaggerGroup(
    index: Int,
    reverseOrder: Boolean = true,
    modifier: Modifier = Modifier,
    staggerMs: Long = 60L,
    durationMs: Int = 420,
    contentOffsetY: Int = 32,
    content: @Composable () -> Unit,
) {
    val effectiveIndex = if (reverseOrder) -index else index
    StaggerFlyIn(
        index = effectiveIndex.coerceAtLeast(0),
        modifier = modifier,
        staggerMs = staggerMs,
        durationMs = durationMs,
        contentOffsetY = contentOffsetY,
        content = content,
    )
}