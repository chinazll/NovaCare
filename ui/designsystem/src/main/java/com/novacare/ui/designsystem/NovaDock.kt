package com.novacare.ui.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected as semanticsSelected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================
// NovaDock —— 底部导航（One UI 9/9.5 灵魂语言 v2）
//
// 【灵魂层级，v2 差异】
// 上一版做了"浮动胶囊 + 药丸外晕 + 底部弧指示器"——这是形状堆叠。
// 这一版的差异在**动作的物理质感**，全部按 One UI 9/9.5 真实设计语言改：
//
//   1. 高度 60dp（之前是 62dp）—— One UI 标准底栏高度
//   2. 选中指示器 = 底部窄滑块
//      - 选中：32dp 宽 × 4dp 高 × 8dp 圆角（One UI 9 标志性"indicating bar"）
//      - 未选：4dp 宽 × 4dp 高 × 8dp 圆角（最小呼吸点）
//      - 宽度用 animateDpAsState 在 4 ↔ 32 之间"长出来",不是颜色变换
//      - 切换用 tween(280, FastOutSlowInEasing) —— One UI 真实选择，
//        不是 spring（避免选不中的时候指示器"弹"得太狠）
//   3. 图标颜色用 animateColorAsState 在 accent / onSurfaceVariant.0.6 之间平滑过渡
//   4. **不再**改图标大小（那是假高斯）：选中态通过图标内 inset 缩进做"放大"视感
//   5. 点击 NovaTap()：振动反馈（One UI 用户感知 50% 来源）
//
//   Edge Lighting（侧边光带）保留,继续表达"从屏幕底部溢出的光"。
// ============================================================

/** 一个 dock 项。 */
@Immutable
data class NovaDockItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector? = null,
)

/**
 * 浮动底部导航 + 底部边缘光带（One UI 9/9.5 Edge Lighting）。
 *
 * @param items 导航项（建议 4~5 个；超过 5 个会挤压文字）
 * @param currentRoute 当前路由
 * @param onSelect 点击回调
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
    val reduceMotion = LocalReduceMotion.current
    val selectedIndex = items.indexOfFirst { it.route == currentRoute }.takeIf { it >= 0 }

    // === Edge Lighting：屏幕底部溢出的柔光带 ===
    Box(modifier = modifier.fillMaxWidth()) {
        EdgeLighting(
            itemCount = items.size,
            selectedIndex = selectedIndex ?: 0,
            color = colors.accent,
            reduceMotion = reduceMotion,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            corner = 28.dp,
            blurRadius = 20.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // 高度 60dp：One UI 9 真实底栏高度（之前 62dp 是 M3 NavigationBar）
                    .height(60.dp)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                items.forEachIndexed { index, item ->
                    DockTab(
                        item = item,
                        selected = index == selectedIndex,
                        showLabel = index == selectedIndex,
                        onClick = { onSelect(item.route) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * Edge Lighting —— One UI 9/9.5 的标志性侧边光带。
 *
 * 三层叠加椭圆模拟"扩散光",位置随选中 tab 滑动。
 */
@Composable
private fun EdgeLighting(
    itemCount: Int,
    selectedIndex: Int,
    color: Color,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val infinite = rememberInfiniteTransition(label = "edgeLight")

    // 呼吸：3.5s 一周期，±15% 强度
    val pulse by infinite.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (reduceMotion) 0 else 3500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "edgePulse",
    )

    val breathe = if (reduceMotion) 1f else pulse

    // 光带在 dock 内的位置（动画迁移）
    val targetPos = if (itemCount <= 1) 0.5f else
        (selectedIndex + 0.5f) / itemCount.toFloat()

    val animatedPos by animateFloatAsState(
        targetValue = targetPos,
        animationSpec = if (reduceMotion) tween(0) else expressiveSpring(),
        label = "edgePos",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .drawBehind {
                if (itemCount <= 0) return@drawBehind
                val w = size.width
                val cx = w * animatedPos
                val baseY = size.height - 4.dp.toPx()

                drawOvalLight(
                    centerX = cx,
                    centerY = baseY,
                    width = 80.dp.toPx(),
                    height = 16.dp.toPx(),
                    color = color.copy(alpha = 0.35f * breathe),
                )
                drawOvalLight(
                    centerX = cx,
                    centerY = baseY + 6.dp.toPx(),
                    width = 160.dp.toPx(),
                    height = 26.dp.toPx(),
                    color = color.copy(alpha = 0.18f * breathe),
                )
                drawOvalLight(
                    centerX = cx,
                    centerY = baseY + 14.dp.toPx(),
                    width = 240.dp.toPx(),
                    height = 38.dp.toPx(),
                    color = color.copy(alpha = 0.08f * breathe),
                )
            },
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawOvalLight(
    centerX: Float,
    centerY: Float,
    width: Float,
    height: Float,
    color: Color,
) {
    drawRoundRect(
        color = color,
        topLeft = Offset(centerX - width / 2f, centerY - height / 2f),
        size = Size(width, height),
        cornerRadius = CornerRadius(width / 2f, height / 2f),
        blendMode = BlendMode.Plus,
    )
}

/**
 * 单个 tab —— 核心是底部窄滑块指示器。
 *
 * 关键差异（v2）：
 *   - 选中指示器宽度在 4dp ↔ 32dp 之间 animateDpAsState（One UI 9 真实语言）
 *   - 切换动画用 tween(280, FastOutSlowInEasing) 而非 spring（One UI 8 真实选择）
 *   - 图标颜色用 animateColorAsState 在 accent / onSurfaceVariant.0.6 之间平滑
 *   - 不再调图标 size —— 用 inset（内 padding）做"放大"视感（避免图标 sprite 模糊）
 *   - 点击触发 NovaTap() —— 振动反馈
 */
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

    // === 选中指示器宽度：4dp ↔ 32dp ===
    // One UI 8/9 真实选择是 tween + FastOutSlowInEasing（280ms）,
    // 不用 spring —— 因为这个动画是"选 A → 选 B"的离散迁移,
    // spring 的回弹会让切换看起来"犹豫"。
    val indicatorWidth by animateDpAsState(
        targetValue = if (selected) 32.dp else 4.dp,
        animationSpec = tween(
            durationMillis = if (reduceMotion) 0 else 280,
            easing = androidx.compose.animation.core.FastOutSlowInEasing,
        ),
        label = "dockIndicatorWidth",
    )

    // === 图标颜色：accent / onSurfaceVariant.0.6 ===
    val iconColor by animateColorAsState(
        targetValue = when {
            selected -> colors.accent
            pressed -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        },
        animationSpec = tween(if (reduceMotion) 0 else 200),
        label = "dockIconColor",
    )

    // === 图标 inset：选中时图标略放大（缩进 4dp → 2dp） ===
    // 不改 Icon 自身 size,改用 padding 做"放大" —— 避免 sprite 在
    // 尺寸变化时重新光栅化模糊（这是 Material 3 NavigationBar 的视觉缺陷）。
    val iconInset by animateDpAsState(
        targetValue = if (selected) 2.dp else 4.dp,
        animationSpec = tween(
            durationMillis = if (reduceMotion) 0 else 280,
            easing = androidx.compose.animation.core.FastOutSlowInEasing,
        ),
        label = "dockIconInset",
    )

    val labelAlpha by animateFloatAsState(
        targetValue = if (showLabel) 1f else 0f,
        animationSpec = tween(if (reduceMotion) 0 else 160),
        label = "dockLabelAlpha",
    )

    val view = LocalView.current

    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(20.dp))
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                // 点击时先振动再执行路由切换 —— NovaTap 是"我点到了"的物理确认
                onClick = {
                    NovaTap(view)
                    onClick()
                },
            )
            .semantics {
                contentDescription = item.label
                semanticsSelected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // === 图标 ===
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = iconInset),
            ) {
                Icon(
                    imageVector = if (selected) (item.selectedIcon ?: item.icon) else item.icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp),
                )
                if (labelAlpha > 0.02f) {
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = item.label,
                        color = colors.accent.copy(alpha = labelAlpha),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.04.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // === 底部指示器滑块 ===
            // 始终存在但宽度会变化 —— 这是 One UI 9 "indicating bar" 的物理感来源。
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .height(4.dp)
                    .width(indicatorWidth)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selected) colors.accent
                        else colors.accent.copy(alpha = 0.28f),
                    ),
            )
        }
    }
}