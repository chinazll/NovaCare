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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novacare.core.model.AutomationRule
import com.novacare.core.model.TriggerType
import com.novacare.ui.designsystem.NovaCareTheme
import com.novacare.ui.designsystem.NovaTap
import com.novacare.ui.designsystem.NovaToggle
import com.novacare.ui.designsystem.OneUiAppBar
import com.novacare.ui.designsystem.OneUiRadius
import com.novacare.ui.designsystem.OneUiSpacing

/**
 * 自动化页 —— OneUI 9.5 重写。
 *
 * - OneUiAppBar 顶部
 * - 规则列表：每条 = M3 ListItem 风格的卡片，点击进入**规则详情**（新增的
 *   `onOpenRule` 回调，由调用方注入导航逻辑）
 * - Switch 仅作为状态指示器（enabled=false / onCheckedChange=null），
 *   真实开关靠整行 —— 避免"按了没反应"
 * - FAB 添加新规则
 * - 顶部 summary + 通知条仍是同款 Compose，但用 Surface + OneUiRadius 替代
 *   GlassPanel（OneUI 9 不再使用毛玻璃）
 */
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

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                OneUiAppBar(title = "自动化")

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = OneUiSpacing.ScreenEdge),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${rules.count { it.enabled }} / ${rules.size} 已启用",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(OneUiSpacing.CardInner))

                if (notice != null) {
                    NoticeCard(
                        text = notice!!,
                        onDismiss = { viewModel.dismissNotice() },
                    )
                    Spacer(Modifier.height(OneUiSpacing.CardGap))
                }

                if (rules.isEmpty()) {
                    EmptyHero(onCreate = { NovaTap(view); onCreate() })
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(
                            horizontal = OneUiSpacing.ScreenEdge,
                            vertical = OneUiSpacing.SectionTitleGap,
                        ),
                        verticalArrangement = Arrangement.spacedBy(OneUiSpacing.CardGap),
                    ) {
                        items(items = rules, key = { it.id }) { rule ->
                            RuleRow(
                                rule = rule,
                                onToggle = { enabled ->
                                    NovaToggle(view.context, enabled)
                                    viewModel.toggle(rule, enabled)
                                },
                                onClick = { NovaTap(view); onOpenRule(rule.id) },
                                onDelete = { NovaTap(view); viewModel.askDelete(rule) },
                            )
                        }
                        item {
                            Spacer(Modifier.height(OneUiSpacing.EmptyHeight + OneUiSpacing.BlockGap))
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = { NovaTap(view); onCreate() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = OneUiSpacing.ScreenEdge, bottom = OneUiSpacing.ScreenEdge),
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

@Composable
private fun EmptyHero(onCreate: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = OneUiSpacing.ScreenEdge),
    ) {
        Spacer(Modifier.height(OneUiSpacing.BlockGap))
        Text(
            text = "还没有自动化规则",
            style = MaterialTheme.typography.titleMedium,
            color = cs.onSurface,
        )
        Spacer(Modifier.height(OneUiSpacing.CardGap))
        Text(
            text = "新建一条规则，系统会在 6 小时维护窗口内执行，" +
                "不会精确到你设定的分钟。",
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
        )
        Spacer(Modifier.height(OneUiSpacing.BlockGap))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(OneUiSpacing.CardInner * 4 - OneUiSpacing.CardGap)
                .clip(RoundedCornerShape(OneUiRadius.Large))
                .clickable { onCreate() },
            color = cs.primary,
            contentColor = cs.onPrimary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "新建第一条规则",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun NoticeCard(text: String, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OneUiSpacing.ScreenEdge)
            .clip(RoundedCornerShape(OneUiRadius.Medium))
            .background(cs.primary.copy(alpha = 0.08f))
            .clickable { onDismiss() }
            .padding(
                horizontal = OneUiSpacing.CardInner,
                vertical = OneUiSpacing.CardGap,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RuleRow(
    rule: AutomationRule,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val accent = NovaCareTheme.colors.accent
    val dangerColor = NovaCareTheme.colors.riskRisky

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OneUiRadius.Large))
            .clickable { onClick() },
        shape = RoundedCornerShape(OneUiRadius.Large),
        color = cs.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(OneUiSpacing.CardInner)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.name,
                        style = MaterialTheme.typography.bodyLarge,
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
                }
                SwitchStatusOnly(checked = rule.enabled)
            }

            Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = rule.lastRunEpochMs?.let { "上次执行：${formatRelativeTime(it)}" } ?: "尚未执行",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(OneUiRadius.Pill))
                        .clickable { onToggle(!rule.enabled) },
                    color = accent.copy(alpha = 0.12f),
                ) {
                    Text(
                        text = if (rule.enabled) "停用" else "启用",
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                        modifier = Modifier.padding(
                            horizontal = OneUiSpacing.SectionTitleGap,
                            vertical = OneUiSpacing.CardGap / 2,
                        ),
                    )
                }
                Spacer(Modifier.size(OneUiSpacing.CardGap))
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(OneUiRadius.Pill))
                        .clickable { onDelete() },
                    color = dangerColor.copy(alpha = 0.12f),
                ) {
                    Text(
                        text = "删除",
                        style = MaterialTheme.typography.labelMedium,
                        color = dangerColor,
                        modifier = Modifier.padding(
                            horizontal = OneUiSpacing.SectionTitleGap,
                            vertical = OneUiSpacing.CardGap / 2,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * Switch 仅作为状态指示器（绿=启用 / 灰=停用）。
 * 整行的"启用/停用" chip 是真实操作 —— 这样视觉和操作分离：
 * 用户看到一眼就知道这条规则跑不跑，但不会去戳 Switch 没反应。
 */
@Composable
private fun SwitchStatusOnly(checked: Boolean) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(OneUiSpacing.CardInner * 2)
            .clip(CircleShape)
            .background(
                if (checked) cs.primary
                else cs.onSurface.copy(alpha = 0.08f),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            // 用一个圆点表示"启用"
            Box(
                modifier = Modifier
                    .size(OneUiSpacing.CardGap)
                    .clip(CircleShape)
                    .background(cs.onPrimary),
            )
        } else {
            // 停用态留空
        }
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
