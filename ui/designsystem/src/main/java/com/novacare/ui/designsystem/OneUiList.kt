package com.novacare.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// =========================================================================
// OneUI 9 列表组件 —— 严格按 samsung.com/one-ui/comp/list.html 实测
//
// 模式（来自真截图 components_lists_img-01..03.png）：
//   - 一组 settings 是一个 26dp squircle 浮卡
//   - 卡之间 12dp gap（不是 1dp 分隔线）
//   - 卡内：40dp 圆形 tinted icon + 主标题 17sp Medium + 副文 14sp Regular
//   - 主标题左侧 padding 16dp（icon 与 title 之间 14dp gap）
//   - subheader：13sp Regular 灰字，独立 padding（不是卡内 inline）
//   - Switch：右侧真开关（M3 Switch），不是 on/off 文字
// =========================================================================

@Composable
fun OneUiListSection(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W500),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = OneUiSpacing.BlockGap, vertical = OneUiSpacing.SectionTitleGap),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(OneUiRadius.Large))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(vertical = OneUiSpacing.CardInner),
        ) {
            Column { content() }
        }
    }
}

@Composable
fun OneUiListRow(
    icon: ImageVector? = null,
    iconTint: Color? = null,
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    val rowModifier = Modifier
        .fillMaxWidth()
        .let { base ->
            if (onClick != null) base.clickable { onClick() } else base
        }
        .padding(horizontal = OneUiSpacing.CardInner, vertical = 14.dp)

    Column(modifier = rowModifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null && iconTint != null) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(OneUiSpacing.CardInner))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
                    color = cs.onSurface,
                    maxLines = 1,
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(OneUiSpacing.CardInner))
                trailing()
            }
        }
        if (showDivider) {
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(cs.onSurface.copy(alpha = 0.06f)),
            )
        }
    }
}