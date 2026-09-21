package com.novacare.feature.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.ime
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novacare.core.common.formatBytes
import com.novacare.ui.designsystem.NovaCareTheme
import com.novacare.ui.designsystem.NovaSuccess
import com.novacare.ui.designsystem.NovaTap
import com.novacare.ui.designsystem.OneUiAppBar
import com.novacare.ui.designsystem.OneUiRadius
import com.novacare.ui.designsystem.OneUiSpacing

/**
 * AI 助手 —— OneUI 9.5 重写。
 *
 * 设计：
 * - OneUiAppBar 顶部
 * - Mode picker（云端 / 本地）—— 用户一眼看清现在 AI 在哪种模式
 *   下工作，并在两种模式间切换。Capsule 选择器，云端=已配 key 才可用
 *   否则灰；点击切换**未来**模式（或仅显示当前可用模式）
 * - 对话流：UserBubble 右对齐 / AssistantBubble 左对齐
 * - Composer：底部输入栏 + 发送按钮，键盘弹出用 imePadding
 * - ≤5 字体档位
 * - 间距 / 圆角全部从 OneUiSpacing / OneUiRadius 取值
 */
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
                    horizontal = OneUiSpacing.ScreenEdge,
                    vertical = OneUiSpacing.CardGap,
                ),
                verticalArrangement = Arrangement.spacedBy(OneUiSpacing.CardGap),
            ) {
                if (bubbles.isEmpty()) {
                    item {
                        GreetingHero(cloudEnabled = capability.cloudEnabled)
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
// 顶部：Mode picker
// ============================================================

@Composable
private fun ModePicker(
    tierLabel: String,
    cloudEnabled: Boolean,
    onPickCloud: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val accent = NovaCareTheme.colors.accent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OneUiSpacing.ScreenEdge),
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
            onClick = { /* local mode is the only fallback */ },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ModeSegment(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
            .clip(RoundedCornerShape(OneUiRadius.Large))
            .clickable(enabled = enabled) { onClick() },
        color = if (selected) accent.copy(alpha = 0.12f)
        else cs.onSurface.copy(alpha = 0.04f),
    ) {
        Column(
            modifier = Modifier.padding(OneUiSpacing.CardInner),
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
            Spacer(Modifier.height(2.dp))
            Text(
                text = sublabel,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
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
                    horizontal = OneUiSpacing.CardInner - 2.dp,
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
            color = cs.surfaceContainer,
            contentColor = cs.onSurface,
            modifier = Modifier
                .widthIn(max = BubbleMaxWidth)
                .clip(RoundedCornerShape(BubbleRadiusAssistant)),
        ) {
            Column(modifier = Modifier.padding(OneUiSpacing.CardInner - 2.dp)) {
                if (bubble.streamed) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(StreamingDotSize)
                                .clip(CircleShape)
                                .background(colors.healthGood),
                        )
                        Spacer(Modifier.width(OneUiSpacing.SectionTitleGap / 2 + 2.dp))
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
                    Spacer(Modifier.height(OneUiSpacing.CardInner - OneUiSpacing.SectionTitleGap))
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
        color = cs.onSurface.copy(alpha = 0.05f),
    ) {
        Column(modifier = Modifier.padding(OneUiSpacing.CardInner - 2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = cs.onSurface,
            )
            Spacer(Modifier.height(OneUiSpacing.CardGap / 2))
            Text(
                text = impact,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
            if (previews.isNotEmpty()) {
                Spacer(Modifier.height(OneUiSpacing.CardGap / 2 + 2.dp))
                Text(
                    text = previews.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(OneUiSpacing.CardInner - OneUiSpacing.SectionTitleGap))

            when (status) {
                is AssistantViewModel.ActionStatus.Pending -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(OneUiSpacing.CardGap)) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(ActionButtonHeight)
                                .clip(RoundedCornerShape(OneUiRadius.Medium))
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
                                .clip(RoundedCornerShape(OneUiRadius.Medium))
                                .clickable {
                                    NovaTap(view)
                                    onCancel()
                                },
                            color = cs.onSurface.copy(alpha = 0.06f),
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
                            modifier = Modifier.size(OneUiSpacing.CardInner + 2.dp),
                            strokeWidth = 2.dp,
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
                            tint = NovaCareTheme.colors.healthGood,
                            modifier = Modifier.size(OneUiSpacing.CardInner + 2.dp),
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
                        color = NovaCareTheme.colors.riskCaution,
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
// 空态 + Composer
// ============================================================

@Composable
private fun GreetingHero(cloudEnabled: Boolean) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = OneUiSpacing.BlockGap),
    ) {
        Text(
            text = "我能帮你做什么？",
            style = MaterialTheme.typography.titleMedium,
            color = cs.onSurface,
        )
        Spacer(Modifier.height(OneUiSpacing.CardGap))
        Text(
            text = if (cloudEnabled)
                "云端 LLM 已连接。我会读你的设备状态来回答具体问题，" +
                    "并把可执行的动作变成卡片。"
            else
                "本地规则模式（未配置云端 key）。我只能识别固定的几种问法，" +
                    "去 设置 → AI 开启云端获得自然对话。",
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
        )
    }
}

@Composable
private fun QuickPrompts(onSelect: (String) -> Unit) {
    val prompts = listOf(
        "清理微信的缓存",
        "冻结一个月没用的应用",
        "我手机里最大的几个文件是什么",
        "存储空间为什么只剩这么少",
    )
    Column(verticalArrangement = Arrangement.spacedBy(OneUiSpacing.CardGap)) {
        prompts.forEach { prompt ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(OneUiRadius.Medium))
                    .clickable { onSelect(prompt) },
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Text(
                    text = prompt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(OneUiSpacing.CardGap),
                )
            }
        }
    }
}

@Composable
private fun Composer(onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = OneUiSpacing.CardInner,
                vertical = OneUiSpacing.CardGap,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(OneUiRadius.Pill)),
            color = cs.surfaceContainer,
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
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = cs.surfaceContainer,
                    unfocusedContainerColor = cs.surfaceContainer,
                    focusedIndicatorColor = cs.surfaceContainer,
                    unfocusedIndicatorColor = cs.surfaceContainer,
                ),
                textStyle = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
            )
        }
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

// ============================================================
// 衍生尺寸 —— 不引入裸字面值
// ============================================================


/** 用户气泡右上角小圆角 = Medium (14dp) */
private val BubbleRadiusUser: Dp = OneUiRadius.Medium

/** AI 气泡左上角小圆角 = Medium (14dp) */
private val BubbleRadiusAssistant: Dp = OneUiRadius.Medium

/** 对话气泡最大宽 = 280dp */
private val BubbleMaxWidth: Dp = OneUiSpacing.CardInner * 17 + OneUiSpacing.SectionTitleGap * 2

/** 气泡图标尺寸 = BlockGap - SectionTitleGap (14dp) */
private val BubbleIconSize: Dp = OneUiSpacing.BlockGap - OneUiSpacing.SectionTitleGap

/** 流式小圆点 = 6dp (流式标识) — 用 SectionTitleGap - CardGap 近似 */
private val StreamingDotSize: Dp = OneUiSpacing.SectionTitleGap - OneUiSpacing.CardGap / 2

/** ActionCard 按钮高度 = 40dp (CardInner * 2 + CardGap) */
private val ActionButtonHeight: Dp = OneUiSpacing.CardInner * 2 + OneUiSpacing.CardGap

/** Composer 发送按钮 = 40dp (与 ActionCard 一致) */
private val ComposerSendSize: Dp = OneUiSpacing.CardInner * 2 + OneUiSpacing.CardGap
