package com.novacare.feature.clean

import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.novacare.core.common.formatBytes
import com.novacare.ui.designsystem.EmptyState
import com.novacare.ui.designsystem.PrimaryAction
import com.novacare.ui.designsystem.RiskChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanScreen(
    rootPath: String,
    modifier: Modifier = Modifier,
    viewModel: CleanViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val includeRisky by viewModel.includeRisky.collectAsState()
    val result by viewModel.lastResult.collectAsState()

    LaunchedEffect(rootPath) { viewModel.load(rootPath) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("清理") }) },
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("包含风险项", modifier = Modifier.weight(1f))
                Switch(checked = includeRisky, onCheckedChange = viewModel::setIncludeRisky)
            }

            when (val s = state) {
                CleanViewModel.UiState.Loading -> EmptyState("扫描中…")
                is CleanViewModel.UiState.Failed -> EmptyState(s.message)
                is CleanViewModel.UiState.Ready -> {
                    if (!s.engineAvailable) {
                        Text(
                            "Rust 引擎不可用：不会展示任何估算数据",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (s.plan.advices.isEmpty()) {
                        EmptyState("没有可清理项")
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                            items(s.plan.advices) { advice ->
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
                                        Text(advice.summary)
                                        Text(
                                            advice.reason,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        if (advice.costNote.isNotBlank()) {
                                            Text(
                                                advice.costNote,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    }
                                    RiskChip(advice.risk)
                                }
                            }
                        }
                        PrimaryAction(
                            text = "清理 ${s.plan.totalReclaimableBytes.formatBytes()}",
                            onClick = viewModel::execute,
                        )
                    }
                }
            }

            result?.let {
                Text(
                    "完成：释放 ${it.freedBytes.formatBytes()}，失败 ${it.failed.size} 项",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
