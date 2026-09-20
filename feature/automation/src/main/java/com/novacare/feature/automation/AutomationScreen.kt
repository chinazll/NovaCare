package com.novacare.feature.automation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TimerOff
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.novacare.core.model.ActionType
import com.novacare.core.model.AutomationRule
import com.novacare.core.model.TriggerType
import com.novacare.ui.designsystem.AuroraBackground
import com.novacare.ui.designsystem.EmptyState
import com.novacare.ui.designsystem.EmptyTone
import com.novacare.ui.designsystem.InlineNotice
import com.novacare.ui.designsystem.NovaCard
import com.novacare.ui.designsystem.NovaCareTheme
import com.novacare.ui.designsystem.PrimaryAction
import com.novacare.ui.designsystem.SecondaryAction
import com.novacare.ui.designsystem.SectionHeader
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 自动化（L2）—— 规则列表 + 图形化规则编辑器
 *
 * 【为什么强调"不保证准点"】
 * 规则由 WorkManager 周期任务驱动，系统只承诺"最终会被跑到"，
 * 不承诺精确时刻。界面必须把这句话写在明面上 ——
 * 让用户以为设了 03:00 就一定 03:00 执行，是最容易积累不信任的做法。
 *
 * 布局节奏：
 *   1. 顶栏 + 调度限制说明（InlineNotice，常驻但克制）
 *   2. 规则卡列表 —— 每条显示：名称 / 触发 / 动作 / 开关 / 上次执行
 *   3. 每张卡右下角：编辑、删除
 *   4. 主 CTA「创建第一条规则」/「新建规则」
 *   5. 自然语言快速建规则（能解析就说清了，不能就直说）
 *
 * 编辑表单以底部浮层的形式在同一个文件内实现（[RuleEditorSheet]），
 * 触发类型 → 时间/阈值 → 动作，全部是可见可点的控件，没有下拉里套下拉。
 */
@Composable
fun AutomationScreen(
    modifier: Modifier = Modifier,
    viewModel: AutomationViewModel = hiltViewModel(),
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val editing by viewModel.editing.collectAsStateWithLifecycle()
    val pendingDelete by viewModel.pendingDelete.collectAsStateWithLifecycle()
    val naturalInput by viewModel.naturalInput.collectAsStateWithLifecycle()

    AuroraBackground {
        Box(modifier = modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 20.dp,
                    bottom = 40.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "header") {
                    Column(modifier = Modifier.statusBarsPadding()) {
                        Text(
                            text = "自动化",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = if (rules.isEmpty()) {
                                "还没有规则"
                            } else {
                                "${rules.size} 条规则 · 已启用 ${rules.count { it.enabled }} 条"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // 调度限制：常驻说明，因为它直接影响用户对"准时"的预期
                item(key = "scheduler-note") {
                    InlineNotice(
                        text = "规则由系统的后台调度执行：只保证最终会跑，不保证精确到分钟。" +
                            "若手机长期省电模式或刚重启，执行时间会推迟。",
                        tone = EmptyTone.Neutral,
                        icon = Icons.Outlined.Info,
                    )
                }

                notice?.let { message ->
                    item(key = "notice") {
                        InlineNotice(
                            text = message,
                            tone = EmptyTone.Success,
                            actionText = "知道了",
                            onAction = viewModel::dismissNotice,
                            icon = Icons.Outlined.Check,
                        )
                    }
                }

                if (rules.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            title = "还没有自动化规则",
                            message = "规则可以让你不用记着清理：比如每周日凌晨自动清理垃圾，" +
                                "或者电量低于 20% 时提醒你。执行前都会先比对一次真实状态，不会盲跑。",
                            icon = Icons.Outlined.Bolt,
                            actionText = "创建第一条规则",
                            onAction = viewModel::startCreate,
                        )
                    }
                } else {
                    item(key = "rules-header") { SectionHeader(title = "规则") }
                    items(rules, key = { it.id }) { rule ->
                        RuleCard(
                            rule = rule,
                            onToggle = { enabled -> viewModel.toggle(rule, enabled) },
                            onEdit = { viewModel.startEdit(rule) },
                            onDelete = { viewModel.askDelete(rule) },
                        )
                    }
                }

                item(key = "quick-create") {
                    QuickCreateCard(
                        value = naturalInput,
                        onValueChange = viewModel::setNaturalInput,
                        onSubmit = viewModel::createFromText,
                    )
                }

                item(key = "action") {
                    PrimaryAction(
                        text = if (rules.isEmpty()) "创建第一条规则" else "新建规则",
                        subtitle = "选触发条件 → 选要做什么，两步完成",
                        onClick = viewModel::startCreate,
                    )
                }

                item(key = "footer") {
                    Text(
                        text = "所有规则都在本机执行，不会上传任何设备信息",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 编辑表单：底部浮层
            editing?.let { draft ->
                RuleEditorSheet(
                    draft = draft,
                    onUpdate = viewModel::updateDraft,
                    onSave = viewModel::saveDraft,
                    onDismiss = viewModel::cancelEdit,
                )
            }

            // 删除确认：二次确认，因为规则是无人值守执行的
            pendingDelete?.let { rule ->
                DeleteConfirmSheet(
                    rule = rule,
                    onConfirm = viewModel::confirmDelete,
                    onDismiss = viewModel::cancelDelete,
                )
            }
        }
    }
}

// ------------------------------------------------------------
// 规则卡片
// ------------------------------------------------------------

@Composable
private fun RuleCard(
    rule: AutomationRule,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = NovaCareTheme.colors
    val triggerIcon = triggerIcon(rule.trigger.type)

    NovaCard {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        if (rule.enabled) {
                            colors.accent.copy(alpha = 0.12f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = triggerIcon,
                    contentDescription = null,
                    tint = if (rule.enabled) {
                        colors.accent
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(19.dp),
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = rule.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (rule.enabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (rule.createdByAi) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = colors.accent.copy(alpha = 0.14f),
                        ) {
                            Text(
                                text = "自然语言",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.accent,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                // 触发 → 动作，逐行摊开，避免挤成一句难读的长串
                MetaLine(label = "触发", value = triggerText(rule))
                MetaLine(label = "执行", value = actionText(rule))
                MetaLine(
                    label = "上次执行",
                    value = rule.lastRunEpochMs?.let { formatLastRun(it) } ?: "尚未执行过",
                    valueColor = if (rule.lastRunEpochMs == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        null
                    },
                )
            }

            Spacer(Modifier.width(8.dp))

            Switch(
                checked = rule.enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.semantics {
                    contentDescription = if (rule.enabled) {
                        "停用规则 ${rule.name}"
                    } else {
                        "启用规则 ${rule.name}"
                    }
                },
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryAction(text = "编辑", onClick = onEdit)
            Row(
                modifier = Modifier
                    .height(48.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onDelete,
                    )
                    .border(
                        width = 1.dp,
                        color = colors.riskRisky.copy(alpha = 0.4f),
                        shape = MaterialTheme.shapes.extraLarge,
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = colors.riskRisky,
                    modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "删除",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.riskRisky,
                )
            }
        }
    }
}

@Composable
private fun MetaLine(label: String, value: String, valueColor: Color? = null) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(62.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 触发条件的人类可读描述（比 describe() 更细，单独展示触发部分） */
private fun triggerText(rule: AutomationRule): String = when (rule.trigger.type) {
    TriggerType.SCHEDULED -> {
        val day = rule.trigger.dayOfWeek?.let { "每" + AutomationViewModel.DAY_LABELS[it - 1] } ?: "每天"
        "%s %02d:00".format(day, rule.trigger.hourOfDay ?: 3)
    }

    TriggerType.BATTERY_BELOW -> "电量低于 ${rule.trigger.thresholdPercent ?: 20}%"
    TriggerType.STORAGE_ABOVE -> "存储占用高于 ${rule.trigger.thresholdPercent ?: 85}%"
    TriggerType.DEVICE_IDLE -> "设备空闲且充电中"
    TriggerType.BOOT -> "设备开机后"
}

private fun actionText(rule: AutomationRule): String =
    rule.actions.map { AutomationViewModel.actionLabel(it.type) }.joinToString("、")

private fun triggerIcon(type: TriggerType): ImageVector = when (type) {
    TriggerType.SCHEDULED -> Icons.Outlined.Schedule
    TriggerType.BATTERY_BELOW -> Icons.Outlined.BatteryAlert
    TriggerType.STORAGE_ABOVE -> Icons.Outlined.Storage
    TriggerType.DEVICE_IDLE -> Icons.Outlined.TimerOff
    TriggerType.BOOT -> Icons.Outlined.PowerSettingsNew
}

private fun formatLastRun(epochMs: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(epochMs))

// ------------------------------------------------------------
// 自然语言快速创建
// ------------------------------------------------------------

@Composable
private fun QuickCreateCard(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val colors = NovaCareTheme.colors
    val canSubmit = value.isNotBlank()

    Column {
        SectionHeader(title = "用一句话创建")
        NovaCard {
            Text(
                text = "支持的说法：「每天 22 点清理垃圾」「每周日 3 点清理缓存」" +
                    "「每天 8 点闪存整理」",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(1.dp, colors.hairline, MaterialTheme.shapes.extraLarge)
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = "例如：每周日 3 点清理垃圾",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        textStyle = LocalTextStyle.current
                            .merge(MaterialTheme.typography.bodyMedium)
                            .copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(colors.accent),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.width(10.dp))

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (canSubmit) colors.accent else MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                        .then(
                            if (canSubmit) {
                                Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    role = Role.Button,
                                    onClick = onSubmit,
                                )
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "按这句话创建规则",
                        tint = if (canSubmit) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------
// 规则编辑浮层
// ------------------------------------------------------------

/**
 * 底部浮层表单。
 *
 * 不用 Material 的 ModalBottomSheet：它在这个项目里会和外层 AuroraBackground
 * 的分层打架，且拖拽手势与内部 LazyColumn 冲突。这里用一个可控的
 * 底部对齐 Surface，行为更可预期。
 *
 * 表单字段随触发类型**动态出现** —— 选"按时间"才显示时间，
 * 选"电量低于"才显示阈值，不给用户看无关的空白输入框。
 */
@Composable
private fun RuleEditorSheet(
    draft: AutomationViewModel.RuleDraft,
    onUpdate: ((AutomationViewModel.RuleDraft) -> AutomationViewModel.RuleDraft) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = NovaCareTheme.colors
    var pendingDismiss by remember { mutableStateOf(false) }

    // 点击遮罩：先给一次"再点一次即放弃"的机会，避免误触丢失已填内容
    LaunchedEffect(pendingDismiss) {
        if (pendingDismiss) {
            delay(2_000)
            pendingDismiss = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (pendingDismiss) onDismiss() else pendingDismiss = true
                    },
                )
        )

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .imePadding(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, colors.hairline),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (draft.id == null) "新建规则" else "编辑规则",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (pendingDismiss) "再点一次放弃" else "取消",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (pendingDismiss) {
                            colors.riskCaution
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button,
                                onClick = onDismiss,
                            )
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                }

                Spacer(Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    contentPadding = PaddingValues(bottom = 4.dp),
                ) {
                    // ---- 名称 ----
                    item(key = "name") {
                        FieldLabel("规则名称")
                        Spacer(Modifier.height(8.dp))
                        EditorTextField(
                            value = draft.name,
                            onValueChange = { v -> onUpdate { it.copy(name = v) } },
                            placeholder = draft.defaultName(),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "留空则使用默认名称「${draft.defaultName()}」",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // ---- 触发类型 ----
                    item(key = "trigger-kind") {
                        FieldLabel("触发条件")
                        Spacer(Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AutomationViewModel.TriggerKind.entries.forEach { kind ->
                                TriggerKindRow(
                                    kind = kind,
                                    selected = draft.kind == kind,
                                    onClick = { onUpdate { it.copy(kind = kind) } },
                                )
                            }
                        }
                    }

                    // ---- 触发参数（随类型出现）----
                    if (draft.kind == AutomationViewModel.TriggerKind.SCHEDULED) {
                        item(key = "schedule") {
                            FieldLabel("执行时间")
                            Spacer(Modifier.height(8.dp))
                            HourPicker(
                                hour = draft.hourOfDay,
                                onPick = { h -> onUpdate { it.copy(hourOfDay = h) } },
                            )
                            Spacer(Modifier.height(14.dp))
                            FieldLabel("重复周期")
                            Spacer(Modifier.height(8.dp))
                            DayPicker(
                                selected = draft.dayOfWeek,
                                onPick = { d -> onUpdate { it.copy(dayOfWeek = d) } },
                            )
                        }
                    }

                    if (draft.kind == AutomationViewModel.TriggerKind.BATTERY_BELOW ||
                        draft.kind == AutomationViewModel.TriggerKind.STORAGE_ABOVE
                    ) {
                        item(key = "threshold") {
                            FieldLabel("触发阈值（%）")
                            Spacer(Modifier.height(8.dp))
                            ThresholdInput(
                                value = draft.thresholdPercent,
                                onValueChange = { p -> onUpdate { it.copy(thresholdPercent = p) } },
                            )
                        }
                    }

                    // ---- 动作 ----
                    item(key = "actions") {
                        FieldLabel("要执行的动作")
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "可多选。动作会按下面的顺序逐个执行。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionType.entries.forEach { type ->
                                ActionRow(
                                    type = type,
                                    selected = type in draft.actions,
                                    onToggle = {
                                        onUpdate { d ->
                                            val next = d.actions.toMutableSet()
                                            if (!next.remove(type)) next.add(type)
                                            d.copy(actions = next)
                                        }
                                    },
                                )
                            }
                        }
                    }

                    // ---- 安全底线 ----
                    item(key = "safety") {
                        SafetyToggle(
                            checked = draft.onlySafeItems,
                            onCheckedChange = { v -> onUpdate { it.copy(onlySafeItems = v) } },
                        )
                    }

                    item(key = "enable") {
                        SimpleToggle(
                            title = "创建后立即启用",
                            subtitle = "关闭则只保存不执行，之后可在列表中开启",
                            checked = draft.enabled,
                            onCheckedChange = { v -> onUpdate { it.copy(enabled = v) } },
                        )
                    }

                    item(key = "save") {
                        Spacer(Modifier.height(2.dp))
                        PrimaryAction(
                            text = if (draft.id == null) "创建规则" else "保存修改",
                            subtitle = if (draft.canSave) {
                                "触发：${triggerPreview(draft)}"
                            } else {
                                "请至少选择一项要执行的动作"
                            },
                            enabled = draft.canSave,
                            onClick = onSave,
                        )
                    }
                }
            }
        }
    }
}

private fun triggerPreview(draft: AutomationViewModel.RuleDraft): String =
    when (draft.kind) {
        AutomationViewModel.TriggerKind.SCHEDULED -> {
            val day = draft.dayOfWeek?.let {
                "每" + AutomationViewModel.DAY_LABELS[it - 1]
            } ?: "每天"
            "%s %02d:00".format(day, draft.hourOfDay)
        }

        AutomationViewModel.TriggerKind.BATTERY_BELOW -> "电量低于 ${draft.thresholdPercent}%"
        AutomationViewModel.TriggerKind.STORAGE_ABOVE -> "存储高于 ${draft.thresholdPercent}%"
        AutomationViewModel.TriggerKind.DEVICE_IDLE -> "设备空闲充电时"
        AutomationViewModel.TriggerKind.BOOT -> "开机后"
    }

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun EditorTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    val colors = NovaCareTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, colors.hairline, MaterialTheme.shapes.medium)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = LocalTextStyle.current
                .merge(MaterialTheme.typography.bodyMedium)
                .copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(colors.accent),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TriggerKindRow(
    kind: AutomationViewModel.TriggerKind,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = NovaCareTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (selected) {
                    colors.accent.copy(alpha = 0.10f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            )
            .border(
                width = 1.dp,
                color = if (selected) colors.accent.copy(alpha = 0.5f) else colors.hairline,
                shape = MaterialTheme.shapes.medium,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectionDot(selected = selected)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = kind.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = kind.explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActionRow(
    type: ActionType,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    val colors = NovaCareTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (selected) {
                    colors.accent.copy(alpha = 0.10f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            )
            .border(
                width = 1.dp,
                color = if (selected) colors.accent.copy(alpha = 0.5f) else colors.hairline,
                shape = MaterialTheme.shapes.medium,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Checkbox,
                onClick = onToggle,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectionDot(selected = selected)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = AutomationViewModel.actionLabel(type),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = AutomationViewModel.actionDetail(type),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SelectionDot(selected: Boolean) {
    val colors = NovaCareTheme.colors
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(if (selected) colors.accent else Color.Transparent)
            .border(
                width = 1.5.dp,
                color = if (selected) colors.accent else colors.hairline,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

/** 小时选择：横向滚动 chip，比 NumberPicker 更符合触摸习惯 */
@Composable
private fun HourPicker(hour: Int, onPick: (Int) -> Unit) {
    val colors = NovaCareTheme.colors
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items((0..23).toList(), key = { it }) { h ->
            val selected = h == hour
            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 44.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(
                        if (selected) {
                            colors.accent
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = if (selected) colors.accent else colors.hairline,
                        shape = MaterialTheme.shapes.medium,
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.RadioButton,
                        onClick = { onPick(h) },
                    )
                    .semantics { contentDescription = "%02d 时".format(h) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "%02d".format(h),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

/** 重复周期：每天 / 周一..周日 */
@Composable
private fun DayPicker(selected: Int?, onPick: (Int?) -> Unit) {
    val colors = NovaCareTheme.colors
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "daily") {
            val isSelected = selected == null
            Box(
                modifier = Modifier
                    .height(44.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(
                        if (isSelected) colors.accent else MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSelected) colors.accent else colors.hairline,
                        shape = MaterialTheme.shapes.medium,
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.RadioButton,
                        onClick = { onPick(null) },
                    )
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "每天",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
        items((1..7).toList(), key = { it }) { day ->
            val isSelected = selected == day
            Box(
                modifier = Modifier
                    .height(44.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(
                        if (isSelected) colors.accent else MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSelected) colors.accent else colors.hairline,
                        shape = MaterialTheme.shapes.medium,
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.RadioButton,
                        onClick = { onPick(day) },
                    )
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = AutomationViewModel.DAY_LABELS[day - 1],
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

/** 阈值输入 + 常用值快捷 chip；直接输入数字或点 chip 都可以 */
@Composable
private fun ThresholdInput(value: Int, onValueChange: (Int) -> Unit) {
    val colors = NovaCareTheme.colors
    var text by remember { mutableStateOf(value.toString()) }

    Column {
        Box(
            modifier = Modifier
                .width(120.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, colors.hairline, MaterialTheme.shapes.medium)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            BasicTextField(
                value = text,
                onValueChange = { raw ->
                    val digits = raw.filter { it.isDigit() }.take(3)
                    text = digits
                    digits.toIntOrNull()?.let { if (it in 1..100) onValueChange(it) }
                },
                textStyle = LocalTextStyle.current
                    .merge(MaterialTheme.typography.bodyMedium)
                    .copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(colors.accent),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(10.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf(10, 20, 30, 50, 80, 85, 90), key = { it }) { preset ->
                val isSelected = preset == value
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(
                            if (isSelected) {
                                colors.accent.copy(alpha = 0.16f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            }
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) colors.accent.copy(alpha = 0.5f) else colors.hairline,
                            shape = MaterialTheme.shapes.medium,
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClick = {
                                text = preset.toString()
                                onValueChange(preset)
                            },
                        )
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "$preset%",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) colors.accent else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/**
 * 安全底线开关。
 *
 * 这是整个自动化功能里最重要的一条说明：关闭它意味着规则可能清理掉
 * 需要确认的项目。因此文案必须直白写出后果，而不是一句"高级选项"。
 */
@Composable
private fun SafetyToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = NovaCareTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (checked) {
                    colors.healthGood.copy(alpha = 0.08f)
                } else {
                    colors.riskRisky.copy(alpha = 0.08f)
                }
            )
            .border(
                width = 1.dp,
                color = if (checked) {
                    colors.healthGood.copy(alpha = 0.3f)
                } else {
                    colors.riskRisky.copy(alpha = 0.3f)
                },
                shape = MaterialTheme.shapes.medium,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (checked) "只清理确定安全的项目" else "允许清理需确认的项目",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (checked) {
                    "推荐。规则只会动内核判定为安全的垃圾文件，不碰任何可能丢数据的内容。"
                } else {
                    "不建议。规则可能在无人看管时执行你未逐一确认过的清理，存在误删风险。" +
                        "只有你清楚自己在做什么时再关闭。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (checked) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    colors.riskRisky
                },
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SimpleToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// ------------------------------------------------------------
// 删除确认
// ------------------------------------------------------------

@Composable
private fun DeleteConfirmSheet(
    rule: AutomationRule,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = NovaCareTheme.colors
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
        )

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, colors.hairline),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(colors.riskRisky.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = null,
                            tint = colors.riskRisky,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "删除这条规则？",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = rule.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = buildString {
                        append("触发：")
                        append(triggerText(rule))
                        append("\n执行：")
                        append(actionText(rule))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = "删除后无法恢复。已经执行过的记录也会一并消失。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.riskCaution,
                )

                Spacer(Modifier.height(18.dp))

                PrimaryAction(text = "确认删除", onClick = onConfirm)

                Spacer(Modifier.height(10.dp))

                SecondaryAction(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
