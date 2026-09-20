package com.novacare.ui.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected as semanticsSelected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================
// NovaDock —— 底部导航（One UI 9/9.5 语言）
//
// 【为什么必须做这个】
//   上一版根本没有底部导航：六个功能页全靠首页的 StatCard 间接跳转，
//   助手要靠卡片进，设置是个角落里的 chip。后果是用户装上之后
//   「不知道从哪用」——功能都在，但入口不可见。
//   底部导航不是装饰，是「可用性」的第一前提。
//
// 【为什么不直接抄 M3 NavigationBar】
//   M3 规范里导航是「贴底、通栏、无容器」的。下一代语言（One UI 9+、
//   M3E 的浮动 dock、iOS 26 的 Liquid Glass）走向了相反方向：
//   **脱离屏幕边缘的浮动胶囊**。理由有三，都是真实收益而非好看：
//     1. 浮动 = 有边界 = 用户能感知「这是一层控件」，而非内容的一部分
//     2. 脱离边缘后，边缘手势（返回、Home）不再与导航栏争夺热区
//     3. 半透明浮层叠在内容上，滚动时内容从它下方穿过，空间感真实
//
// 【选中态为什么不只换颜色】
//   颜色是弱信号，在户外强光或色觉障碍下会失效。
//   这里用**三重冗余**表达选中：滑动药丸底 + 图标填充 + 文字出现。
//   任一路径失效，其余仍可辨。
//
// 【动效内核】
//   药丸在 tab 之间**滑过去**（expressiveSpring），不是淡出淡入。
//   这是刻意的：滑动让用户建立起「这是一个连续控件」的空间认知，
//   淡入淡出则会让每次切换都像重新加载。
// ============================================================

/** 一个 dock 项。图标用 `ImageVector`（filled / outlined 两态由调用方给）。 */
@Immutable
data class NovaDockItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    /** 选中态图标。若非空则选中时切换为它（填充版），否则沿用 [icon] */
    val selectedIcon: ImageVector? = null,
)

/**
 * 浮动底部导航。
 *
 * @param items 导航项（建议 4~5 个；超过 5 个会挤压文字，导航变难认）
 * @param currentRoute 当前路由，用于高亮
 * @param onSelect 点击回调
 * @param modifier 外层修饰符
 */
@Composable
fun NovaDock(
    items: List<NovaDockItem>,
    currentRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    val colors = NovaCareTheme.colors
    val selectedIndex = items.indexOfFirst { it.route == currentRoute }
        .takeIf { it >= 0 }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(28.dp),
        color = colors.floatSurface,
        // 发丝描边 + 顶部受光高光：玻璃感来自这两条线，而不是模糊
        border = BorderStroke(1.dp, colors.hairline),
        shadowElevation = 12.dp,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            items.forEachIndexed { index, item ->
                DockTab(
                    item = item,
                    selected = index == selectedIndex,
                    // 有选中项时，其余项压缩为「仅图标」，把空间让给选中项的文字。
                    // 这是 One UI 的取舍：标签常驻会让 5 格变挤，常隐又不便扫读。
                    // 折中——只显示当前所在页的名字，其余靠图标 + 无障碍描述。
                    showLabel = index == selectedIndex,
                    onClick = { onSelect(item.route) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DockTab(
    item: NovaDockItem,
    selected: Boolean,
    showLabel: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NovaCareTheme.colors
    val reduceMotion = LocalReduceMotion.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    // 药丸底：弹簧铺开。reduceMotion 时退回短 tween，不做位移抖动。
    val pillAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = if (reduceMotion) tween(0) else snappySpring(),
        label = "dockPillAlpha",
    )
    val pillScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.72f,
        animationSpec = if (reduceMotion) tween(0) else expressiveSpring(),
        label = "dockPillScale",
    )

    val iconColor by animateColorAsState(
        targetValue = when {
            selected -> colors.accent
            pressed -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
        },
        animationSpec = tween(if (reduceMotion) 0 else 180),
        label = "dockIconColor",
    )

    val iconSize by animateDpAsState(
        targetValue = if (pressed && !reduceMotion) 21.dp else 23.dp,
        animationSpec = if (reduceMotion) tween(0) else snappySpring(),
        label = "dockIconSize",
    )

    val labelAlpha by animateFloatAsState(
        targetValue = if (showLabel) 1f else 0f,
        animationSpec = tween(if (reduceMotion) 0 else 160),
        label = "dockLabelAlpha",
    )

    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(20.dp))
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics {
                // 屏幕阅读器永远能读到标签，无论视觉上是否显示
                contentDescription = item.label
                semanticsSelected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        // ---- 药丸底 + 弧形指示器 ----
        // 用 Canvas 画：一个圆角胶囊 + 底部一条更短的强调弧线。
        // 弧线是多余的装饰吗？不是 —— 它给出「重心」，
        // 让药丸看起来是被机身高亮照亮的凹槽，而不是一块贴上去的色块。
        Box(
            modifier = Modifier
                .size(width = 52.dp, height = 40.dp)
                .drawBehind {
                    if (pillAlpha <= 0.01f) return@drawBehind
                    val w = size.width * pillScale
                    val h = size.height
                    val left = (size.width - w) / 2f
                    val top = (size.height - h) / 2f

                    drawRoundRect(
                        color = colors.accent.copy(alpha = 0.14f * pillAlpha),
                        topLeft = Offset(left, top),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(h / 2f, h / 2f),
                    )
                    // 底部强调弧：宽度收窄到 40%，贴住胶囊下缘内侧
                    val arcW = w * 0.4f
                    val arcH = h * 1.6f
                    drawRoundRect(
                        color = colors.accent.copy(alpha = 0.85f * pillAlpha),
                        topLeft = Offset(left + (w - arcW) / 2f, top + h - arcH * 0.72f),
                        size = Size(arcW, arcH),
                        cornerRadius = CornerRadius(arcH / 2f, arcH / 2f),
                    )
                },
        )

        // ---- 图标 + 可选标签 ----
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = if (selected) (item.selectedIcon ?: item.icon) else item.icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(iconSize),
            )
            if (labelAlpha > 0.02f) {
                Spacer(Modifier.width(7.dp))
                Text(
                    text = item.label,
                    color = colors.accent.copy(alpha = labelAlpha),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 底部操作位（thumb-zone 安全区）。
 *
 * 理由：屏幕下半部分是拇指自然活动区，主操作放底部比放顶部
 * 点击更快、误触更少。但底部又常被导航栏占据——所以主操作要
 * **浮在 dock 之上**，并与 dock 保持视觉区分（实心强调色 vs 半透明玻璃）。
 *
 * 内容底部留白交给调用方；此处只负责「钉在底部 + 抬离 dock」。
 */
@Composable
fun NovaBottomActionBar(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
