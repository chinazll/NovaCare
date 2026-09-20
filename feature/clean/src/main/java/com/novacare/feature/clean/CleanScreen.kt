package com.novacare.feature.clean

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novacare.core.common.formatBytes
import com.novacare.core.domain.key
import com.novacare.core.model.CleanAdvice
import com.novacare.core.model.CleanRisk
import com.novacare.ui.designsystem.NovaCareTheme
import com.novacare.ui.designsystem.NovaSuccess
import com.novacare.ui.designsystem.NovaTap
import com.novacare.ui.designsystem.NovaToggle

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

    LaunchedEffect(Unit) {
        if (state is CleanViewModel.UiState.Idle) {
            viewModel.scanNow(rootPath)
        }
    }

    val view = LocalView.current

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
                )

                CleanViewModel.UiState.Executing -> ExecutingHero()

                is CleanViewModel.UiState.Done -> DoneHero(
                    freedBytes = s.result.freedBytes,
                    succeededCount = s.result.succeeded.size,
                    failedCount = s.result.failed.size,
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
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Text(
            text = "清理",
            style = MaterialTheme.typography.displaySmall,
            color = cs.onSurface,
        )
        Spacer(Modifier.height(8.dp))
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
                .clip(RoundedCornerShape(20.dp)),
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
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Text(
            text = "正在扫描",
            style = MaterialTheme.typography.displaySmall,
            color = cs.onSurface,
        )
        Spacer(Modifier.height(8.dp))
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
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Text(
            text = "扫描失败",
            style = MaterialTheme.typography.displaySmall,
            color = cs.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = cs.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(20.dp)),
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
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Text(
            text = "正在释放",
            style = MaterialTheme.typography.displaySmall,
            color = cs.onSurface,
        )
        Spacer(Modifier.height(8.dp))
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
    onDone: () -> Unit,
    onRescan: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Text(
            text = "已释放 ${freedBytes.formatBytes()}",
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.W300),
            color = colors.healthGood,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = buildString {
                append("成功 $succeededCount 项")
                if (failedCount > 0) append(" · 失败 $failedCount 项")
            },
            style = MaterialTheme.typography.bodyLarge,
            color = cs.onSurfaceVariant,
        )
        Spacer(Modifier.height(48.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(20.dp)),
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
                .clip(RoundedCornerShape(14.dp))
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
    selected: Set<String>,
    includeRisky: Boolean,
    moveToRecycleBin: Boolean,
    onToggle: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onIncludeRiskyChange: (Boolean) -> Unit,
    onMoveToRecycleBinChange: (Boolean) -> Unit,
    onExecute: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val view = LocalView.current
    val selectedBytes = plan.advices
        .filter { it.key() in selected }
        .sumOf { it.recommendedBytes }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "清理",
                    style = MaterialTheme.typography.displaySmall,
                    color = cs.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${selected.size} / ${plan.advices.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(24.dp))

            RingSummary(
                selected = selected.size,
                total = plan.advices.size,
                selectedBytes = selectedBytes,
                totalBytes = plan.totalReclaimableBytes,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Chip(
                    text = if (selected.size == plan.advices.size) "全不选" else "全选",
                    onClick = { onSelectAll(selected.size != plan.advices.size) },
                )
                Spacer(Modifier.width(8.dp))
                Chip(
                    text = if (includeRisky) "✓ 含需确认" else "含需确认",
                    selected = includeRisky,
                    onClick = { onIncludeRiskyChange(!includeRisky) },
                )
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
                        checked = advice.key() in selected,
                        onToggle = { onToggle(advice.key()) },
                    )
                    HorizontalDivider()
                }
                item {
                    Spacer(Modifier.height(120.dp))
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            color = cs.surface,
            shadowElevation = 8.dp,
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
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
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "先移入回收站（7 天内可撤销）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurface,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(20.dp)),
                    color = if (selected.isNotEmpty()) cs.primary else cs.onSurface.copy(alpha = 0.12f),
                    contentColor = if (selected.isNotEmpty()) cs.onPrimary else cs.onSurface.copy(alpha = 0.38f),
                    onClick = {
                        if (selected.isNotEmpty()) {
                            onExecute()
                        }
                    },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (selected.isEmpty()) "请选择要清理的项目"
                                   else "释放 ${selectedBytes.formatBytes()}",
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
    val ratio = if (total > 0) selected.toFloat() / total else 0f
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
            .clip(RoundedCornerShape(14.dp))
            .clickable { onToggle() }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(14.dp))
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
