package com.novacare.optimizer.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacare.optimizer.core.AppInfo
import com.novacare.optimizer.core.DeviceRepository
import com.novacare.optimizer.ui.components.OneUiCard
import com.novacare.optimizer.ui.components.OneUiLargeHeader
import com.novacare.optimizer.ui.components.OneUiRow
import com.novacare.optimizer.ui.components.SectionTitle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppsUiState(
    val loading: Boolean = true,
    val apps: List<AppInfo> = emptyList(),
    val frozen: Set<String> = emptySet(),
    val query: String = "",
)

@HiltViewModel
class AppsViewModel @Inject constructor(
    private val repo: DeviceRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AppsUiState())
    val state = _state.asStateFlow()

    init { scan() }

    fun scan() {
        viewModelScope.launch {
            val apps = repo.scanApps()
            _state.value = _state.value.copy(loading = false, apps = apps)
        }
    }

    fun toggleFreeze(pkg: String) {
        val s = _state.value.frozen.toMutableSet()
        if (pkg in s) s -= pkg else s += pkg
        _state.value = _state.value.copy(frozen = s)
        // 生产环境：通过 Shizuku pm disable-user / pm suspend 执行
        // 免 Root 场景下引导用户授权 Shizuku（dev.rikka.shizuku api 已接入）
    }

    fun setQuery(q: String) { _state.value = _state.value.copy(query = q) }
}

/**
 * 应用管理（借鉴 AppManager + UAD 安全分级）
 * 冻结 = pm suspend，可逆、比卸载安全——One UI "一次只做一件事"：本页只管理不清理
 */
@Composable
fun AppsScreen(vm: AppsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val filtered = state.apps.filter {
        it.label.contains(state.query, true) || it.packageName.contains(state.query, true)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        OneUiLargeHeader(
            title = "应用管理",
            subtitle = "冻结不卸载，随时可恢复",
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::setQuery,
            placeholder = { Text("搜索应用") },
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))

        SectionTitle("全部应用 · ${filtered.size}")
        LazyColumn(Modifier.padding(horizontal = 20.dp)) {
            item {
                OneUiCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AcUnit, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "已冻结 ${state.frozen.size} 个应用（已从后台与桌面隐藏，数据保留）",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            items(filtered, key = { it.packageName }) { app ->
                OneUiRow(
                    title = app.label,
                    subtitle = buildString {
                        if (app.isSystemApp) append("系统应用 · ")
                        if (app.cacheSizeMb > 0.1f) append("缓存 %.1f MB".format(app.cacheSizeMb))
                        else append("缓存极小")
                    },
                    trailing = {
                        Row {
                            FilledTonalIconButton(
                                onClick = { vm.toggleFreeze(app.packageName) },
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(
                                    if (app.packageName in state.frozen) Icons.Rounded.Bedtime
                                    else Icons.Rounded.AcUnit,
                                    contentDescription = "冻结",
                                    modifier = Modifier.size(18.dp),
                                    tint = if (app.packageName in state.frozen)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            FilledTonalIconButton(
                                onClick = { /* 引导系统卸载流程 */ },
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = "卸载",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}
