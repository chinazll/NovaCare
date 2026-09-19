package com.novacare.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.novacare.core.common.formatBytes
import com.novacare.ui.designsystem.EmptyState
import com.novacare.ui.designsystem.HealthRing
import com.novacare.ui.designsystem.PrimaryAction
import com.novacare.ui.designsystem.RiskChip
import com.novacare.ui.designsystem.StatCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val score by viewModel.score.collectAsState()
    val summary by viewModel.summary.collectAsState()
    val selected by viewModel.selected.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("NovaCare") }) },
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            score?.let { HealthRing(score = it.total, verdict = it.verdict) }
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatCard(
                    title = "清理",
                    value = "垃圾",
                    subtitle = "内核扫描",
                    onClick = { onNavigate("clean") },
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    title = "冻结",
                    value = "应用",
                    subtitle = "不常用",
                    onClick = { onNavigate("freeze") },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatCard(
                    title = "自动化",
                    value = "规则",
                    subtitle = "定时执行",
                    onClick = { onNavigate("automation") },
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    title = "助手",
                    value = "对话",
                    subtitle = "说人话",
                    onClick = { onNavigate("assistant") },
                    modifier = Modifier.weight(1f),
                )
            }

            when (val s = state) {
                is HomeViewModel.UiState.Idle ->
                    PrimaryAction(text = "智能优化", onClick = viewModel::onOptimizeClick)

                HomeViewModel.UiState.Scanning ->
                    PrimaryAction(text = "扫描中…", enabled = false, loading = true, onClick = {})

                is HomeViewModel.UiState.Preview -> {
                    if (s.plan.advices.isEmpty()) {
                        EmptyState("没有发现可安全清理的项目")
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f, fill = false),
                            contentPadding = PaddingValues(vertical = 8.dp),
                        ) {
                            items(s.plan.advices, key = { it.targetPath ?: (it.targetPackage ?: it.targetLabel) }) { advice ->
                                val key = advice.targetPath ?: ("pkg:" + advice.targetPackage.orEmpty())
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = key in selected,
                                        onCheckedChange = { viewModel.toggle(key) },
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(advice.summary, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            advice.reason,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    RiskChip(advice.risk)
                                }
                            }
                        }
                        PrimaryAction(
                            text = "执行（释放 ${s.plan.totalReclaimableBytes.formatBytes()}）",
                            onClick = viewModel::onConfirm,
                        )
                    }
                }

                HomeViewModel.UiState.Executing ->
                    PrimaryAction(text = "执行中…", enabled = false, loading = true, onClick = {})

                is HomeViewModel.UiState.Done -> {
                    Text(
                        text = "已释放 ${s.result.freedBytes.formatBytes()}，成功 ${s.result.succeeded.size} 项，" +
                            "失败 ${s.result.failed.size} 项",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (s.result.recycleBinPath != null) {
                        Text(
                            text = "已移入回收站，7 天内可撤销",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    PrimaryAction(text = "完成", onClick = viewModel::reset)
                }

                is HomeViewModel.UiState.Error ->
                    EmptyState(s.message)
            }
        }
    }
}
