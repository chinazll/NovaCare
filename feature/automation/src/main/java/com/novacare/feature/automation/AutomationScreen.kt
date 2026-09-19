package com.novacare.feature.automation

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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.novacare.ui.designsystem.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationScreen(
    modifier: Modifier = Modifier,
    viewModel: AutomationViewModel = hiltViewModel(),
) {
    val rules by viewModel.rules.collectAsState()
    val notice by viewModel.notice.collectAsState()
    var text by remember { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text("自动化") }) },
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("用自然语言建规则，如：每周日 3 点清理垃圾") },
                    singleLine = true,
                )
                Button(onClick = { viewModel.createFromText(text) }) {
                    Text("生成")
                }
            }

            notice?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            if (rules.isEmpty()) {
                EmptyState("还没有规则")
            } else {
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(rules, key = { it.id }) { rule ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(rule.name, style = MaterialTheme.typography.bodyLarge)
                                Text(rule.describe(), style = MaterialTheme.typography.bodySmall)
                                if (rule.createdByAi) {
                                    Text(
                                        "由自然语言生成",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            Switch(
                                checked = rule.enabled,
                                onCheckedChange = { viewModel.toggle(rule, it) },
                            )
                        }
                    }
                }
            }
        }
    }
}
