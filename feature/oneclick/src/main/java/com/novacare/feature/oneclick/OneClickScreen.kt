package com.novacare.feature.oneclick

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novacare.core.domain.OneClickCheckUseCase
import com.novacare.ui.designsystem.NovaCareTheme
import com.novacare.ui.designsystem.NovaTap
import com.novacare.ui.designsystem.OneUiAppBar
import com.novacare.ui.designsystem.OneUiRadius
import com.novacare.ui.designsystem.OneUiSpacing

/**
 * 一键体检屏（v0.21.0 新增）
 *
 * UI 状态：
 *   - Idle：展示「开始体检」按钮
 *   - Running：5 步进度条
 *   - Done：总分（0~100 大数字）+ 5 步结果卡片 + 体检发现的问题清单 + 释放 / 冻结入口
 *
 * 按钮：
 *   - 「释放」→ onOpenClean（跳清理 tab）
 *   - 「冻结」→ onOpenFreeze（跳冻结 tab）
 *   - 「重新体检」→ runScan 再次执行
 */
@Composable
fun OneClickScreen(
    onOpenClean: () -> Unit,
    onOpenFreeze: () -> Unit,
    onBack: () -> Unit,
    rootPath: String = "/storage/emulated/0",
    modifier: Modifier = Modifier,
    viewModel: OneClickViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val view = LocalView.current

    LaunchedEffect(rootPath) {
        if (state is OneClickViewModel.UiState.Idle) {
            viewModel.runScan(rootPath)
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            OneUiAppBar(
                title = "一键体检",
                onBack = {
                    NovaTap(view)
                    onBack()
                },
            )

            when (val s = state) {
                OneClickViewModel.UiState.Idle -> IdleState(
                    onStart = { NovaTap(view); viewModel.runScan(rootPath) },
                )
                is OneClickViewModel.UiState.Running -> RunningState(
                    completed = s.completed,
                    total = s.total,
                )
                is OneClickViewModel.UiState.Done -> DoneState(
                    report = s.report,
                    onOpenClean = { NovaTap(view); onOpenClean() },
                    onOpenFreeze = { NovaTap(view); onOpenFreeze() },
                    onRestart = { NovaTap(view); viewModel.runScan(rootPath) },
                )
                is OneClickViewModel.UiState.Failed -> FailedState(
                    message = s.message,
                    onRetry = { NovaTap(view); viewModel.runScan(rootPath) },
                )
            }
        }
    }
}

@Composable
private fun IdleState(onStart: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = OneUiSpacing.BlockGap),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(OneUiRadius.Large),
            color = cs.surfaceContainer,
        ) {
            Column(
                modifier = Modifier.padding(OneUiSpacing.CardInner),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(OneUiSpacing.CardInner))
                Text(
                    text = "5 步聚合扫描",
                    style = MaterialTheme.typography.titleLarge,
                    color = cs.onSurface,
                )
                Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
                Text(
                    text = "存储 / 内存 / 电池 / 屏幕时长 / 流量。\n完成后给出一份体检报告与可执行的下一步。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
                Spacer(Modifier.height(OneUiSpacing.CardInner))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(OneUiRadius.Medium)),
                    color = cs.primary,
                    contentColor = cs.onPrimary,
                    onClick = onStart,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "开始体检",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W600),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RunningState(completed: Int, total: Int) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors
    val progress = if (total > 0) completed.toFloat() / total else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = OneUiSpacing.BlockGap),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(OneUiRadius.Large),
            color = cs.surfaceContainer,
        ) {
            Column(
                modifier = Modifier.padding(OneUiSpacing.CardInner),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(56.dp),
                    strokeWidth = 4.dp,
                    color = colors.healthGood,
                    progress = { progress.coerceIn(0f, 1f) },
                )
                Spacer(Modifier.height(OneUiSpacing.CardInner))
                Text(
                    text = "正在扫描…",
                    style = MaterialTheme.typography.titleLarge,
                    color = cs.onSurface,
                )
                Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
                Text(
                    text = "已完成 $completed / $total",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
                Spacer(Modifier.height(OneUiSpacing.CardInner))
                // 5 步指示
                StepIndicator(
                    labels = listOf("存储", "内存", "电池", "屏幕时长", "流量"),
                    completedCount = completed,
                )
            }
        }
    }
}

@Composable
private fun StepIndicator(labels: List<String>, completedCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        labels.forEachIndexed { index, label ->
            val done = index < completedCount
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (done) NovaCareTheme.colors.healthGood else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.24f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp),
                        )
                    } else {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DoneState(
    report: OneClickCheckUseCase.Report,
    onOpenClean: () -> Unit,
    onOpenFreeze: () -> Unit,
    onRestart: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors
    val tone = when {
        report.score >= 80 -> colors.healthGood
        report.score >= 50 -> colors.healthFair
        else -> colors.healthPoor
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = OneUiSpacing.BlockGap, vertical = OneUiSpacing.CardGap),
    ) {
        item {
            // 总分大卡
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(OneUiRadius.Large),
                color = cs.surfaceContainer,
            ) {
                Column(
                    modifier = Modifier.padding(OneUiSpacing.CardInner),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "体检得分",
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
                    Text(
                        text = "${report.score}",
                        style = MaterialTheme.typography.displayLarge,
                        color = tone,
                    )
                    Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
                    Text(
                        text = scoreHint(report.score),
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
                    // 释放 / 冻结 入口（按 issue 类型显示）
                    val hasClean = report.issues.any {
                        it.targetRoute == OneClickCheckUseCase.IssueTarget.CLEAN
                    }
                    val hasFreeze = report.issues.any {
                        it.targetRoute == OneClickCheckUseCase.IssueTarget.FREEZE
                    }
                    if (hasClean || hasFreeze) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (hasClean) {
                                ActionButton(
                                    text = "去释放",
                                    icon = Icons.Outlined.CleaningServices,
                                    onClick = onOpenClean,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (hasFreeze) {
                                ActionButton(
                                    text = "去冻结",
                                    icon = Icons.Outlined.Schedule,
                                    onClick = onOpenFreeze,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
                    TextButton(onClick = onRestart, contentPadding = PaddingValues(horizontal = 0.dp)) {
                        Text("重新体检")
                    }
                }
            }
            Spacer(Modifier.height(OneUiSpacing.BlockGap))
        }

        item {
            Text(
                text = "分项结果",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
            Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
        }

        items(items = report.results, key = { it.step.name }) { result ->
            StepResultCard(result = result)
            Spacer(Modifier.height(OneUiSpacing.CardGap))
        }

        if (report.issues.isNotEmpty()) {
            item {
                Spacer(Modifier.height(OneUiSpacing.BlockGap))
                Text(
                    text = "体检发现",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
                Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
            }
            items(items = report.issues) { issue ->
                IssueCard(issue = issue)
                Spacer(Modifier.height(OneUiSpacing.CardGap))
            }
        }

        item {
            Spacer(Modifier.height(OneUiSpacing.EmptyHeight))
        }
    }
}

@Composable
private fun StepResultCard(result: OneClickCheckUseCase.StepResult) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors
    val score = result.healthScore
    val tone = when {
        score == null -> cs.onSurfaceVariant
        score >= 80 -> colors.healthGood
        score >= 50 -> colors.healthFair
        else -> colors.healthPoor
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(OneUiRadius.Large),
        color = cs.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(OneUiSpacing.CardInner)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = stepIcon(result.step),
                    contentDescription = null,
                    tint = tone,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stepLabel(result.step),
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (result.healthScore != null) {
                    Text(
                        text = "${result.healthScore}",
                        style = MaterialTheme.typography.titleMedium,
                        color = tone,
                    )
                } else {
                    Text(
                        text = "—",
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = result.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
            )
            if (result.suggestion != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "建议：${result.suggestion}",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun IssueCard(issue: OneClickCheckUseCase.Issue) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(OneUiRadius.Large),
        color = colors.riskCaution.copy(alpha = 0.08f),
    ) {
        Column(modifier = Modifier.padding(OneUiSpacing.CardInner)) {
            Text(
                text = issue.title,
                style = MaterialTheme.typography.titleMedium,
                color = cs.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = issue.why,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(OneUiRadius.Medium),
        color = cs.primary,
        contentColor = cs.onPrimary,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W600))
        }
    }
}

@Composable
private fun FailedState(message: String, onRetry: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = OneUiSpacing.BlockGap),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(OneUiRadius.Large),
            color = cs.errorContainer.copy(alpha = 0.4f),
        ) {
            Column(modifier = Modifier.padding(OneUiSpacing.CardInner)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = cs.error,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "体检失败",
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onSurface,
                    )
                }
                Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
                Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
                TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Text("重试")
                }
            }
        }
    }
}

private fun stepIcon(step: OneClickCheckUseCase.Step): ImageVector = when (step) {
    OneClickCheckUseCase.Step.STORAGE -> Icons.Outlined.Storage
    OneClickCheckUseCase.Step.MEMORY -> Icons.Outlined.Memory
    OneClickCheckUseCase.Step.BATTERY -> Icons.Outlined.BatteryStd
    OneClickCheckUseCase.Step.SCREEN_TIME -> Icons.Outlined.Schedule
    OneClickCheckUseCase.Step.TRAFFIC -> Icons.Outlined.SwapVert
}

private fun stepLabel(step: OneClickCheckUseCase.Step): String = when (step) {
    OneClickCheckUseCase.Step.STORAGE -> "存储"
    OneClickCheckUseCase.Step.MEMORY -> "内存"
    OneClickCheckUseCase.Step.BATTERY -> "电池"
    OneClickCheckUseCase.Step.SCREEN_TIME -> "屏幕时长"
    OneClickCheckUseCase.Step.TRAFFIC -> "流量"
}

private fun scoreHint(score: Int): String = when {
    score >= 80 -> "状态良好。继续保持。"
    score >= 50 -> "存在可优化项，已在上方列出。"
    else -> "多项指标偏低，建议按上方列表逐项处理。"
}
