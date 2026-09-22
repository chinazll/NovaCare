package com.novacare.feature.clean

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novacare.core.common.formatBytes
import com.novacare.core.domain.key
import com.novacare.core.model.CleanAdvice
import com.novacare.core.model.CleanRisk
import com.novacare.core.system.MissingCapability
import com.novacare.ui.designsystem.NovaCareTheme
import com.novacare.ui.designsystem.NovaSuccess
import com.novacare.ui.designsystem.NovaTap
import com.novacare.ui.designsystem.NovaToggle
import com.novacare.ui.designsystem.OneUiAppBar
import com.novacare.ui.designsystem.OneUiListRow
import com.novacare.ui.designsystem.OneUiListSection
import com.novacare.ui.designsystem.OneUiRadius
import com.novacare.ui.designsystem.OneUiSpacing

// =========================================================================
// CleanScreen — OneUI 9 重写版
//
// 来源：research/oneui9-ref/components_lists_img-01.png
//
// 模式：
//   - 顶部 OneUiAppBar
//   - Idle / Failed 状态：单个 squircle card（OneUiListSection），包含一个
//     OneUiListRow（36dp tinted icon + 标题 + 副文）+ 底部 primary CTA pill
//   - Scanning / Executing 状态：单个 squircle card，含 loading + 文案
//   - Done 状态：单个 squircle card，含 hero + primary CTA
//   - Results 状态：顶部 RingChart 在 card 内 + OneUiListSection 包住建议清单
//     + sticky bottom CTA（OneUiListSection 风格保持一致）
//
// 仅复用 CleanViewModel 状态机；不修改 VM、不修改逻辑，只重写 Compose。
// =========================================================================

@Composable
fun CleanScreen(
    rootPath: String,
    modifier: Modifier = Modifier,
    viewModel: CleanViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val includeRisky by viewModel.includeRisky.collectAsStateWithLifecycle()
    val moveToRecycleBin by viewModel.moveToRecycleBin.collectAsStateWithLifecycle()
    val availability by viewModel.releaseAvailability.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refreshEngineState()
    }

    val view = LocalView.current

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, rootPath) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onResume(rootPath)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        Toast.makeText(view.context, text, Toast.LENGTH_SHORT).show()
        viewModel.consumeMessage()
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            OneUiAppBar(title = "清理")

            when (val s = state) {
                CleanViewModel.UiState.Idle -> EmptyBody(
                    title = "扫一下，看看你能释放多少空间",
                    subtitle = "会读取应用缓存、日志、缩略图与残留文件，列清单让你勾选",
                    ctaLabel = "开始扫描",
                    onCta = {
                        NovaTap(view)
                        viewModel.scanNow(rootPath, force = true)
                    },
                )

                CleanViewModel.UiState.Scanning -> StatusBody(
                    title = "正在扫描",
                    subtitle = "分析存储、缓存与残留文件",
                )

                is CleanViewModel.UiState.Failed -> EmptyBody(
                    title = "扫描失败",
                    subtitle = s.message,
                    ctaLabel = "重试",
                    onCta = {
                        NovaTap(view)
                        viewModel.scanNow(rootPath, force = true)
                    },
                )

                is CleanViewModel.UiState.Results -> ResultsBody(
                    plan = s.plan,
                    engineAvailable = s.engineAvailable,
                    availability = availability,
                    selected = selected,
                    includeRisky = includeRisky,
                    moveToRecycleBin = moveToRecycleBin,
                    onToggle = { key ->
                        NovaToggle(view.context, true)
                        viewModel.toggle(key)
                    },
                    onSelectAll = { all ->
                        NovaTap(view)
                        viewModel.selectAll(all)
                    },
                    onSelectSuggested = {
                        NovaTap(view)
                        viewModel.selectSuggested()
                    },
                    onIncludeRiskyChange = { inc ->
                        NovaTap(view)
                        viewModel.setIncludeRisky(inc)
                    },
                    onMoveToRecycleBinChange = { e ->
                        NovaTap(view)
                        viewModel.setMoveToRecycleBin(e)
                    },
                    onExecute = {
                        NovaSuccess(view.context)
                        viewModel.execute()
                    },
                    onGrantUsage = {
                        NovaTap(view)
                        viewModel.grant(MissingCapability.USAGE_STATS)
                    },
                    onRescan = {
                        NovaTap(view)
                        viewModel.rescan(rootPath)
                    },
                )

                CleanViewModel.UiState.Executing -> StatusBody(
                    title = "正在释放",
                    subtitle = "请保持 NovaCare 在前台",
                )

                is CleanViewModel.UiState.Done -> DoneBody(
                    freedBytes = s.result.freedBytes,
                    succeededCount = s.result.succeeded.size,
                    failedCount = s.result.failed.size,
                    needsManualCount = s.result.needsManual.size,
                    onDone = {
                        NovaSuccess(view.context)
                        viewModel.dismissResult()
                    },
                    onRescan = {
                        NovaTap(view)
                        viewModel.rescan(rootPath)
                    },
                )
            }
        }
    }
}

// ============================================================
// 单一状态视图：EmptyBody / StatusBody
// 一个 squircle card + 36dp tinted icon 行 + 底部 primary CTA
// ============================================================

@Composable
private fun EmptyBody(
    title: String,
    subtitle: String,
    ctaLabel: String,
    onCta: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = OneUiSpacing.BlockGap,
            vertical = OneUiSpacing.SectionTitleGap,
        ),
        verticalArrangement = Arrangement.spacedBy(OneUiSpacing.BlockGap),
    ) {
        item {
            OneUiListSection {
                OneUiListRow(
                    icon = Icons.Outlined.WarningAmber,
                    iconTint = CleanTints.idle,
                    title = title,
                    subtitle = subtitle,
                    onClick = null,
                )
            }
        }
        item {
            PrimaryPillCta(
                text = ctaLabel,
                enabled = true,
                onClick = onCta,
            )
        }
        item {
            Spacer(Modifier.height(OneUiSpacing.EmptyHeight))
        }
    }
}

@Composable
private fun StatusBody(title: String, subtitle: String) {
    val cs = MaterialTheme.colorScheme
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = OneUiSpacing.BlockGap,
            vertical = OneUiSpacing.SectionTitleGap,
        ),
        verticalArrangement = Arrangement.spacedBy(OneUiSpacing.BlockGap),
    ) {
        item {
            OneUiListSection {
                OneUiListRow(
                    icon = Icons.Outlined.WarningAmber,
                    iconTint = cs.primary,
                    title = title,
                    subtitle = subtitle,
                    onClick = null,
                )
            }
        }
        item {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(OneUiSpacing.CardInner * 3),
                    strokeWidth = OneUiSpacing.CardGap / 2,
                    color = cs.primary,
                )
            }
        }
        item {
            Spacer(Modifier.height(OneUiSpacing.EmptyHeight))
        }
    }
}

// ============================================================
// Results —— 顶部 RingChart card + 建议清单 + sticky bottom CTA
// ============================================================

@Composable
private fun ResultsBody(
    plan: com.novacare.core.model.CleanPlan,
    engineAvailable: Boolean,
    availability: ReleaseAvailability,
    selected: Set<String>,
    includeRisky: Boolean,
    moveToRecycleBin: Boolean,
    onToggle: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onSelectSuggested: () -> Unit,
    onIncludeRiskyChange: (Boolean) -> Unit,
    onMoveToRecycleBinChange: (Boolean) -> Unit,
    onExecute: () -> Unit,
    onGrantUsage: () -> Unit,
    onRescan: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val hasAdvices = plan.advices.isNotEmpty()
    val selectedKeys = remember(selected, plan) {
        selected.filter { key -> plan.advices.any { it.key() == key } }.toSet()
    }
    val selectedBytes = remember(selectedKeys, plan) {
        plan.advices.filter { it.key() in selectedKeys }.sumOf { it.recommendedBytes }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = OneUiSpacing.BlockGap,
                vertical = OneUiSpacing.SectionTitleGap,
            ),
            verticalArrangement = Arrangement.spacedBy(OneUiSpacing.CardGap),
        ) {
            // 顶部 hero card：单选 / RingChart / 字节数
            item {
                OneUiListSection {
                    Column(modifier = Modifier.padding(OneUiSpacing.CardInner)) {
                        RingChart(
                            selected = selectedKeys.size,
                            total = plan.advices.size,
                            selectedBytes = selectedBytes,
                            totalBytes = plan.totalReclaimableBytes,
                        )
                        Spacer(Modifier.height(OneUiSpacing.CardInner))
                        ChipRow(
                            allSelected = selectedKeys.size == plan.advices.size && plan.advices.isNotEmpty(),
                            includeRisky = includeRisky,
                            onSelectAll = onSelectAll,
                            onIncludeRiskyChange = onIncludeRiskyChange,
                        )
                    }
                }
            }

            if (!engineAvailable && hasAdvices) {
                item {
                    NoticeBlock(
                        text = "清理内核不可用：以下只有应用缓存分析，没有文件级垃圾扫描结果",
                        tone = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (!hasAdvices) {
                item {
                    ReleaseBlockCard(availability = availability)
                }
            }

            // 建议清单 —— OneUiListSection + 每条用 OneUiListRow 风格的自定义行
            item {
                OneUiListSection {
                    plan.advices.forEachIndexed { index, advice ->
                        AdviceRow(
                            advice = advice,
                            checked = advice.key() in selectedKeys,
                            onToggle = { onToggle(advice.key()) },
                            showDivider = index < plan.advices.lastIndex,
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(OneUiSpacing.EmptyHeight + OneUiSpacing.BlockGap))
            }
        }

        BottomActionBar(
            availability = availability,
            hasAdvices = hasAdvices,
            moveToRecycleBin = moveToRecycleBin,
            onMoveToRecycleBinChange = onMoveToRecycleBinChange,
            onExecute = onExecute,
            onSelectSuggested = onSelectSuggested,
            onGrantUsage = onGrantUsage,
            onRescan = onRescan,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

// ============================================================
// Done —— 单 squircle card + hero + primary CTA + 副 CTA
// ============================================================

@Composable
private fun DoneBody(
    freedBytes: Long,
    succeededCount: Int,
    failedCount: Int,
    needsManualCount: Int,
    onDone: () -> Unit,
    onRescan: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors
    val manualOnly = freedBytes == 0L && succeededCount == 0 && needsManualCount > 0
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = OneUiSpacing.BlockGap,
            vertical = OneUiSpacing.SectionTitleGap,
        ),
        verticalArrangement = Arrangement.spacedBy(OneUiSpacing.BlockGap),
    ) {
        item {
            OneUiListSection {
                Column(modifier = Modifier.padding(OneUiSpacing.CardInner)) {
                    Text(
                        text = if (manualOnly) "已引导 $needsManualCount 项去系统设置页"
                        else "已释放 ${freedBytes.formatBytes()}",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (manualOnly) cs.onSurface else colors.healthGood,
                    )
                    Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
                    Text(
                        text = buildString {
                            if (manualOnly) {
                                append("Android 不允许第三方应用清理其他应用的缓存")
                            } else {
                                append("成功 $succeededCount 项")
                                if (failedCount > 0) append(" · 失败 $failedCount 项")
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                    )
                    if (needsManualCount > 0) {
                        Spacer(Modifier.height(OneUiSpacing.CardGap))
                        Text(
                            text = "另有 $needsManualCount 项需要你在系统设置页手动完成，" +
                                "这部分没有释放任何空间，也没有计入上面的数字。",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            PrimaryPillCta(text = "完成", enabled = true, onClick = onDone)
        }
        item {
            SecondaryPillCta(text = "重新扫描", onClick = onRescan)
        }
        item {
            Spacer(Modifier.height(OneUiSpacing.EmptyHeight))
        }
    }
}

// ============================================================
// RingChart —— OneUI 风格：圆环 + 中央数字 + 右侧文案
// ============================================================

@Composable
private fun RingChart(
    selected: Int,
    total: Int,
    selectedBytes: Long,
    totalBytes: Long,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val ringTrack = NovaCareTheme.colors.ringTrack
    val accent = cs.primary
    val ratio = if (total > 0) (selected.toFloat() / total).coerceIn(0f, 1f) else 0f
    val animatedRatio by animateFloatAsState(
        targetValue = ratio,
        animationSpec = tween(600),
        label = "cleanRing",
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(ChartSize),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = ChartStroke.toPx()
                val inset = strokeWidth / 2
                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                drawArc(
                    color = ringTrack,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedRatio,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$selected",
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurface,
                )
                Text(
                    text = "/ $total",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(OneUiSpacing.BlockGap))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "已选 ${selectedBytes.formatBytes()}",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
                color = cs.onSurface,
            )
            Spacer(Modifier.height(OneUiSpacing.CardGap))
            Text(
                text = "共可清 ${totalBytes.formatBytes()}",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

// ============================================================
// 复用件
// ============================================================

@Composable
private fun ChipRow(
    allSelected: Boolean,
    includeRisky: Boolean,
    onSelectAll: (Boolean) -> Unit,
    onIncludeRiskyChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChipToggle(
            text = if (allSelected) "全不选" else "全选",
            selected = false,
            onClick = { onSelectAll(!allSelected) },
        )
        Spacer(Modifier.width(OneUiSpacing.CardGap))
        ChipToggle(
            text = if (includeRisky) "✓ 含需确认" else "含需确认",
            selected = includeRisky,
            onClick = { onIncludeRiskyChange(!includeRisky) },
        )
    }
}

@Composable
private fun AdviceRow(
    advice: CleanAdvice,
    checked: Boolean,
    onToggle: () -> Unit,
    showDivider: Boolean,
) {
    val cs = MaterialTheme.colorScheme
    val tint = advice.risk.tintColor()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = OneUiSpacing.CardInner, vertical = OneUiSpacing.ListRowVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(OneUiSpacing.CardInner * 2 + OneUiSpacing.CardGap)
                .clip(RoundedCornerShape(OneUiRadius.Small))
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(OneUiSpacing.BlockGap - OneUiSpacing.SectionTitleGap),
            )
        }
        Spacer(Modifier.width(OneUiSpacing.CardInner))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = advice.targetLabel,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
                color = cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(OneUiSpacing.CardGap / 2))
            Text(
                text = advice.summary,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(OneUiSpacing.CardInner))
        Text(
            text = advice.recommendedBytes.formatBytes(),
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurface,
        )
        Spacer(Modifier.width(OneUiSpacing.CardInner))
        Box(
            modifier = Modifier
                .size(OneUiSpacing.CardInner * 2)
                .clip(CircleShape)
                .background(if (checked) cs.primary else cs.onSurface.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = cs.onPrimary,
                    modifier = Modifier.size(OneUiSpacing.CardInner + OneUiSpacing.CardGap),
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

@Composable
private fun ChipToggle(text: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(OneUiRadius.Pill))
            .clickable { onClick() },
        color = if (selected) cs.primary.copy(alpha = 0.12f)
        else cs.onSurface.copy(alpha = 0.06f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) cs.primary else cs.onSurface,
            modifier = Modifier.padding(
                horizontal = OneUiSpacing.CardInner,
                vertical = OneUiSpacing.SectionTitleGap,
            ),
        )
    }
}

@Composable
private fun NoticeBlock(text: String, tone: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OneUiRadius.Medium))
            .background(tone.copy(alpha = 0.08f))
            .padding(
                horizontal = OneUiSpacing.CardInner,
                vertical = OneUiSpacing.CardGap,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReleaseBlockCard(availability: ReleaseAvailability) {
    val caution = NovaCareTheme.colors.riskCaution
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OneUiRadius.Medium))
            .background(caution.copy(alpha = 0.08f))
            .padding(
                horizontal = OneUiSpacing.CardInner,
                vertical = OneUiSpacing.CardGap,
            ),
    ) {
        Text(
            text = availability.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
            color = caution,
        )
        availability.explain?.let { explain ->
            Spacer(Modifier.height(OneUiSpacing.CardGap / 2))
            Text(
                text = explain,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PrimaryPillCta(text: String, enabled: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(CtaHeight)
            .clip(RoundedCornerShape(OneUiRadius.Pill))
            .clickable(enabled = enabled) { onClick() },
        color = if (enabled) cs.primary else cs.onSurface.copy(alpha = 0.12f),
        contentColor = if (enabled) cs.onPrimary else cs.onSurface.copy(alpha = 0.38f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun SecondaryPillCta(text: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(CtaHeight)
            .clip(RoundedCornerShape(OneUiRadius.Pill))
            .clickable { onClick() },
        color = cs.onSurface.copy(alpha = 0.06f),
        contentColor = cs.primary,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun BottomActionBar(
    availability: ReleaseAvailability,
    hasAdvices: Boolean,
    moveToRecycleBin: Boolean,
    onMoveToRecycleBinChange: (Boolean) -> Unit,
    onExecute: () -> Unit,
    onSelectSuggested: () -> Unit,
    onGrantUsage: () -> Unit,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = cs.surface,
        shadowElevation = OneUiSpacing.CardGap,
    ) {
        Column(modifier = Modifier.padding(OneUiSpacing.CardInner)) {
            if (availability.explain != null && hasAdvices) {
                ReleaseBlockCard(availability = availability)
                Spacer(Modifier.height(OneUiSpacing.CardInner - OneUiSpacing.CardGap))
            }
            if (hasAdvices) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(OneUiRadius.Medium))
                        .clickable { onMoveToRecycleBinChange(!moveToRecycleBin) }
                        .padding(vertical = OneUiSpacing.SectionTitleGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(OneUiSpacing.BlockGap - OneUiSpacing.SectionTitleGap)
                            .clip(RoundedCornerShape(OneUiSpacing.SectionTitleGap / 2))
                            .background(
                                if (moveToRecycleBin) cs.primary
                                else cs.onSurface.copy(alpha = 0.12f),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (moveToRecycleBin) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = cs.onPrimary,
                                modifier = Modifier.size(OneUiSpacing.CardInner),
                            )
                        }
                    }
                    Spacer(Modifier.width(OneUiSpacing.SectionTitleGap))
                    Text(
                        text = "先移入回收站（7 天内可撤销）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurface,
                    )
                }
                Spacer(Modifier.height(OneUiSpacing.CardInner - OneUiSpacing.CardGap))
            }
            val actionable = availability.canRelease ||
                availability.action != ReleaseAction.NONE
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CtaHeight)
                    .clip(RoundedCornerShape(OneUiRadius.Pill))
                    .clickable {
                        when {
                            availability.canRelease -> onExecute()
                            availability.action == ReleaseAction.SELECT_SUGGESTED -> onSelectSuggested()
                            availability.action == ReleaseAction.GRANT_USAGE_STATS -> onGrantUsage()
                            availability.action == ReleaseAction.RESCAN -> onRescan()
                        }
                    },
                color = if (actionable) cs.primary
                else cs.onSurface.copy(alpha = 0.12f),
                contentColor = if (actionable) cs.onPrimary
                else cs.onSurface.copy(alpha = 0.38f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (availability.canRelease) availability.title
                        else availability.actionLabel ?: availability.title,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun CleanRisk.tintColor(): Color {
    val cs = MaterialTheme.colorScheme
    return when (this) {
        CleanRisk.SAFE -> cs.primary
        CleanRisk.CAUTION -> cs.tertiary
        CleanRisk.RISKY -> NovaCareTheme.colors.riskRisky
    }
}

// ============================================================
// 颜色 / 尺寸 —— 由 token 派生，0 自由 dp
// ============================================================

private object CleanTints {
    val idle = Color(0xFF2F6FED)
}

/** 圆环外径 = EmptyHeight - BlockGap = 88dp */
private val ChartSize: Dp = OneUiSpacing.EmptyHeight - OneUiSpacing.BlockGap

/** 圆环描边 = CardGap = 8dp */
private val ChartStroke: Dp = OneUiSpacing.CardGap

/** 主 CTA 高度 = CardInner*4 - CardGap = 56dp */
private val CtaHeight: Dp = OneUiSpacing.CardInner * 4 - OneUiSpacing.CardGap
