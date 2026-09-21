package com.novacare.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novacare.core.common.formatBytes
import com.novacare.core.model.DimensionScore
import com.novacare.core.model.HealthDimension
import com.novacare.core.model.HealthScore
import com.novacare.core.system.MissingCapability
import com.novacare.ui.designsystem.AiOrb
import com.novacare.ui.designsystem.MotionTokens
import com.novacare.ui.designsystem.NovaCareColors
import com.novacare.ui.designsystem.NovaCareTheme
import com.novacare.ui.designsystem.NovaTap
import com.novacare.ui.designsystem.OneUiAppBar
import com.novacare.ui.designsystem.OneUiRadius
import com.novacare.ui.designsystem.OneUiSpacing

// =========================================================================
// OneUI 9 Device Care 风格的首页
//
// 整个屏按 Samsung Device Care 重做：
//   1. AppBar（NovaCare 标题 + 内核状态圆点）
//   2. 健康 Hero（OneUI 8.5 大字分数 + 评级语 + 评分依据，4 维度 OneUI 卡片列表）
//   3. AI 助手入口（squircle 玻璃浮片，含有呼吸 orb）
//
// 只有"真有依据才显示"的导航箭头：
//   - 哪个维度分低 + 有话可说 → 该行才出现跳转
//   - 全绿时什么箭头都不显示
// =========================================================================

/** 首页可跳转的目的地 —— 故意只两个（避免和底栏复读） */
enum class HomeDestination { CLEAN, FREEZE }

@Composable
fun HomeScreen(
    onOpenAssistant: () -> Unit,
    onNavigate: (HomeDestination) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val score by viewModel.score.collectAsStateWithLifecycle()
    val basis by viewModel.basis.collectAsStateWithLifecycle()
    val hints by viewModel.hints.collectAsStateWithLifecycle()
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val missing by viewModel.missing.collectAsStateWithLifecycle()
    val engineAvailable by viewModel.engineAvailable.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshCapabilities()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val view = LocalView.current

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            OneUiAppBar(
                title = "NovaCare",
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (engineAvailable) NovaCareTheme.colors.healthGood
                                else NovaCareTheme.colors.riskCaution
                            ),
                    )
                },
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = OneUiSpacing.BlockGap),
            ) {
                Spacer(Modifier.height(OneUiSpacing.CardGap))

                when (loadState) {
                    HomeLoadState.Loading -> ReadingHero()

                    is HomeLoadState.Failed -> FailedHero(
                        message = (loadState as HomeLoadState.Failed).message,
                        onRetry = {
                            NovaTap(view)
                            viewModel.refresh()
                        },
                    )

                    HomeLoadState.Ready -> {
                        HealthHero(
                            score = score,
                            basis = basis,
                            hints = hints,
                            onNavigate = { destination ->
                                NovaTap(view)
                                onNavigate(destination)
                            },
                        )

                        Spacer(Modifier.height(OneUiSpacing.BlockGap))

                        // OneUI 8.5 Device Care：宽状态条 + 大百分比
                        DeviceStatusCard(overview = overview)

                        Spacer(Modifier.height(OneUiSpacing.BlockGap))

                        AssistantEntry {
                            NovaTap(view)
                            onOpenAssistant()
                        }

                        // 降级说明紧跟其后（不并排）
                        DegradedSection(
                            engineAvailable = engineAvailable,
                            missing = missing,
                            onRetry = { viewModel.refresh() },
                            onGrant = { capability ->
                                NovaTap(view)
                                viewModel.grant(capability)
                            },
                        )
                    }
                }

                Spacer(Modifier.height(160.dp))
            }
        }
    }
}

// =========================================================================
// 2. 健康 Hero —— OneUI 9 "single proposition" 模式
// =========================================================================
@Composable
private fun HealthHero(
    score: HealthScore?,
    basis: String,
    hints: Map<HealthDimension, String>,
    onNavigate: (HomeDestination) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors
    val scored = score?.takeIf { it.dimensions.isNotEmpty() }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AiOrb(size = 18.dp)
            Spacer(Modifier.width(OneUiSpacing.CardGap))
            Text(
                text = "设备健康",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(OneUiSpacing.CardGap))

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = scored?.total?.toString() ?: "—",
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.W200),
                color = scored?.let { scoreColor(it.total, colors) } ?: cs.onSurface,
                maxLines = 1,
            )
            Spacer(Modifier.width(OneUiSpacing.SectionTitleGap))
            Text(
                text = score?.verdict ?: "正在评估",
                style = MaterialTheme.typography.titleMedium,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        if (basis.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = basis,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }

        if (scored != null) {
            Spacer(Modifier.height(OneUiSpacing.CardInner))
            scored.dimensions.forEachIndexed { index, dim ->
                key(dim.dimension) {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(MotionTokens.standard) +
                            slideInVertically(
                                animationSpec = MotionTokens.standardOffset,
                                initialOffsetY = { (index + 1) * 14 },
                            ),
                    ) {
                        DimensionRow(
                            dim = dim,
                            hint = hints[dim.dimension],
                            onNavigate = onNavigate,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DimensionRow(
    dim: DimensionScore,
    hint: String?,
    onNavigate: (HomeDestination) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors
    val fraction = (dim.score.coerceIn(0, 100)) / 100f

    val destination = destinationFor(dim.dimension)
    val actionable = hint != null && destination != null && dim.score < GOOD_THRESHOLD

    val rowModifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(OneUiRadius.Small))
        .let { base ->
            if (actionable) base.clickable { onNavigate(destination!!) } else base
        }
        .padding(vertical = OneUiSpacing.CardGap)

    Column(modifier = rowModifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = dim.dimension.displayName,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
                color = cs.onSurface,
                modifier = Modifier.width(64.dp),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(cs.onSurface.copy(alpha = 0.06f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(scoreColor(dim.score, colors)),
                )
            }
            Spacer(Modifier.width(OneUiSpacing.SectionTitleGap))
            Text(
                text = "${dim.score}",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
                color = cs.onSurface,
                modifier = Modifier.width(36.dp),
            )
            if (actionable) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = "去处理",
                    tint = cs.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        val caption = if (actionable) hint!! else dim.summary
        Spacer(Modifier.height(2.dp))
        Text(
            text = caption,
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(start = 64.dp),
        )
    }
}

private fun destinationFor(dimension: HealthDimension): HomeDestination? = when (dimension) {
    HealthDimension.STORAGE -> HomeDestination.CLEAN
    HealthDimension.APP -> HomeDestination.FREEZE
    HealthDimension.MEMORY, HealthDimension.BATTERY -> null
}

private const val GOOD_THRESHOLD = 85

private fun scoreColor(total: Int, colors: NovaCareColors): androidx.compose.ui.graphics.Color = when {
    total >= GOOD_THRESHOLD -> colors.healthGood
    total >= 60 -> colors.riskCaution
    else -> colors.riskRisky
}

// =========================================================================
// 3. 设备状态 —— OneUI 8.5 Device Care 风格：宽状态条 + 大百分比
// =========================================================================
@Composable
private fun DeviceStatusCard(overview: HomeViewModel.Overview?) {
    val cs = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(OneUiRadius.Medium),
        color = cs.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(OneUiSpacing.CardInner)) {
            Text(
                text = "设备状态",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W600),
                color = cs.onSurface,
            )

            if (overview == null) {
                Spacer(Modifier.height(OneUiSpacing.CardGap))
                Text(
                    text = "尚无读数",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
                return@Surface
            }

            Spacer(Modifier.height(OneUiSpacing.BlockGap))

            MetricBar(
                percentText = if (overview.totalBytes > 0L) {
                    "${(overview.usedBytes.toFloat() / overview.totalBytes * 100).toInt()}%"
                } else "—",
                label = "存储",
                detail = if (overview.totalBytes > 0L) {
                    "已用 ${overview.usedBytes.formatBytes()} · 共 ${overview.totalBytes.formatBytes()}"
                } else "未获取存储总量",
                fraction = if (overview.totalBytes > 0L) {
                    (overview.usedBytes.toFloat() / overview.totalBytes).coerceIn(0f, 1f)
                } else 0f,
                measurable = overview.totalBytes > 0L,
            )

            Spacer(Modifier.height(OneUiSpacing.BlockGap))

            val memMeasurable = overview.memoryTotalBytes > 0L
            val memFraction = if (memMeasurable) {
                (overview.memoryAvailableBytes.toFloat() / overview.memoryTotalBytes).coerceIn(0f, 1f)
            } else 0f
            MetricBar(
                percentText = if (memMeasurable) "可用 ${(memFraction * 100).toInt()}%" else "—",
                label = "内存",
                detail = if (memMeasurable) {
                    "空闲 ${overview.memoryAvailableBytes.formatBytes()} · 共 ${overview.memoryTotalBytes.formatBytes()}"
                } else "未获取内存读数",
                fraction = memFraction,
                measurable = memMeasurable,
            )

            Spacer(Modifier.height(OneUiSpacing.BlockGap))

            MetricBar(
                percentText = if (overview.batteryPercent > 0) "${overview.batteryPercent}%" else "—",
                label = "电池",
                detail = buildString {
                    if (overview.batteryPercent > 0) {
                        if (overview.batteryTemperatureTenths > 0) {
                            append("${overview.batteryTemperatureTenths / 10f}°C · ")
                        }
                        append(batteryHealthLabel(overview.batteryHealth))
                    } else {
                        append("未获取电量")
                    }
                },
                fraction = (overview.batteryPercent / 100f).coerceIn(0f, 1f),
                measurable = overview.batteryPercent > 0,
            )
        }
    }
}

/** OneUI 8.5 指标条：百分比 + 标签 + 详情 + 14dp squircle 状态条 */
@Composable
private fun MetricBar(
    percentText: String,
    label: String,
    detail: String,
    fraction: Float,
    measurable: Boolean,
) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = percentText,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.W200),
                color = if (measurable) cs.onSurface else cs.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
                    color = cs.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(colors.ringTrack),
        ) {
            if (measurable && fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(cs.primary),
                )
            }
        }
    }
}

@Composable
private fun batteryHealthLabel(raw: String): String = when (raw) {
    "good" -> "状态正常"
    "overheat" -> "过热"
    "dead" -> "已损坏"
    "over_voltage" -> "电压过高"
    "cold" -> "温度过低"
    else -> "系统未给出健康结论"
}

// =========================================================================
// 4. AI 助手 —— OneUI squircle 浮片
// =========================================================================
@Composable
private fun AssistantEntry(onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OneUiRadius.Medium)),
        color = cs.primaryContainer,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(OneUiSpacing.CardInner),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AiOrb(size = 28.dp)
            Spacer(Modifier.width(OneUiSpacing.CardInner))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "问问 AI 助手",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W600),
                    color = cs.onPrimaryContainer,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "读设备状态 · 给建议 · 帮你动手",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onPrimaryContainer.copy(alpha = 0.74f),
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                tint = cs.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// =========================================================================
// 装载态
// =========================================================================
@Composable
private fun ReadingHero() {
    val cs = MaterialTheme.colorScheme
    Text(
        text = "正在读取设备状态",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W300),
        color = cs.onSurface,
    )
    Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
    Text(
        text = "存储、内存、电池三项读数来自系统，不预估、不填充。",
        style = MaterialTheme.typography.bodySmall,
        color = cs.onSurfaceVariant,
    )
}

@Composable
private fun FailedHero(message: String, onRetry: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors

    Text(
        text = "读不到",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W300),
        color = colors.riskCaution,
    )
    Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
    Text(
        text = "本次扫描没有拿到任何一项读数，所以这里不给分数，也不用上一次的结果凑数。",
        style = MaterialTheme.typography.bodySmall,
        color = cs.onSurfaceVariant,
    )
    Spacer(Modifier.height(6.dp))
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = cs.onSurfaceVariant,
    )
    Spacer(Modifier.height(OneUiSpacing.CardInner))
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(OneUiRadius.Small))
            .background(cs.onSurface.copy(alpha = 0.06f))
            .clickable { onRetry() }
            .padding(horizontal = OneUiSpacing.CardInner, vertical = OneUiSpacing.CardGap),
    ) {
        Text(
            text = "重试",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W600),
            color = cs.primary,
        )
    }
}

// =========================================================================
// 5. 降级说明 —— 引擎 / 权限，每项带修复动作
// =========================================================================
@Composable
private fun DegradedSection(
    engineAvailable: Boolean,
    missing: List<MissingCapability>,
    onRetry: () -> Unit,
    onGrant: (MissingCapability) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors

    if (!engineAvailable) {
        Spacer(Modifier.height(OneUiSpacing.BlockGap))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(OneUiRadius.Small))
                .background(colors.riskCaution.copy(alpha = 0.08f))
                .clickable { onRetry() }
                .padding(horizontal = OneUiSpacing.CardInner, vertical = OneUiSpacing.CardGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "内核不可用",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.riskCaution,
                )
                Text(
                    text = "存储与内存的读数仍取自系统；垃圾识别、回收量估算不可用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
            Text(
                text = "重试",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W600),
                color = colors.riskCaution,
            )
        }
    }

    missing.forEach { capability ->
        Spacer(Modifier.height(OneUiSpacing.CardGap))
        PermissionRow(capability = capability, onGrant = { onGrant(capability) })
    }
}

@Composable
private fun PermissionRow(
    capability: MissingCapability,
    onGrant: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors
    val (title, why) = when (capability) {
        MissingCapability.USAGE_STATS ->
            "使用情况访问" to "缺少它就无法判断哪些应用长期没被打开"
        MissingCapability.ALL_FILES ->
            "所有文件访问" to "缺少它只能扫到部分目录，清理页的结果会偏少"
        MissingCapability.NOTIFICATIONS ->
            "通知权限" to "长任务的执行结果无法在通知里提醒"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OneUiRadius.Small))
            .background(colors.riskCaution.copy(alpha = 0.06f))
            .clickable { onGrant() }
            .padding(horizontal = OneUiSpacing.CardInner, vertical = OneUiSpacing.CardGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurface,
            )
            Text(
                text = why,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }
        Text(
            text = "去开启",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W600),
            color = cs.primary,
        )
    }
}