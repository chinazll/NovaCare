package com.novacare.optimizer.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacare.optimizer.core.AppInfo
import com.novacare.optimizer.core.DeviceRepository
import com.novacare.optimizer.ui.components.*
import com.novacare.optimizer.ui.theme.OneUiSpacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppsState(
    val loading: Boolean = true,
    val apps: List<AppInfo> = emptyList(),
    val frozen: Set<String> = emptySet(),
    val query: String = "",
    val errorMessage: String? = null,
)

@HiltViewModel
class AppsViewModel @Inject constructor(
    private val repo: DeviceRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AppsState())
    val state: StateFlow<AppsState> = _state.asStateFlow()

    init { scan() }

    fun scan() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, errorMessage = null) }
            try {
                val apps = repo.scanApps()
                _state.update { it.copy(loading = false, apps = apps) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, errorMessage = "扫描失败：${e.message ?: "未知"}") }
            }
        }
    }

    fun setQuery(q: String) {
        _state.update { it.copy(query = q) }
    }
}

@Composable
fun AppsScreen(vm: AppsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val filtered = remember(state.apps, state.query) {
        val q = state.query.trim()
        if (q.isEmpty()) state.apps
        else state.apps.filter { it.label.contains(q, true) || it.packageName.contains(q, true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        OneUiLargeHeader(
            title = "应用管理",
            subtitle = state.errorMessage ?: "已扫描 ${state.apps.size} 个应用",
        )
        Spacer(Modifier.height(OneUiSpacing.sm))

        OutlinedTextField(
            value = state.query,
            onValueChange = vm::setQuery,
            placeholder = { Text("搜索应用") },
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OneUiSpacing.xl),
            singleLine = true,
        )

        Spacer(Modifier.height(OneUiSpacing.md))

        if (state.loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(strokeWidth = 3.dp)
            }
        } else if (filtered.isEmpty()) {
            Box(modifier = Modifier.padding(horizontal = OneUiSpacing.xl)) {
                EmptyState(
                    icon = Icons.Rounded.Apps,
                    title = "未找到应用",
                    description = "尝试修改搜索词",
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    horizontal = OneUiSpacing.xl,
                    vertical = OneUiSpacing.sm,
                ),
            ) {
                items(filtered, key = { it.packageName }) { app ->
                    AppListItem(app = app)
                }
            }
        }
    }
}

@Composable
private fun AppListItem(app: AppInfo) {
    OneUiListRow(
        title = app.label,
        subtitle = buildString {
            if (app.isSystemApp) append("系统应用 · ")
            append("v${app.versionName}")
            if (app.lastUsedDays >= 0) {
                append(" · ${app.lastUsedDays} 天前用")
            }
        },
        leading = Icons.Rounded.Apps,
        leadingTint = if (app.isSystemApp) MaterialTheme.colorScheme.outline
        else MaterialTheme.colorScheme.primary,
        showChevron = false,
        trailing = {
            if (!app.isSystemApp) {
                Icon(
                    imageVector = Icons.Rounded.AcUnit,
                    contentDescription = "冻结",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Bedtime,
                    contentDescription = "系统应用",
                    tint = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
    )
}