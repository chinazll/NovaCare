package com.novacare.feature.assistant

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.novacare.core.common.formatBytes
import com.novacare.core.model.AiTier
import com.novacare.core.model.CleanAdvice
import com.novacare.core.model.CleanRisk
import com.novacare.core.model.FreezeCandidate
import com.novacare.core.model.FreezeRisk
import com.novacare.ui.designsystem.AuroraBackground
import com.novacare.ui.designsystem.EmptyTone
import com.novacare.ui.designsystem.InlineNotice
import com.novacare.ui.designsystem.NovaCard
import com.novacare.ui.designsystem.NovaCareTheme
import com.novacare.ui.designsystem.NovaNowBar
import com.novacare.ui.designsystem.NowBarStatus
import com.novacare.ui.designsystem.PrimaryAction
import com.novacare.ui.designsystem.RingSpinner
import com.novacare.ui.designsystem.RiskChip
import com.novacare.ui.designsystem.SectionHeader
import com.novacare.ui.designsystem.StaggerFlyIn

/**
 * AI 助手（L2）—— 对话式指令界面
 *
 * 【v0.7.2 视觉统一】与 HomeScreen 同一设计语言：
 *   - 顶部 NovaNowBar（替代老 AssistantHeader 角落大头像 + 标题）
 *   - 圆角统一 22dp；NowBar 已有水平 padding，子项不再额外 padding
 *
 * 【这个屏幕到底是什么，不装】
 * 它不是通用聊天机器人。用户输入的每句话都会被解析成一个**结构化意图**，
 * 然后生成一份待确认的动作清单。解析不出来就明说「没听懂」，
 * 并列出真正能听懂的说法 —— 这是本屏幕所有文案的底线。
 *
 * 布局：
 *   ┌ 顶栏           助手身份 + 当前解析层（本地规则 / 云端模型）
 *   ├ 能力提示       云端未开启时说明「离线规则、覆盖有限」
 *   ├ 消息流         用户气泡靠右（强调色低饱和底），助手气泡靠左（卡片底）
 *   │                助手气泡可附带清单卡片（每项带风险标签 + 可验证理由）
 *   ├ 思考指示       三点跳动 + 正在扫描设备的文案
 *   ├ 结果提示       执行后的真实释放量（区分「已释放」与「需手动处理」）
 *   └ 输入区         圆角输入框 + 圆形发送键，固定在底部并跟随键盘上移
 *
 * 空态：不用一句"暂无消息"打发，而是给出四个能真正被解析的示例口令。
 */
@Composable
fun AssistantScreen(
    rootPath: String,
    modifier: Modifier = Modifier,
    viewModel: AssistantViewModel = hiltViewModel(),
) {
    val bubbles by viewModel.bubbles.collectAsStateWithLifecycle()
    val thinking by viewModel.thinking.collectAsStateWithLifecycle()
    val capability by viewModel.capability.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val report by viewModel.report.collectAsStateWithLifecycle()

    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // 从设置页回来时重新读取云端模型配置 —— 用户可能刚打开/关闭了 L3
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshCapability()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 新消息或思考状态变化时滚到底部 —— 用户永远看得到最新一条
    LaunchedEffect(bubbles.size, thinking) {
        val target = bubbles.size + if (thinking) 1 else 0
        if (target > 0) listState.animateScrollToItem(target - 1)
    }

    val canSend = draft.isNotBlank() && !thinking
    val send = {
        if (canSend) {
            viewModel.submit(draft, rootPath)
            draft = ""
        }
    }

    AuroraBackground {
        Column(
            modifier = modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            // v0.7.2 视觉统一：AssistantHeader → NovaNowBar
            NovaNowBar(
                title = "助手",
                subtitle = capability.tierLabel,
                status = when {
                    !capability.engineAvailable -> NowBarStatus.Error
                    capability.cloudEnabled -> NowBarStatus.Healthy
                    else -> NowBarStatus.Idle
                },
                modifier = Modifier.padding(top = 10.dp),
            )

            // 云端模型未开启时如实说明能力边界 —— 用户有权知道对面是什么
            if (!capability.cloudEnabled) {
                StaggerFlyIn(index = 0) {
                    InlineNotice(
                        text = "当前使用本地规则解析（不联网）。它能听懂清理缓存 / 清理垃圾 / " +
                            "冻结不常用应用 / 分析存储这四类说法。要理解更复杂的句子，" +
                            "可在「设置 → 云端 AI」中自行开启。",
                        tone = EmptyTone.Neutral,
                        icon = Icons.Outlined.Shield,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
            }

            error?.let { message ->
                StaggerFlyIn(index = 1) {
                    InlineNotice(
                        text = message,
                        tone = EmptyTone.Error,
                        actionText = "重试",
                        onAction = { viewModel.retry(rootPath) },
                        icon = Icons.Outlined.SearchOff,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
            }

            report?.let { r ->
                StaggerFlyIn(index = 2) {
                    ExecutionNotice(r = r, onDismiss = viewModel::clearReport)
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                if (bubbles.isEmpty()) {
                    SuggestionPanel(
                        onPick = { prompt -> viewModel.submit(prompt, rootPath) },
                        enabled = !thinking,
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 12.dp,
                            bottom = 16.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(bubbles, key = { it.id }) { bubble ->
                            when (bubble) {
                                is AssistantViewModel.Bubble.User ->
                                    UserBubble(text = bubble.text)

                                is AssistantViewModel.Bubble.Assistant ->
                                    AssistantBubble(
                                        bubble = bubble,
                                        onExecute = { advices ->
                                            viewModel.executePlan(advices, rootPath)
                                        },
                                    )
                            }
                        }

                        if (thinking) {
                            item(key = "thinking") { ThinkingIndicator() }
                        }
                    }
                }
            }

            AssistantComposer(
                value = draft,
                onValueChange = { draft = it },
                onSend = send,
                enabled = !thinking,
                canSend = canSend,
            )
        }
    }
}

// ------------------------------------------------------------
// 消息气泡
// ------------------------------------------------------------

/** 用户气泡：靠右，强调色低饱和底 —— 唯一区分"我说的"的方式，不抢视觉 */
@Composable
private fun UserBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = NovaCareTheme.colors.accent.copy(alpha = 0.16f),
            border = BorderStroke(
                1.dp,
                NovaCareTheme.colors.accent.copy(alpha = 0.28f),
            ),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            )
        }
    }
}

/** 助手气泡：靠左，卡片底 + 发丝描边；附带可执行清单时给出 CTA */
@Composable
private fun AssistantBubble(
    bubble: AssistantViewModel.Bubble.Assistant,
    onExecute: (List<CleanAdvice>) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    if (bubble.understood) {
                        NovaCareTheme.colors.accent.copy(alpha = 0.12f)
                    } else {
                        NovaCareTheme.colors.riskCaution.copy(alpha = 0.14f)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (bubble.understood) {
                    Icons.Outlined.AutoAwesome
                } else {
                    Icons.Outlined.SearchOff
                },
                contentDescription = null,
                tint = if (bubble.understood) {
                    NovaCareTheme.colors.accent
                } else {
                    NovaCareTheme.colors.riskCaution
                },
                modifier = Modifier.size(15.dp),
            )
        }
        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(
                    1.dp,
                    NovaCareTheme.colors.hairline,
                ),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    // 没听懂时，第一行就是明确的否认，避免用户误以为被理解了
                    if (!bubble.understood) {
                        Text(
                            text = "没听懂这句话",
                            style = MaterialTheme.typography.titleMedium,
                            color = NovaCareTheme.colors.riskCaution,
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(
                        text = bubble.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    // 解析依据：把"它到底理解成什么"摊开给用户看，而不是黑箱
                    val understoodMeta = buildList {
                        if (bubble.olderThanDays != null) add("早于 ${bubble.olderThanDays} 天")
                        if (bubble.excludedLabels.isNotEmpty()) {
                            add("排除 ${bubble.excludedLabels.joinToString("、")}")
                        }
                    }
                    if (understoodMeta.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        understoodMeta.forEach { line ->
                            Text(
                                text = "· $line",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = sourceLine(bubble),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                }
            }

            when (val card = bubble.card) {
                is AssistantViewModel.ReplyCard.Plan -> PlanCard(card = card, onExecute = onExecute)
                is AssistantViewModel.ReplyCard.Freeze -> FreezeCard(card = card)
                null -> Unit
            }
        }
    }
}

/** 结论来源标注 —— 用户有权知道这句话是规则算出来的还是模型说的 */
private fun sourceLine(bubble: AssistantViewModel.Bubble.Assistant): String {
    val tier = when (bubble.tier) {
        AiTier.DETERMINISTIC -> "本地规则解析"
        AiTier.ON_DEVICE -> "端侧模型解析"
        AiTier.CLOUD -> "云端模型解析"
    }
    return if (bubble.understood) {
        "$tier · 置信度 ${(bubble.confidence * 100).toInt()}% · 未执行任何操作"
    } else {
        "$tier · 未识别出意图 · 未执行任何操作"
    }
}

// ------------------------------------------------------------
// 助手回复附带的可执行卡片
// ------------------------------------------------------------

/**
 * 清理清单卡片。
 *
 * 关键诚实点：只有 SAFE 项才允许直接点执行；需确认 / 有风险的项
 * 需要用户在清理页逐项确认，这里只做展示 + 指路。
 */
@Composable
private fun PlanCard(
    card: AssistantViewModel.ReplyCard.Plan,
    onExecute: (List<CleanAdvice>) -> Unit,
) {
    val colors = NovaCareTheme.colors
    val safeItems = card.advices.filter { it.risk == CleanRisk.SAFE }
    val riskyCount = card.advices.size - safeItems.size

    Column(modifier = Modifier.padding(top = 10.dp)) {
        NovaCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "待确认清单",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${card.advices.size} 项 · 共 ${card.totalBytes.formatBytes()}" +
                            " · 其中安全项 ${card.safeBytes.formatBytes()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.CleaningServices,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(Modifier.height(12.dp))

            // 只展示前 6 项，避免气泡无限长；剩余数量如实告知
            card.advices.take(6).forEach { advice ->
                AdviceRow(advice = advice)
            }
            if (card.advices.size > 6) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "另有 ${card.advices.size - 6} 项，请到「清理」页查看完整清单",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (riskyCount > 0) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "其中 $riskyCount 项需要你逐项确认（清错会丢数据），" +
                        "助手不会替你决定。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.riskCaution,
                )
            }

            Spacer(Modifier.height(14.dp))
            if (safeItems.isNotEmpty()) {
                PrimaryAction(
                    text = "清理 ${safeItems.size} 项安全项",
                    subtitle = "预计释放 ${card.safeBytes.formatBytes()} · 会移入回收站可撤销",
                    onClick = { onExecute(safeItems) },
                )
            } else {
                Text(
                    text = "没有可安全直接清理的项。这类内容需要你到「清理」页确认后执行。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AdviceRow(advice: CleanAdvice) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = advice.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = advice.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = advice.recommendedBytes.formatBytes(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            RiskChip(advice.risk)
        }
    }
}

/** 冻结候选卡片：冻结是可逆的，但仍需用户逐项确认 —— 这里只给清单和数量 */
@Composable
private fun FreezeCard(card: AssistantViewModel.ReplyCard.Freeze) {
    val colors = NovaCareTheme.colors
    Column(modifier = Modifier.padding(top = 10.dp)) {
        NovaCard {
            Text(
                text = "可冻结清单",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "冻结后应用不会后台自启，图标保留、数据不丢，随时可解冻。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            card.candidates.take(6).forEach { candidate ->
                FreezeRow(candidate = candidate)
            }
            if (card.candidates.size > 6) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "另有 ${card.candidates.size - 6} 个，请到「冻结」页查看",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = colors.riskCaution,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "冻结属于改动系统状态，助手不代为执行。请到「冻结」页逐项确认。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FreezeRow(candidate: FreezeCandidate) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = candidate.app.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = candidate.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = candidate.daysUnused?.let { "$it 天未用" } ?: "从未使用",
                style = MaterialTheme.typography.labelMedium,
                color = NovaCareTheme.colors.riskCaution,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = when (candidate.risk) {
                    com.novacare.core.model.FreezeRisk.SAFE -> "可安全冻结"
                    com.novacare.core.model.FreezeRisk.CAUTION -> "冻结后可能收不到通知"
                    com.novacare.core.model.FreezeRisk.RISKY -> "系统组件，不建议"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ------------------------------------------------------------
// 思考指示 / 执行结果
// ------------------------------------------------------------

/**
 * 三点跳动。
 * 不用 CircularProgressIndicator —— 那时长不可知的旋转会让用户以为卡住了，
 * 这里明确写出「正在做什么」，把等待变成可理解的进度。
 */
@Composable
private fun ThinkingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(
                1.dp,
                NovaCareTheme.colors.hairline,
            ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RingSpinner(
                    color = NovaCareTheme.colors.accent,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "正在读取设备状态并解析这句话…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 执行结果：把「真的动了什么」和「需要你自己做什么」分开展示，不合并成一个漂亮数字 */
@Composable
private fun ExecutionNotice(
    r: AssistantViewModel.ExecutionReport,
    onDismiss: () -> Unit,
) {
    val colors = NovaCareTheme.colors
    val tone = when {
        r.failed > 0 -> EmptyTone.Warning
        r.succeeded == 0 -> EmptyTone.Neutral
        else -> EmptyTone.Success
    }
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        InlineNotice(
            text = buildString {
                append("本次已释放 ${r.freedBytes.formatBytes()}（成功 ${r.succeeded} 项")
                if (r.failed > 0) append("，失败 ${r.failed} 项")
                append("）")
            },
            tone = tone,
            icon = if (tone == EmptyTone.Success) Icons.Outlined.CheckCircle else null,
            actionText = "知道了",
            onAction = onDismiss,
        )
        if (r.needsManual > 0) {
            Spacer(Modifier.height(6.dp))
            InlineNotice(
                text = "另有 ${r.needsManual} 项需要你在系统「存储」页手动清理 —— " +
                    "普通模式下应用无权代清第三方缓存，这部分空间**尚未释放**。",
                tone = EmptyTone.Warning,
            )
        }
        if (r.recycleBinPath != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "已移入回收站，7 天内可撤销",
                style = MaterialTheme.typography.bodySmall,
                color = colors.healthGood,
            )
        }
    }
}

// ------------------------------------------------------------
// 空态：示例口令
// ------------------------------------------------------------

private data class PromptChip(
    val text: String,
    val hint: String,
    val icon: ImageVector,
)

/**
 * 空态不是「暂无消息」。
 * 四个示例对应助手**真正**能解析的四类意图，点一下就直接发送 ——
 * 用户不必猜"我能说什么"。
 */
@Composable
private fun SuggestionPanel(
    onPick: (String) -> Unit,
    enabled: Boolean,
) {
    val prompts = listOf(
        PromptChip("清理一下缓存", "扫描各应用缓存并列出可清理项", Icons.Outlined.CleaningServices),
        PromptChip("哪些应用可以冻结", "找出长期未使用的应用", Icons.Outlined.Speed),
        PromptChip("为什么手机变慢了", "分析存储占用与可回收空间", Icons.Outlined.Storage),
        PromptChip("清理半年前的垃圾，别动微信", "支持「时间范围 + 排除应用」的组合说法", Icons.Outlined.Bolt),
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "empty-intro") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(NovaCareTheme.colors.accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = NovaCareTheme.colors.accent,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = "用一句话描述你想做什么",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "助手会把它拆成一份待确认的清单，不会直接执行。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item(key = "prompt-header") { SectionHeader(title = "可以这样说") }

        items(prompts, key = { it.text }) { prompt ->
            SuggestionRow(prompt = prompt, enabled = enabled, onPick = onPick)
        }

        item(key = "guard") {
            Spacer(Modifier.height(4.dp))
            NovaCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = NovaCareTheme.colors.healthGood,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "所有解析都在本机完成。除非你在设置里主动开启云端 AI，" +
                            "否则不会有任何文字被发送到网络。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    prompt: PromptChip,
    enabled: Boolean,
    onPick: (String) -> Unit,
) {
    NovaCard(
        onClick = if (enabled) ({ onPick(prompt.text) }) else null,
        modifier = Modifier.semantics {
            contentDescription = "示例指令：${prompt.text}。${prompt.hint}"
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = prompt.icon,
                contentDescription = null,
                tint = if (enabled) {
                    NovaCareTheme.colors.accent
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = prompt.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = prompt.hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ------------------------------------------------------------
// 输入区
// ------------------------------------------------------------

/**
 * 底部输入区。
 *
 * 输入框用 BasicTextField 而非 OutlinedTextField —— 后者的下划线与浮动标签
 * 在这个尺寸下会显得笨重。这里要的是一枚圆角胶囊 + 一侧圆形发送键。
 * 回车键映射为发送（ImeAction.Send），单手可见即用。
 */
@Composable
private fun AssistantComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
    canSend: Boolean,
) {
    val colors = NovaCareTheme.colors

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, colors.hairline),
        shape = RoundedCornerShape(
            topStart = 0.dp,
            topEnd = 0.dp,
            bottomStart = 0.dp,
            bottomEnd = 0.dp,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(
                        width = 1.dp,
                        color = if (enabled) colors.hairline else colors.hairline.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.extraLarge,
                    )
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = "例如：清理半年没用的缓存，别动微信",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    textStyle = LocalTextStyle.current.merge(
                        MaterialTheme.typography.bodyMedium,
                    ).copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(colors.accent),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.width(10.dp))

            val sendBg = if (canSend) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(sendBg)
                    .then(
                        if (canSend) {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button,
                                onClick = onSend,
                            )
                        } else {
                            Modifier
                        }
                    )
                    .semantics {
                        contentDescription = if (canSend) "发送" else "发送（请输入内容）"
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Send,
                    contentDescription = null,
                    tint = if (canSend) {
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
