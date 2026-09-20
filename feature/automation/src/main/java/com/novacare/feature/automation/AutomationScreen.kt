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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novacare.core.model.AutomationRule
import com.novacare.core.model.TriggerType
import com.novacare.ui.designsystem.NovaTap
import com.novacare.ui.designsystem.NovaToggle

/**
 * 自动化页 —— OneUI 9.5 真实设计语言。
 */
@Composable
fun AutomationScreen(
    modifier: Modifier = Modifier,
    viewModel: AutomationViewModel = hiltViewModel(),
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val view = LocalView.current

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Spacer(Modifier.height(56.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "自动化",
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${rules.count { it.enabled }} / ${rules.size} 已启用",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (notice != null) {
                    NoticeCard(
                        text = notice!!,
                        onDismiss = { viewModel.dismissNotice() },
                    )
                    Spacer(Modifier.height(8.dp))
                }

                if (rules.isEmpty()) {
                    EmptyHero(onCreate = { NovaTap(view); viewModel.startCreate() })
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(items = rules, key = { it.id }) { rule ->
                            RuleRow(
                                rule = rule,
                                onToggle = { enabled ->
                                    NovaToggle(view.context, enabled)
                                    viewModel.toggle(rule, enabled)
                                },
                                onEdit = { NovaTap(view); viewModel.startEdit(rule) },
                            )
                        }
                        item {
                            Spacer(Modifier.height(120.dp))
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 24.dp)
                    .size(56.dp)
                    .clip(CircleShape)
                    .clickable { NovaTap(view); viewModel.startCreate() },
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "新建规则",
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun NoticeCard(text: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            .clickable { onDismiss() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun EmptyHero(onCreate: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Text(
            text = "还没有自动化规则",
            style = MaterialTheme.typography.titleMedium,
            color = cs.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "新建一条规则，系统会在 6 小时维护窗口内执行，" +
                "不会精确到你设定的分钟。",
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(20.dp)),
            color = cs.primary,
            contentColor = cs.onPrimary,
            onClick = onCreate,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "新建第一条规则",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W600),
                )
            }
        }
    }
}

@Composable
private fun RuleRow(
    rule: AutomationRule,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { expanded = !expanded },
        color = cs.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = cs.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = cs.onSurfaceVariant,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = rule.describe(),
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = cs.onPrimary,
                        checkedTrackColor = cs.primary,
                    ),
                )
            }

            if (expanded) {
                Spacer(Modifier.height(12.dp))
                Column {
                    rule.actions.forEach { action ->
                        Row(
                            modifier = Modifier.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(cs.primary.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = cs.primary,
                                    modifier = Modifier.size(10.dp),
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = AutomationViewModel.actionLabel(action.type),
                                style = MaterialTheme.typography.bodyMedium,
                                color = cs.onSurface,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = rule.lastRunEpochMs?.let { "上次执行：${formatRelativeTime(it)}" } ?: "尚未执行",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onEdit() }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "编辑",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W600),
                            color = cs.primary,
                        )
                    }
                }
            }
        }
    }
}

private fun AutomationRule.describe(): String {
    return when (trigger.type) {
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

// (end of file)
