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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.CheckCircleOutline
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novacare.core.common.formatBytes
import com.novacare.ui.designsystem.AiOrb
import com.novacare.ui.designsystem.GlassPanel
import com.novacare.ui.designsystem.MotionTokens
import com.novacare.ui.designsystem.NovaCareTheme
import com.novacare.ui.designsystem.NovaSuccess
import com.novacare.ui.designsystem.NovaTap
import com.novacare.ui.designsystem.OneUiAppBar

/**
 * AI 助手 —— OneUI 9.5 真实设计语言。
 *
 * 对话流 + 输入栏。顶部标题 + tier 徽章，空状态给 4 个 quick prompt。
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

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            ) {
                Text(
                    text = capability.tierLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (bubbles.isEmpty()) {
                    item {
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(MotionTokens.standard) +
                                scaleIn(MotionTokens.standard, initialScale = 0.96f),
                        ) { GreetingHero(capability.cloudEnabled) }
                    }
                    item {
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(MotionTokens.standard) +
                                scaleIn(MotionTokens.standard, initialScale = 0.96f),
                        ) {
                            Column {
                                Spacer(Modifier.height(8.dp))
                                QuickPrompts(
                                    onSelect = { prompt ->
                                        NovaTap(view)
                                        viewModel.submit(prompt, rootPath)
                                    }
                                )
                            }
                        }
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

@Composable
private fun GreetingHero(cloudEnabled: Boolean) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 32.dp),
    ) {
        Text(
            text = "我能帮你做什么？",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W600),
            color = cs.onSurface,
        )
        Spacer(Modifier.height(6.dp))
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        prompts.forEach { prompt ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSelect(prompt) },
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Text(
                    text = prompt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }
        }
    }
}

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
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 4.dp)),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
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
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 20.dp)),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (bubble.streamed) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(colors.healthGood),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "云端对话",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Text(
                    text = bubble.text.ifEmpty { "…" },
                    style = MaterialTheme.typography.bodyMedium,
                )
                val card = bubble.card
                val actionStatus = bubble.actionStatus
                if (card != null && actionStatus != null) {
                    Spacer(Modifier.height(10.dp))
                    ActionCard(
                        card = card,
                        status = actionStatus,
                        onConfirm = { onConfirm(bubble.id) },
                        onCancel = { onCancel(bubble.id) },
                    )
                }
                if (!bubble.understood && !bubble.streamed) {
                    Spacer(Modifier.height(6.dp))
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

/**
 * 行动卡片 —— 「感知 → 分析 → 行动 → 确认」闭环里的「行动 + 确认」载体。
 *
 * AI 给出可执行动作后，卡片显示「将做什么 + 影响」，用户点确认才真正执行，
 * 执行结果直接回显在卡片内（Done 状态），随对话流保留。
 */
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
            .clip(RoundedCornerShape(16.dp)),
        color = cs.onSurface.copy(alpha = 0.05f),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.W600,
                color = cs.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = impact,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
            if (previews.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = previews.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))

            when (status) {
                is AssistantViewModel.ActionStatus.Pending -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(14.dp)),
                            color = cs.primary,
                            contentColor = cs.onPrimary,
                            onClick = {
                                NovaTap(view)
                                onConfirm()
                            },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "确认执行",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.W600,
                                )
                            }
                        }
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(14.dp)),
                            color = cs.onSurface.copy(alpha = 0.06f),
                            contentColor = cs.onSurfaceVariant,
                            onClick = {
                                NovaTap(view)
                                onCancel()
                            },
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
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = cs.primary,
                        )
                        Spacer(Modifier.width(8.dp))
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
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
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

@Composable
private fun Composer(onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val cs = MaterialTheme.colorScheme

    GlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
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
                    focusedContainerColor = cs.surfaceContainer,
                    unfocusedContainerColor = cs.surfaceContainer,
                    focusedIndicatorColor = cs.surfaceContainer,
                    unfocusedIndicatorColor = cs.surfaceContainer,
                ),
                textStyle = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
            )
            Spacer(Modifier.width(4.dp))
            Surface(
                modifier = Modifier
                    .size(40.dp)
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
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

// (end of file)
