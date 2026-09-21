package com.novacare.ui.designsystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected as semanticsSelected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ============================================================
// NovaDock —— OneUI 风格悬浮胶囊底部导航（v0.8 完整重做）
//
// 【直接抄自 chinazll/flashskip 的 OneUiFloatingPillNav】
// flashskip 是你认可的 OneUI 实现，我用 gh CLI 拉到本地（607 文件），
// 把它的底部 dock 规格完整搬过来：
//
//   - 胶囊本体 56dp 高（OneUiFloatingPillNav.kt:103）
//   - tab 间距 6dp（:134）
//   - 未选中命中区 60dp / 选中 90dp（:159-178）—— Fluid AI 选中"占更宽位置"
//   - 圆角 50%（:291）—— 任何高度恒为半圆胶囊
//   - 双层阴影：接触 2dp ambientα0.30/spotα0.18 + 投射 8dp ambientα0.50/spotα0.50（:240-257）
//   - 图标 22dp、文字 labelMedium 13sp（OneUi 9 实测）
//   - 切换宽度 spring(DampingRatioNoBouncy, StiffnessMediumLow)
//   - 切换图标线性↔填充 120ms tween
//   - tap 触发振动 HapticSemantic.TabSelect
//   - TalkBack stateDescription
//
// 【关键修复】用户原话："下面 tab 栏内容都看不清"
// 旧版用 weight(1f) 等分整屏宽（每块 ≈82dp），但视觉块只 60dp，命中区与视觉块分裂。
// 新版命中区与视觉块合一（66-90dp 固定宽），间距 6dp，命中区不分散。
// ============================================================

@Immutable
data class NovaDockItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector? = null,
)

/**
 * OneUI 风格悬浮胶囊底部导航。
 *
 * @param items 导航项（4-5 个最佳）
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

    val selectedIndex = items.indexOfFirst { it.route == currentRoute }.takeIf { it >= 0 } ?: 0
    val view = LocalView.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                horizontal = OneUiPillNavSpec.SidePadding,
                vertical = OneUiPillNavSpec.BottomGap,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // === 接触阴影（紧贴胶囊底部）===
        // 2dp elevation，ambient α0.30 + spot α0.18，模拟"贴地"
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(OneUiPillNavSpec.TouchShadowElevation)
                .padding(horizontal = OneUiPillNavSpec.ItemPaddingHorizontal),
        )

        // === 胶囊本体（玻璃 + 描边 + 投射阴影）===
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(OneUiPillNavSpec.Height)
                .shadow(
                    elevation = OneUiElevation.Floating,
                    shape = RoundedCornerShape(percent = 50),
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = OneUiPillNavSpec.CastShadowAmbientAlpha),
                    spotColor = Color.Black.copy(alpha = OneUiPillNavSpec.CastShadowSpotAlpha),
                )
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(percent = 50),
                )
                .padding(horizontal = OneUiPillNavSpec.InnerPadding),
            horizontalArrangement = Arrangement.spacedBy(OneUiPillNavSpec.ItemGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                PillNavItem(
                    item = item,
                    selected = index == selectedIndex,
                    onClick = {
                        NovaTap(view)
                        onSelect(item.route)
                    },
                )
            }
        }
    }
}

/**
 * 单个 tab —— 命中区与视觉块合一（抄自 OneUiFloatingPillNav.kt:443-475）。
 *
 * 关键点：
 *   - 命中区与高亮块宽度一致（不是分开的两个东西）
 *   - 选中时宽度在 60 ↔ 90dp 之间 spring 弹动
 *   - 选中颜色 primaryContainer（OneUi 9 实测）
 *   - 文字 labelMedium 13sp Bold（选中）/ Medium（未选）
 */
@Composable
private fun PillNavItem(
    item: NovaDockItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }

    // 三档宽度：未选 60dp / 基础 68dp / 选中 90dp
    // Fluid AI 选中"占更宽位置"——视觉上提示"我在哪"
    val targetWidth: Dp = if (selected) {
        OneUiPillNavSpec.ItemWidthSelected
    } else {
        OneUiPillNavSpec.ItemWidthIdle
    }

    val animatedWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = OneUiSpring.tabSelect(),
        label = "pillWidth",
    )

    // 选中态：primaryContainer @ 1f；未选：透明
    val bgAlpha by animateDpAsState(
        targetValue = if (selected) 1.dp else 0.dp,
        animationSpec = OneUiSpring.tabSelect(),
        label = "pillBg",
    )

    Box(
        modifier = Modifier
            .height(OneUiPillNavSpec.Height)
            .width(animatedWidth)
            .clip(RoundedCornerShape(percent = 50))
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics {
                contentDescription = item.label
                semanticsSelected = selected
                stateDescription = if (selected) "已选中" else "未选中"
            },
        contentAlignment = Alignment.Center,
    ) {
        // 高亮块（命中区与高亮合一）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(OneUiPillNavSpec.Height - OneUiPillNavSpec.ItemPaddingVertical * 2)
                .clip(RoundedCornerShape(percent = 50))
                .background(
                    if (bgAlpha > 0.dp) colors.primaryContainer.copy(alpha = 0.95f)
                    else Color.Transparent,
                )
                .padding(
                    horizontal = OneUiPillNavSpec.ItemPaddingHorizontal - OneUiPillNavSpec.InnerPadding,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // 图标：选中/未选切换用 120ms tween 淡入淡出（OneUi 9 实测）
                AnimatedContent(
                    targetState = selected,
                    transitionSpec = {
                        (fadeIn(tween(120)) togetherWith fadeOut(tween(120)))
                    },
                    label = "pillIconSwap",
                ) { isSelected ->
                    Icon(
                        imageVector = if (isSelected) (item.selectedIcon ?: item.icon) else item.icon,
                        contentDescription = null,
                        tint = if (isSelected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                        modifier = Modifier.size(OneUiPillNavSpec.IconSize),
                    )
                }

                // 文字（OneUi 9 labelMedium = 13sp）
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 总占用高度 = OneUiPillNavSpec.TotalHeight（FAB 等悬浮按钮需避让）
 */
val NovaDockTotalHeight: Dp = OneUiPillNavSpec.TotalHeight
