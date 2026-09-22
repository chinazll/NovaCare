package com.novacare.feature.automation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novacare.core.model.AutomationRule
import com.novacare.core.model.TriggerType
import com.novacare.ui.designsystem.NovaCareTheme
import com.novacare.ui.designsystem.NovaTap
import com.novacare.ui.designsystem.NovaToggle
import com.novacare.ui.designsystem.OneUiAppBar
import com.novacare.ui.designsystem.OneUiListRow
import com.novacare.ui.designsystem.OneUiListSection
import com.novacare.ui.designsystem.OneUiRadius
import com.novacare.ui.designsystem.OneUiSpacing

// =========================================================================
// AutomationScreen — OneUI 9 重写版
//
// 模式：
//   - 顶部 OneUiAppBar
//   - 全部规则在一个 OneUiListSection 里，每行 = 36dp tinted icon + 规则名
//     + 描述 + 末次执行 + 启用 pill（小色块表示启用状态）
//   - 点击行 = 通知 VM startEdit（外部导航到规则编辑页）；展开行内
//     显示"停用 / 删除" pill
//   - 右下角小 56dp 圆形 primary FAB
// =========================================================================

@Composable
fun AutomationScreen(
    modifier: Modifier = Modifier,
    onOpenRule: (String) -> Unit = {},
    onCreate: () -> Unit = {},
    viewModel: AutomationViewModel = hiltViewModel(),
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val pendingDelete by viewModel.pendingDelete.collectAsStateWithLifecycle()
    val view = LocalView.current

    var expandedRuleId by remember { mutableStateOf<String?>(null) }

    val openRuleAndCollapse: (String) -> Unit = { id ->
        expandedRuleId = null
        NovaTap(view)
        onOpenRule(id)
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    horizontal = OneUiSpacing.BlockGap,
                    vertical = OneUiSpacing.SectionTitleGap,
                ),
                verticalArrangement = Arrangement.spacedBy(OneUiSpacing.CardGap),
            ) {
                item { OneUiAppBar(title = "自动化") }

                item {
                    OneUiListSection {
                        OneUiListRow(
                            icon = Icons.Outlined.AutoMode,
                            iconTint = AutomationTints.idle,
                            title = "${rules.count { it.enabled }} / ${rules.size} 已启用",
                            subtitle = "触发器命中后系统在 6 小时维护窗口内执行",
                            onClick = null,
                        )
                    }
                }

                if (notice != null) {
                    item {
                        NoticeCard(text = notice!!, onDismiss = { viewModel.dismissNotice() })
                    }
                }

                if (rules.isEmpty()) {
                    item {
                        OneUiListSection {
                            Column(modifier = Modifier.padding(OneUiSpacing.CardInner)) {
                                Text(
                                    text = "还没有自动化规则",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
                                Text(
                                    text = "新建一条规则，系统会在 6 小时维护窗口内执行，" +
                                        "不会精确到你设定的分钟。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    item {
                        Spacer(Modifier.height(OneUiSpacing.EmptyHeight))
                    }
                } else {
                    item {
                        OneUiListSection {
                            rules.forEachIndexed { index, rule ->
                                RuleRow(
                                    rule = rule,
                                    expanded = expandedRuleId == rule.id,
                                    onToggle = { enabled ->
                                        NovaToggle(view.context, enabled)
                                        viewModel.toggle(rule, enabled)
                                    },
                                    onClick = {
                                        if (expandedRuleId == rule.id) {
                                            openRuleAndCollapse(rule.id)
                                        } else {
                                            expandedRuleId = rule.id
                                        }
                                    },
                                    onEdit = { openRuleAndCollapse(rule.id) },
                                    onDelete = { NovaTap(view); viewModel.askDelete(rule) },
                                    showDivider = index < rules.lastIndex,
                                )
                            }
                        }
                    }
                    item {
                        Spacer(Modifier.height(OneUiSpacing.EmptyHeight + OneUiSpacing.BlockGap))
                    }
                }
            }

            FloatingActionButton(
                onClick = { NovaTap(view); onCreate() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = OneUiSpacing.BlockGap, bottom = OneUiSpacing.BlockGap),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "新建规则",
                )
            }
        }
    }

    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = { Text("删除规则「${pendingDelete!!.name}」？") },
            text = {
                Text(
                    "删除后下次不会执行。规则配置不保留，但执行历史（如有）随记录保留。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    NovaTap(view)
                    viewModel.confirmDelete()
                }) {
                    Text(
                        "删除",
                        style = MaterialTheme.typography.labelLarge,
                        color = NovaCareTheme.colors.riskRisky,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDelete() }) {
                    Text("取消", style = MaterialTheme.typography.labelLarge)
                }
            },
        )
    }
}

// ============================================================
// 复用件
// ============================================================

@Composable
private fun NoticeCard(text: String, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OneUiRadius.Medium))
            .background(cs.primary.copy(alpha = 0.10f))
            .clickable { onDismiss() }
            .padding(
                horizontal = OneUiSpacing.CardInner,
                vertical = OneUiSpacing.CardGap,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RuleRow(
    rule: AutomationRule,
    expanded: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    showDivider: Boolean,
) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors
    val accent = if (rule.enabled) AutomationTints.enabled else AutomationTints.disabled

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = OneUiSpacing.CardInner, vertical = OneUiSpacing.ListRowVertical),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 36dp 圆形 tinted icon（用规则名的首字）
            Box(
                modifier = Modifier
                    .size(AvatarSize)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = rule.name.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W600),
                    color = accent,
                )
            }
            Spacer(Modifier.width(OneUiSpacing.CardInner))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(OneUiSpacing.CardGap / 2))
                Text(
                    text = rule.describe(),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(OneUiSpacing.CardGap / 2))
                Text(
                    text = rule.lastRunEpochMs?.let { "上次执行：${formatRelativeTime(it)}" } ?: "尚未执行",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(OneUiSpacing.CardInner))
            // 状态指示器：启用 = 实心圆点 / 停用 = 空心
            Box(
                modifier = Modifier
                    .size(StatusDotSize)
                    .clip(CircleShape)
                    .background(
                        if (rule.enabled) cs.primary
                        else cs.onSurface.copy(alpha = 0.12f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (rule.enabled) {
                    Box(
                        modifier = Modifier
                            .size(StatusDotInner)
                            .clip(CircleShape)
                            .background(cs.onPrimary),
                    )
                }
            }
        }

        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = OneUiSpacing.CardInner * 3 + OneUiSpacing.CardGap,
                        end = OneUiSpacing.CardInner,
                        bottom = OneUiSpacing.ListRowVertical,
                    ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PillAction(
                        text = if (rule.enabled) "停用" else "启用",
                        tint = if (rule.enabled) colors.riskCaution else colors.healthGood,
                        onClick = { onToggle(!rule.enabled) },
                    )
                    Spacer(Modifier.size(OneUiSpacing.CardGap))
                    PillAction(
                        text = "编辑",
                        tint = cs.primary,
                        onClick = onEdit,
                    )
                    Spacer(Modifier.size(OneUiSpacing.CardGap))
                    PillAction(
                        text = "删除",
                        tint = colors.riskRisky,
                        onClick = onDelete,
                    )
                }
            }
        }

        if (showDivider) {
            Spacer(Modifier.height(OneUiSpacing.ListRowVertical))
            Box(
                modifier = Modifier
                    .padding(start = OneUiSpacing.CardInner * 3 + OneUiSpacing.CardGap)
                    .fillMaxWidth()
                    .height(OneUiSpacing.CardGap / 2)
                    .background(cs.onSurface.copy(alpha = 0.06f)),
            )
        }
    }
}

@Composable
private fun PillAction(
    text: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(OneUiRadius.Pill))
            .clickable { onClick() },
        color = tint.copy(alpha = 0.18f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            modifier = Modifier.padding(
                horizontal = OneUiSpacing.CardInner,
                vertical = OneUiSpacing.CardGap,
            ),
        )
    }
}

private fun AutomationRule.describe(): String = when (trigger.type) {
    TriggerType.SCHEDULED -> {
        val h = trigger.hourOfDay
        val d = trigger.dayOfWeek
        when {
            h != null && d != null -> "${AutomationViewModel.DAY_LABELS[(d - 1).coerceIn(0, 6)]} ${"%02d".format(h)} 点"
            h != null -> "每天 ${"%02d".format(h)} 点"
            else -> "按时间"
        }
    }
    TriggerType.BATTERY_BELOW -> "电量低于 ${trigger.thresholdPercent ?: 20}%"
    TriggerType.STORAGE_ABOVE -> "存储超过 ${trigger.thresholdPercent ?: 85}%"
    TriggerType.DEVICE_IDLE -> "设备空闲充电"
}

private fun formatRelativeTime(epochMs: Long): String {
    val delta = (System.currentTimeMillis() - epochMs) / 1000
    return when {
        delta < 60 -> "刚刚"
        delta < 3600 -> "${delta / 60} 分钟前"
        delta < 86400 -> "${delta / 3600} 小时前"
        else -> "${delta / 86400} 天前"
    }
}

// ============================================================
// 颜色 / 尺寸 —— 由 token 派生，0 自由 dp
// ============================================================

private object AutomationTints {
    val idle = Color(0xFFD18BFE)
    val enabled = Color(0xFF2F6FED)
    val disabled = Color(0xFF7B7B7B)
}

/** 头像 = CardInner * 2 + CardGap = 40dp */
private val AvatarSize: Dp = OneUiSpacing.CardInner * 2 + OneUiSpacing.CardGap

/** 状态指示器 = CardInner * 2 = 32dp */
private val StatusDotSize: Dp = OneUiSpacing.CardInner * 2

/** 状态指示器内圆点 = CardGap = 8dp */
private val StatusDotInner: Dp = OneUiSpacing.CardGap
