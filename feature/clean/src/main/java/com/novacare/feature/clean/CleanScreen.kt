package com.novacare.feature.clean

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.novacare.ui.designsystem.GlassPanel
import com.novacare.ui.designsystem.MotionTokens
import com.novacare.ui.designsystem.NovaCareTheme
import com.novacare.ui.designsystem.NovaSuccess
import com.novacare.ui.designsystem.NovaTap
import com.novacare.ui.designsystem.NovaToggle
import com.novacare.ui.designsystem.OneUiRadius
import com.novacare.ui.designsystem.OneUiSpacing

/**
 * 清理页 —— OneUI 9.5 真实设计语言。
 *
 * 单一主张 + 可控细节。每屏一个主 CTA（释放），其他都是 inline 操作。
 */
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
        if (state is CleanViewModel.UiState.Idle) {
            viewModel.scanNow(rootPath)
        }
    }

    val view = LocalView.current

    // 从「使用情况访问」设置页返回时 Compose 不会自动重组，
    // 必须回前台检测一次，否则用户刚授完权仍看到"需要使用情况访问权限"。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, rootPath) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onResume(rootPath)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 一次性提示必须有出口 —— 否则「点了释放没反应」这类静默失败会再次发生
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
            Spacer(Modifier.height(56.dp))

            when (val s = state) {
                CleanViewModel.UiState.Idle -> IdleHero(
                    onScan = {
                        NovaTap(view)
                        viewModel.scanNow(rootPath, force = true)
                    },
                )

                CleanViewModel.UiState.Scanning -> ScanningHero()

                is CleanViewModel.UiState.Failed -> FailedHero(
                    message = s.message,
                    onRetry = {
                        NovaTap(view)
                        viewModel.scanNow(rootPath, force = true)
                    },
                )

                is CleanViewModel.UiState.Results -> ResultsView(
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

                CleanViewModel.UiState.Executing -> ExecutingHero()

                is CleanViewModel.UiState.Done -> DoneHero(
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

@Composable
private fun IdleHero(onScan: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = Modifier.padding(horizontal = OneUiSpacing.BlockGap)) {
        Text(
            text = "清理",
            style = MaterialTheme.typography.displaySmall,
            color = cs.onSurface,
        )
        Spacer(Modifier.height(OneUiSpacing.CardGap))
        Text(
            text = "扫一下，看看你能释放多少空间",
            style = MaterialTheme.typography.bodyLarge,
            color = cs.onSurfaceVariant,
        )
        Spacer(Modifier.height(48.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(OneUiRadius.Large)),
            color = cs.primary,
            contentColor = cs.onPrimary,
            onClick = onScan,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "开始扫描",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W600),
                )
            }
        }
    }
}

@Composable
private fun ScanningHero() {
    val cs = MaterialTheme.colorScheme
    Column(modifier = Modifier.padding(horizontal = OneUiSpacing.BlockGap)) {
        Text(
            text = "正在扫描",
            style = MaterialTheme.typography.displaySmall,
            color = cs.onSurface,
        )
        Spacer(Modifier.height(OneUiSpacing.CardGap))
        Text(
            text = "分析存储、缓存与残留文件",
            style = MaterialTheme.typography.bodyLarge,
            color = cs.onSurfaceVariant,
        )
        Spacer(Modifier.height(40.dp))
        androidx.compose.material3.CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            strokeWidth = 3.dp,
            color = cs.primary,
        )
    }
}

@Composable
private fun FailedHero(message: String, onRetry: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = Modifier.padding(horizontal = OneUiSpacing.BlockGap)) {
        Text(
            text = "扫描失败",
            style = MaterialTheme.typography.displaySmall,
            color = cs.onSurface,
        )
        Spacer(Modifier.height(OneUiSpacing.CardGap))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = cs.onSurfaceVariant,
        )
        Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(OneUiRadius.Large)),
            color = cs.primary,
            contentColor = cs.onPrimary,
            onClick = onRetry,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "重试",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W600),
                )
            }
        }
    }
}

@Composable
private fun ExecutingHero() {
    val cs = MaterialTheme.colorScheme
    Column(modifier = Modifier.padding(horizontal = OneUiSpacing.BlockGap)) {
        Text(
            text = "正在释放",
            style = MaterialTheme.typography.displaySmall,
            color = cs.onSurface,
        )
        Spacer(Modifier.height(OneUiSpacing.CardGap))
        Text(
            text = "请保持 NovaCare 在前台",
            style = MaterialTheme.typography.bodyLarge,
            color = cs.onSurfaceVariant,
        )
        Spacer(Modifier.height(40.dp))
        androidx.compose.material3.CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            strokeWidth = 3.dp,
            color = cs.primary,
        )
    }
}

@Composable
private fun DoneHero(
    freedBytes: Long,
    succeededCount: Int,
    failedCount: Int,
    needsManualCount: Int,
    onDone: () -> Unit,
    onRescan: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors
    // 一项都没真正释放、只是把用户引导去了系统设置页时，
    // 不能用「已释放 0 B」冒充清理过 —— 那是「点了释放像没生效」的观感来源。
    val manualOnly = freedBytes == 0L && succeededCount == 0 && needsManualCount > 0
    Column(modifier = Modifier.padding(horizontal = OneUiSpacing.BlockGap)) {
        Text(
            text = if (manualOnly) {
                "已引导 $needsManualCount 项去系统设置页"
            } else {
                "已释放 ${freedBytes.formatBytes()}"
            },
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.W300),
            color = if (manualOnly) cs.onSurface else colors.healthGood,
        )
        Spacer(Modifier.height(OneUiSpacing.CardGap))
        Text(
            text = buildString {
                if (manualOnly) {
                    append("Android 不允许第三方应用清理其他应用的缓存")
                } else {
                    append("成功 $succeededCount 项")
                    if (failedCount > 0) append(" · 失败 $failedCount 项")
                }
            },
            style = MaterialTheme.typography.bodyLarge,
            color = cs.onSurfaceVariant,
        )
        if (needsManualCount > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "另有 $needsManualCount 项需要你在系统设置页手动完成，" +
                    "这部分没有释放任何空间，也没有计入上面的数字。",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(48.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(OneUiRadius.Large)),
            color = cs.primary,
            contentColor = cs.onPrimary,
            onClick = onDone,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "完成",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W600),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(OneUiRadius.Medium))
                .clickable { onRescan() }
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "重新扫描",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W500),
                color = cs.primary,
            )
        }
    }
}

@Composable
private fun ResultsView(
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
    val view = LocalView.current
    val hasAdvices = plan.advices.isNotEmpty()
    // 只统计清单里真实存在的键，避免「已选 X / Y」虚高、环形图比例越界
    val selectedKeys = selected.filter { key -> plan.advices.any { it.key() == key } }.toSet()
    val selectedBytes = plan.advices
        .filter { it.key() in selectedKeys }
        .sumOf { it.recommendedBytes }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OneUiSpacing.BlockGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "清理",
                    style = MaterialTheme.typography.displaySmall,
                    color = cs.onSurface,
                    modifier = Modifier.weight(1f),
                )
                AnimatedContent(
                    targetState = selectedKeys.size to plan.advices.size,
                    transitionSpec = {
                        ContentTransform(
                            targetContentEnter = fadeIn(MotionTokens.standard) +
                                slideInVertically(MotionTokens.standardOffset) { it / 4 },
                            initialContentExit = fadeOut(tween(durationMillis = 120)),
                        )
                    },
                    label = "selection-count",
                ) { (sel, total) ->
                    Text(
                        text = "$sel / $total",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(OneUiSpacing.BlockGap))

            RingSummary(
                selected = selectedKeys.size,
                total = plan.advices.size,
                selectedBytes = selectedBytes,
                totalBytes = plan.totalReclaimableBytes,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OneUiSpacing.BlockGap),
            )

            Spacer(Modifier.height(OneUiSpacing.CardInner))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OneUiSpacing.BlockGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                key("chip-all") {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(MotionTokens.standard) +
                            slideInVertically(MotionTokens.standardOffset) { it / 6 },
                    ) {
                        Chip(
                            text = if (selectedKeys.size == plan.advices.size) "全不选" else "全选",
                            onClick = { onSelectAll(selectedKeys.size != plan.advices.size) },
                        )
                    }
                }
                Spacer(Modifier.width(OneUiSpacing.CardGap))
                key("chip-risky") {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(MotionTokens.standard) +
                            slideInVertically(MotionTokens.standardOffset) { it / 6 },
                    ) {
                        Chip(
                            text = if (includeRisky) "✓ 含需确认" else "含需确认",
                            selected = includeRisky,
                            onClick = { onIncludeRiskyChange(!includeRisky) },
                        )
                    }
                }
            }

            if (!engineAvailable && hasAdvices) {
                Spacer(Modifier.height(12.dp))
                NoticeRow(
                    text = "清理内核不可用：以下只有应用缓存分析，没有文件级垃圾扫描结果",
                )
            }

            if (!hasAdvices) {
                Spacer(Modifier.height(12.dp))
                // 清单为空时，原因必须出现在用户眼睛正在看的位置，而不是只藏在底部
                ReleaseBlockCard(availability = availability)
            }

            Spacer(Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                items(items = plan.advices, key = { it.key() }) { advice ->
                    AdviceRow(
                        advice = advice,
                        checked = advice.key() in selectedKeys,
                        onToggle = { onToggle(advice.key()) },
                    )
                    HorizontalDivider()
                }
                item {
                    Spacer(Modifier.height(120.dp))
                }
            }
        }

        GlassPanel(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                // 可释放时不占版面；不可释放时必须把「为什么 + 怎么办」写在按钮上方
                if (availability.explain != null && hasAdvices) {
                    ReleaseBlockCard(availability = availability)
                    Spacer(Modifier.height(12.dp))
                }
                if (hasAdvices) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(OneUiRadius.Medium))
                            .clickable { onMoveToRecycleBinChange(!moveToRecycleBin) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (moveToRecycleBin) cs.primary else cs.onSurface.copy(alpha = 0.12f),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (moveToRecycleBin) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = cs.onPrimary,
                                    modifier = Modifier.size(14.dp),
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
                    Spacer(Modifier.height(12.dp))
                }
                // 有下一步动作时按钮必须是「可点的样子」——
                // 灰按钮 + 可点击本身就是最容易误导人的组合（用户会直接判定为坏了）
                val actionable = availability.canRelease ||
                    availability.action != ReleaseAction.NONE
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(OneUiRadius.Large)),
                    color = if (actionable) cs.primary else cs.onSurface.copy(alpha = 0.12f),
                    contentColor = if (actionable) cs.onPrimary else cs.onSurface.copy(alpha = 0.38f),
                    onClick = {
                        // 不可用也不吞点击：把用户带到下一步（勾选项 / 去授权 / 重扫）
                        when {
                            availability.canRelease -> {
                                NovaSuccess(view.context)
                                onExecute()
                            }

                            availability.action == ReleaseAction.SELECT_SUGGESTED -> {
                                NovaTap(view)
                                onSelectSuggested()
                            }

                            availability.action == ReleaseAction.GRANT_USAGE_STATS -> {
                                NovaTap(view)
                                onGrantUsage()
                            }

                            availability.action == ReleaseAction.RESCAN -> {
                                NovaTap(view)
                                onRescan()
                            }
                        }
                    },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        // 可释放时按钮就是「释放 X」；不可释放时按钮变成**下一步动作**
                        // （去授权 / 选择建议的 N 项 / 重新扫描），而不是一句让人无从下手的废话
                        Text(
                            text = if (availability.canRelease) {
                                availability.title
                            } else {
                                availability.actionLabel ?: availability.title
                            },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W600),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RingSummary(
    selected: Int,
    total: Int,
    selectedBytes: Long,
    totalBytes: Long,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val accent = cs.primary
    val track = cs.onSurface.copy(alpha = 0.08f)
    val ratio = if (total > 0) (selected.toFloat() / total).coerceIn(0f, 1f) else 0f
    val animatedRatio by animateFloatAsState(
        targetValue = ratio,
        animationSpec = tween(600),
        label = "ring",
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(112.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 8.dp.toPx()
                val inset = strokeWidth / 2
                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                val topLeft = Offset(inset, inset)
                drawArc(
                    color = track,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedRatio,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$selected",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.W600),
                    color = cs.onSurface,
                )
                Text(
                    text = "/ $total",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "已选 ${selectedBytes.formatBytes()}",
                style = MaterialTheme.typography.titleSmall,
                color = cs.onSurface,
            )
            Text(
                text = "共可清 ${totalBytes.formatBytes()}",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AdviceRow(
    advice: CleanAdvice,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OneUiRadius.Medium))
            .clickable { onToggle() }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(OneUiRadius.Medium))
                .background(advice.risk.tintColor().copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = advice.risk.icon(),
                contentDescription = null,
                tint = advice.risk.tintColor(),
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = advice.targetLabel,
                style = MaterialTheme.typography.titleSmall,
                color = cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = advice.summary,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = advice.recommendedBytes.formatBytes(),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
            color = cs.onSurface,
        )
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (checked) cs.primary else cs.onSurface.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = cs.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun HorizontalDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
    )
}

/**
 * 「释放」不可用时的原因卡。
 *
 * 事故复盘（本次 P0）：过去这里只有一句「请选择要清理的项目」+ 一个灰按钮，
 * 用户看到扫描有结果却点不动，页面上没有任何一句话说明到底缺什么。
 * 现在必须同时给出：**发生了什么** + **下一步干什么**。
 */
@Composable
private fun ReleaseBlockCard(availability: ReleaseAvailability) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OneUiRadius.Medium))
            .background(colors.riskCaution.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = availability.title,
            style = MaterialTheme.typography.titleSmall,
            color = colors.riskCaution,
        )
        availability.explain?.let { explain ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = explain,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

/** 中性提示行（如实说明能力降级，不给虚假操作入口） */
@Composable
private fun NoticeRow(text: String) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OneUiRadius.Medium))
            .background(cs.onSurface.copy(alpha = 0.04f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
        )
    }
}

@Composable
private fun Chip(
    text: String,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(9999.dp))
            .clickable { onClick() },
        color = if (selected) cs.primary.copy(alpha = 0.12f) else cs.onSurface.copy(alpha = 0.06f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) cs.primary else cs.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
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

private fun CleanRisk.icon(): ImageVector = Icons.Outlined.WarningAmber

// (end of file)
