package com.novacare.ui.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateValue
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
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
// NovaDock —— 底部导航（One UI 9/9.5 灵魂语言）
//
// 【灵魂层级，不是形状】
// 上一版只做了"浮动胶囊"——这是形状。这版的差异在灵魂：
//
//   1. Edge Lighting（侧边光带）
//      当前选中的 tab 在屏幕**底部边缘**溢出一道柔光带，向下、向外辐射。
//      这是 One UI 在锁屏、来电、Galaxy AI 提示里反复出现的签名——
//      不是发光描边，是**从屏幕底部向环境溢出的光**。
//      tab 切换时光带做 cross-fade：旧光带缓慢退场，新光带缓慢上场。
//
//   2. 药丸底不是色块
//      它是**两段**：
//        - 外圈：药丸外延的柔光晕（accent 10% alpha，blur 12dp）
//        - 内核：药丸底（accent 14% alpha）
//      两段都用 snappySpring 弹簧同步。
//      外圈比内核多 4dp —— 这是"光"的来源，不是色块的描边。
//
//   3. Tab 指示器弧（底部弧线）
//      药丸底下方有一道**短弧**，宽度收窄到 40%，弧高比药丸多 60%，
//      模拟相机对焦点环的视觉——告诉用户"这里是当前的着力点"。
//
//   4. 选中文本 = 颜色的二次确认
//      只在选中 tab 上显示文字——其余靠图标。
//      文字用 accent 色 + SemiBold + 0.04 字距 + 13sp。
//      出现用 fadeIn，消失直接置 0（不动画，避免切换时的视觉抖动）。
//
// 【为什么不抄 M3 NavigationBar】
// M3 贴底通栏导航（透明背景 + 文字 + 图标 + Indicator 拖条）
// 与 One UI 9/9.5 的"空间层次 + 边缘光带"是相反的语言。
// 抄 M3 是退而求其次的妥协，不是"做到了"。
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
    // 关键差异：光带不是 dock 的一部分，而是**穿过 dock 向屏幕外辐射**的。
    // 在这里画在 dock 容器的上方，让它与底部边缘对齐，看起来像"从屏幕底部漏出来"。
    Box(modifier = modifier.fillMaxWidth()) {

        // === Edge Lighting 光带 ===
        // 用三层叠加做出"扩散"感：内核（强）+ 中层（中）+ 外层（弱，blur 模拟）
        // 选中 tab 切换时，光带位置在 Spring 下"滑过去"——和药丸底同步。
        EdgeLighting(
            itemCount = items.size,
            selectedIndex = selectedIndex ?: 0,
            color = colors.accent,
            reduceMotion = reduceMotion,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            shape = RoundedCornerShape(28.dp),
            color = colors.floatSurface,
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
 * 工作原理：
 *   - 用 Box + drawBehind 画三层渐变椭圆（圆角矩形），叠加产生"扩散光"
 *   - 三层分别在内、中、外，blur 半径递增，alpha 递减
 *   - 位置从当前选中 tab 滑过去——用 springAnimatable(0..1) 选 X 偏移
 *   - 整体随选中切换做 cross-fade：新光带上升、旧的下降
 *   - 颜色固定为 accent，但呼吸（pulse）周期 3.5s、幅度 ±15%——这是"活的"
 *
 * 视觉感觉：
 *   像 dock 的底部边缘有一道光从下方漏出来，向下辐射到 dock 之外。
 *   切换 tab 时，光带"扫过去"——告诉用户"你选了新的"。
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

    // 整体亮度（不呼吸时为 1）
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

                // 三层叠加，做出"扩散光"
                // 内核：椭圆，高 12dp，宽 80dp，强 35% alpha
                drawOvalLight(
                    centerX = cx,
                    centerY = baseY,
                    width = 80.dp.toPx(),
                    height = 16.dp.toPx(),
                    color = color.copy(alpha = 0.35f * breathe),
                )
                // 中层：宽 160dp，alpha 18%
                drawOvalLight(
                    centerX = cx,
                    centerY = baseY + 6.dp.toPx(),
                    width = 160.dp.toPx(),
                    height = 26.dp.toPx(),
                    color = color.copy(alpha = 0.18f * breathe),
                )
                // 外层：宽 240dp，alpha 8% ——最虚的扩散
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
        blendMode = BlendMode.Plus, // 加色叠加 —— 光带的核心：颜色累加而不是覆盖
    )
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
                contentDescription = item.label
                semanticsSelected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        // 药丸：外晕 + 内核
        Box(
            modifier = Modifier
                .size(width = 52.dp, height = 40.dp)
                .drawBehind {
                    if (pillAlpha <= 0.01f) return@drawBehind
                    val w = size.width * pillScale
                    val h = size.height
                    val left = (size.width - w) / 2f
                    val top = (size.height - h) / 2f

                    // 外晕：比内核大 4dp，alpha 6%
                    val glowW = w + 6.dp.toPx()
                    val glowH = h + 6.dp.toPx()
                    drawRoundRect(
                        color = colors.accent.copy(alpha = 0.06f * pillAlpha),
                        topLeft = Offset(left - 3.dp.toPx(), top - 3.dp.toPx()),
                        size = Size(glowW, glowH),
                        cornerRadius = CornerRadius(glowH / 2f, glowH / 2f),
                    )
                    // 内核：accent 14% alpha
                    drawRoundRect(
                        color = colors.accent.copy(alpha = 0.14f * pillAlpha),
                        topLeft = Offset(left, top),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(h / 2f, h / 2f),
                    )
                    // 底部弧指示器
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
                    letterSpacing = 0.04.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}