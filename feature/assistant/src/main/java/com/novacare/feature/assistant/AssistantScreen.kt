package com.novacare.feature.assistant

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.novacare.core.domain.AssistantOutcome
import com.novacare.ui.designsystem.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    rootPath: String,
    modifier: Modifier = Modifier,
    viewModel: AssistantViewModel = hiltViewModel(),
) {
    val turns by viewModel.turns.collectAsState()
    val busy by viewModel.busy.collectAsState()
    var text by remember { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text("AI 助手") }) },
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            Row {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("例如：清理半年没用的缓存，别动微信") },
                    singleLine = true,
                )
                Button(enabled = !busy, onClick = {
                    viewModel.submit(text, rootPath)
                    text = ""
                }) { Text("发送") }
            }

            if (turns.isEmpty()) {
                EmptyState("说出你的需求，AI 会把它拆成具体的清理 / 冻结动作（不会直接执行）")
            } else {
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(turns) { turn ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text("你：${turn.input}", style = MaterialTheme.typography.bodyMedium)
                            turn.intent?.let {
                                Text(
                                    "理解：${it.intent.name}（来源 ${it.source.name}，置信度 ${(it.confidence * 100).toInt()}%）",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                if (it.excludePackages.isNotEmpty()) {
                                    Text(
                                        "排除：${it.excludePackages.joinToString()}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            when (val outcome = turn.outcome) {
                                is AssistantOutcome.Plan -> Text(
                                    "${outcome.explanation}：${outcome.advices.size} 项，" +
                                        "共 ${outcome.advices.sumOf { a -> a.recommendedBytes }} 字节",
                                    style = MaterialTheme.typography.bodySmall,
                                )

                                is AssistantOutcome.Freeze -> Text(
                                    "${outcome.explanation}：${outcome.candidates.size} 个应用",
                                    style = MaterialTheme.typography.bodySmall,
                                )

                                is AssistantOutcome.Info -> Text(
                                    outcome.explanation,
                                    style = MaterialTheme.typography.bodySmall,
                                )

                                null -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
}
