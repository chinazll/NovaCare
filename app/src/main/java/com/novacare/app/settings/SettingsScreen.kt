package com.novacare.app.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import com.novacare.core.model.CloudModel
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacare.core.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
) : ViewModel() {

    val settings = repository.settings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000),
        SettingsRepository.Settings(),
    )

    fun setAdvanced(enabled: Boolean) = viewModelScope.launch { repository.setAdvancedMode(enabled) }
    fun setCloudAi(enabled: Boolean) = viewModelScope.launch { repository.setCloudAi(enabled) }
    fun setModel(model: CloudModel) = viewModelScope.launch { repository.setCloudModel(model) }
    fun setApiKey(key: String) = viewModelScope.launch { repository.setCloudApiKey(key) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    var apiKey by remember(settings.cloudApiKey) { mutableStateOf(settings.cloudApiKey) }

    Scaffold(topBar = { TopAppBar(title = { Text("设置") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("高级模式", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "开启后可使用 Shizuku 冻结 / 清缓存等系统级能力",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = settings.advancedMode, onCheckedChange = viewModel::setAdvanced)
            }

            Row(
                modifier = Modifier.padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("云端 AI（默认关闭）", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "关闭时 App 不会发起任何网络请求；开启后才会使用你填写的模型",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = settings.cloudAiEnabled, onCheckedChange = viewModel::setCloudAi)
            }

            if (settings.cloudAiEnabled) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    OutlinedTextField(
                        value = settings.cloudModel.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("云端模型") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        CloudModel.entries.forEach { model ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        model.displayName +
                                            if (model.isChinaCompliant) "（国内已备案）" else "（境外）",
                                    )
                                },
                                onClick = {
                                    viewModel.setModel(model)
                                    expanded = false
                                },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        viewModel.setApiKey(it)
                    },
                    label = { Text("API Key（仅保存在本机）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
    }
}
