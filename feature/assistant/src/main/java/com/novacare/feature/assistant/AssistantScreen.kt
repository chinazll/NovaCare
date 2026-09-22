package com.novacare.feature.assistant

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novacare.core.common.formatBytes
import com.novacare.ui.designsystem.NovaCareTheme
import com.novacare.ui.designsystem.NovaSuccess
import com.novacare.ui.designsystem.NovaTap
import com.novacare.ui.designsystem.OneUiAppBar
import com.novacare.ui.designsystem.OneUiListSection
import com.novacare.ui.designsystem.OneUiRadius
import com.novacare.ui.designsystem.OneUiSpacing

// =========================================================================
// AssistantScreen — OneUI 9 重写版
//
// 模式：
//   - 顶部 OneUiAppBar
//   - Mode picker（云端 / 本地）= OneUiListSection（一个 squircle 包两个 mode segment）
//   - 下方：scrollable message list（user right / assistant left）
//   - 底部：composer = 固定 squircle（pill shape input + send button）
// =========================================================================

@Composable
fun AssistantScreen(
    rootPath: String,
    modifier: Modifier = Modifier,
    viewModel: AssistantViewModel = hiltViewModel(),
) {
    val bubbles by viewModel.bubbles.collectAsStateWithLifecycle()
    val capability by viewModel.capability.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val view = LocalView.current

    LaunchedEffect(bubbles.size) {
        if (bubbles.isNotEmpty()) {
            listState.animateScrollToItem(bubbles.size - 1)
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            OneUiAppBar(title = "AI 助手")

            ModePicker(
                tierLabel = capability.tierLabel,
                cloudEnabled = capability.cloudEnabled,
                onPickCloud = { NovaTap(view); viewModel.refreshCapability() },
            )

            Spacer(Modifier.height(OneUiSpacing.CardInner))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(
                    horizontal = OneUiSpacing.BlockGap,
                    vertical = OneUiSpacing.CardGap,
                ),
                verticalArrangement = Arrangement.spacedBy(OneUiSpacing.CardGap),
            ) {
                if (bubbles.isEmpty()) {
                    item {
                        OneUiListSection {
                            Column(modifier = Modifier.padding(OneUiSpacing.CardInner)) {
                                Text(
                                    text = "我能帮你做什么？",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
                                Text(
                                    text = if (capability.cloudEnabled)
                                        "云端 LLM 已连接。我会读你的设备状态来回答具体问题，" +
                                            "并把可执行的动作变成卡片。"
                                    else
                                        "本地规则模式（未配置云端 key）。我只能识别固定的几种问法，" +
                                            "去 设置 → AI 开启云端获得自然对话。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    item {
                        QuickPrompts(
                            onSelect = { prompt ->
                                NovaTap(view)
                                viewModel.submit(prompt, rootPath)
                            },
                        )
                    }
                }
                items(items = bubbles, key = { it.id }) { bubble ->
                    when (bubble) {
                        is AssistantViewModel.Bubble.User -> UserBubble(bubble.text)
                        is AssistantViewModel.Bubble.Assistant -> AssistantBubble(
                            bubble = bubble,
                            onConfirm = { viewModel.confirmAction(bubble.id, rootPath) },
                            onCancel = { viewModel.cancelAction(bubble.id) },
                        )
                    }
                }
            }

            Composer(
                onSend = { text ->
                    NovaSuccess(view.context)
                    viewModel.submit(text, rootPath)
                },
            )
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

// ============================================================
// 顶部：Mode picker —— 一个 squircle 包两个 mode segment
// ============================================================

@Composable
private fun ModePicker(
    tierLabel: String,
    cloudEnabled: Boolean,
    onPickCloud: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OneUiSpacing.BlockGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OneUiListSection(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(OneUiSpacing.CardInner),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ModeSegment(
                    icon = Icons.Outlined.Cloud,
                    label = "云端",
                    sublabel = if (cloudEnabled) "已配 key · 流式" else "未配 key · 灰",
                    selected = cloudEnabled,
                    enabled = cloudEnabled,
                    onClick = onPickCloud,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(OneUiSpacing.CardGap))
                ModeSegment(
                    icon = Icons.Outlined.SmartToy,
                    label = "本地",
                    sublabel = "离线 · 确定性",
                    selected = !cloudEnabled,
                    enabled = true,
                    onClick = { /* 本地模式始终可用，无需切换 */ },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ModeSegment(
    icon: ImageVector,
    label: String,
    sublabel: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val accent = NovaCareTheme.colors.accent
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(OneUiRadius.Medium))
            .clickable(enabled = enabled) { onClick() },
        color = if (selected) accent.copy(alpha = 0.14f)
        else cs.onSurface.copy(alpha = 0.04f),
    ) {
        Column(
            modifier = Modifier.padding(OneUiSpacing.SectionTitleGap),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) accent else cs.onSurfaceVariant,
                    modifier = Modifier.size(BubbleIconSize),
                )
                Spacer(Modifier.width(OneUiSpacing.SectionTitleGap))
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) accent else cs.onSurface,
                )
            }
            Spacer(Modifier.height(OneUiSpacing.CardGap / 2))
            Text(
                text = sublabel,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

// ============================================================
// 对话气泡
// ============================================================

@Composable
private fun UserBubble(text: String) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            color = cs.primary,
            contentColor = cs.onPrimary,
            modifier = Modifier
                .widthIn(max = BubbleMaxWidth)
                .clip(RoundedCornerShape(BubbleRadiusUser)),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(
                    horizontal = OneUiSpacing.CardInner,
                    vertical = OneUiSpacing.SectionTitleGap,
                ),
            )
        }
    }
}

@Composable
private fun AssistantBubble(
    bubble: AssistantViewModel.Bubble.Assistant,
    onConfirm: (Long) -> Unit,
    onCancel: (Long) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            color = cs.surfaceVariant,
            contentColor = cs.onSurface,
            modifier = Modifier
                .widthIn(max = BubbleMaxWidth)
                .clip(RoundedCornerShape(BubbleRadiusAssistant)),
        ) {
            Column(modifier = Modifier.padding(OneUiSpacing.CardInner)) {
                if (bubble.streamed) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(StreamingDotSize)
                                .clip(CircleShape)
                                .background(colors.healthGood),
                        )
                        Spacer(Modifier.width(OneUiSpacing.SectionTitleGap))
                        Text(
                            text = "云端对话",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(OneUiSpacing.CardGap))
                }
                Text(
                    text = bubble.text.ifEmpty { "…" },
                    style = MaterialTheme.typography.bodyMedium,
                )
                val card = bubble.card
                val actionStatus = bubble.actionStatus
                if (card != null && actionStatus != null) {
                    Spacer(Modifier.height(OneUiSpacing.CardGap))
                    ActionCard(
                        card = card,
                        status = actionStatus,
                        onConfirm = { onConfirm(bubble.id) },
                        onCancel = { onCancel(bubble.id) },
                    )
                }
                if (!bubble.understood && !bubble.streamed) {
                    Spacer(Modifier.height(OneUiSpacing.CardGap))
                    Text(
                        text = "本地规则 · 没完全听懂，可以换个说法",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionCard(
    card: AssistantViewModel.ReplyCard,
    status: AssistantViewModel.ActionStatus,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors
    val view = LocalView.current

    val (title, impact, previews) = when (card) {
        is AssistantViewModel.ReplyCard.Plan -> Triple(
            "建议清理 ${card.advices.size} 项",
            "可释放 ${card.totalBytes.formatBytes()}",
            card.advices.take(3).map { it.targetLabel },
        )
        is AssistantViewModel.ReplyCard.Freeze -> Triple(
            "建议冻结 ${card.candidates.size} 个应用",
            "冻结后停止后台活动，需要时可手动恢复",
            card.candidates.take(3).map { it.app.label },
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OneUiRadius.Medium)),
        color = cs.onSurface.copy(alpha = 0.06f),
    ) {
        Column(modifier = Modifier.padding(OneUiSpacing.CardInner)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.W600),
                color = cs.onSurface,
            )
            Spacer(Modifier.height(OneUiSpacing.CardGap / 2))
            Text(
                text = impact,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
            if (previews.isNotEmpty()) {
                Spacer(Modifier.height(OneUiSpacing.CardGap))
                Text(
                    text = previews.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(OneUiSpacing.CardInner))

            when (status) {
                is AssistantViewModel.ActionStatus.Pending -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(OneUiSpacing.CardGap)) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(ActionButtonHeight)
                                .clip(RoundedCornerShape(OneUiRadius.Pill))
                                .clickable {
                                    NovaTap(view)
                                    onConfirm()
                                },
                            color = cs.primary,
                            contentColor = cs.onPrimary,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "确认执行",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(ActionButtonHeight)
                                .clip(RoundedCornerShape(OneUiRadius.Pill))
                                .clickable {
                                    NovaTap(view)
                                    onCancel()
                                },
                            color = cs.onSurface.copy(alpha = 0.08f),
                            contentColor = cs.onSurfaceVariant,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "取消",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }

                AssistantViewModel.ActionStatus.Running -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(OneUiSpacing.CardInner * 2),
                            strokeWidth = OneUiSpacing.CardGap / 2,
                            color = cs.primary,
                        )
                        Spacer(Modifier.width(OneUiSpacing.CardGap))
                        Text(
                            text = "正在执行…",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }

                is AssistantViewModel.ActionStatus.Done -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircleOutline,
                            contentDescription = null,
                            tint = colors.healthGood,
                            modifier = Modifier.size(OneUiSpacing.CardInner * 2),
                        )
                        Spacer(Modifier.width(OneUiSpacing.CardGap))
                        Text(
                            text = status.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurface,
                        )
                    }
                }

                is AssistantViewModel.ActionStatus.Failed -> {
                    Text(
                        text = status.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.riskCaution,
                    )
                }

                AssistantViewModel.ActionStatus.Cancelled -> {
                    Text(
                        text = "已取消",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ============================================================
// Quick prompts —— OneUiListSection 包一组快捷 prompt
// ============================================================

@Composable
private fun QuickPrompts(onSelect: (String) -> Unit) {
    val prompts = listOf(
        "清理微信的缓存",
        "冻结一个月没用的应用",
        "我手机里最大的几个文件是什么",
        "存储空间为什么只剩这么少",
    )
    OneUiListSection {
        prompts.forEachIndexed { index, prompt ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(prompt) }
                    .padding(horizontal = OneUiSpacing.CardInner, vertical = OneUiSpacing.ListRowVertical),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = prompt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
            if (index < prompts.lastIndex) {
                Spacer(Modifier.height(OneUiSpacing.ListRowVertical))
                Box(
                    modifier = Modifier
                        .padding(start = OneUiSpacing.CardInner)
                        .fillMaxWidth()
                        .height(OneUiSpacing.CardGap / 2)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
                )
            }
        }
    }
}

// ============================================================
// Composer —— 固定底部 squircle（pill shape 输入 + send 按钮）
// ============================================================

@Composable
private fun Composer(onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OneUiSpacing.BlockGap, vertical = OneUiSpacing.CardGap),
        color = cs.surfaceVariant,
        shape = RoundedCornerShape(OneUiRadius.Pill),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OneUiSpacing.CardInner, vertical = OneUiSpacing.CardGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = text,
                onValueChange = { text = it },
                placeholder = {
                    Text(
                        text = "问点啥…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                    )
                },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = cs.surfaceVariant,
                    unfocusedContainerColor = cs.surfaceVariant,
                    focusedIndicatorColor = cs.surfaceVariant,
                    unfocusedIndicatorColor = cs.surfaceVariant,
                ),
                textStyle = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
            )
            Spacer(Modifier.width(OneUiSpacing.CardGap / 2))
            Surface(
                modifier = Modifier
                    .size(ComposerSendSize)
                    .clip(CircleShape)
                    .clickable(enabled = text.isNotBlank()) {
                        onSend(text.trim())
                        text = ""
                    },
                color = if (text.isNotBlank()) cs.primary else cs.onSurface.copy(alpha = 0.12f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Send,
                        contentDescription = "发送",
                        tint = if (text.isNotBlank()) cs.onPrimary else cs.onSurface.copy(alpha = 0.38f),
                        modifier = Modifier.size(OneUiSpacing.BlockGap),
                    )
                }
            }
        }
    }
}

// ============================================================
// 衍生尺寸 —— 由 token 派生，0 自由 dp
// ============================================================

/** 用户气泡右上角小圆角 = Medium (14dp) */
private val BubbleRadiusUser: Dp = OneUiRadius.Medium

/** AI 气泡左上角小圆角 = Medium (14dp) */
private val BubbleRadiusAssistant: Dp = OneUiRadius.Medium

/** 对话气泡最大宽 = CardInner*17 + SectionTitleGap*2 ≈ 280dp */
private val BubbleMaxWidth: Dp = OneUiSpacing.CardInner * 17 + OneUiSpacing.SectionTitleGap * 2

/** 气泡图标尺寸 = BlockGap - SectionTitleGap (14dp) */
private val BubbleIconSize: Dp = OneUiSpacing.BlockGap - OneUiSpacing.SectionTitleGap

/** 流式小圆点 = SectionTitleGap - CardGap/2 (6dp) */
private val StreamingDotSize: Dp = OneUiSpacing.SectionTitleGap - OneUiSpacing.CardGap / 2

/** ActionCard 按钮高度 = CardInner * 2 + CardGap = 40dp */
private val ActionButtonHeight: Dp = OneUiSpacing.CardInner * 2 + OneUiSpacing.CardGap

/** Composer 发送按钮 = 40dp (与 ActionCard 一致) */
private val ComposerSendSize: Dp = OneUiSpacing.CardInner * 2 + OneUiSpacing.CardGap
