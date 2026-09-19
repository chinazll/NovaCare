package com.novacare.feature.freeze

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreezeScreen(
    rootPath: String,
    modifier: Modifier = Modifier,
    viewModel: FreezeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val results by viewModel.results.collectAsState()

    LaunchedEffect(rootPath) { viewModel.load(rootPath) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("冻结") }) },
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            when (val s = state) {
                FreezeViewModel.UiState.Loading -> EmptyState("扫描中…")
                is FreezeViewModel.UiState.Failed -> EmptyState(s.message)
                is FreezeViewModel.UiState.Ready -> {
                    if (!s.advancedMode) {
                        Text(
                            "普通模式：点击后会打开系统设置页，由你手动停用（官方路径，零风险）。" +
                                "开启高级模式并授权 Shizuku 可一键冻结。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (s.candidates.isEmpty()) {
                        EmptyState("没有发现不常用应用")
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                            items(s.candidates, key = { it.app.packageName }) { candidate ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(candidate.app.label, style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            candidate.reason,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            "占用 ${candidate.app.sizeBytes.formatBytes()}",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    Button(onClick = { viewModel.freeze(candidate.app.packageName) }) {
                                        Text("冻结")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            results.takeLast(3).forEach {
                Text(
                    "${it.packageName}: ${it.message}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
