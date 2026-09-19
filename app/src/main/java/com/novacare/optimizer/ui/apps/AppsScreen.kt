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
import com.novacare.optimizer.core.AppFreezeManager
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
    /** Shizuku 是否可用（冻结能力的前提） */
    val shizukuAvailable: Boolean = false,
    val toast: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class AppsViewModel @Inject constructor(
    private val repo: DeviceRepository,
    private val freezeManager: AppFreezeManager,
) : ViewModel() {
    private val _state = MutableStateFlow(AppsState())
    val state: StateFlow<AppsState> = _state.asStateFlow()

    init {
        scan()
        viewModelScope.launch {
            // 持续观察已冻结集合，冻结成功后 UI 立刻反映
            freezeManager.frozenPackages.collect { frozen ->
                _state.update { it.copy(frozen = frozen) }
            }
        }
    }

    fun scan() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, errorMessage = null) }
            try {
                val apps = repo.scanApps()
                _state.update {
                    it.copy(
                        loading = false,
                        apps = apps,
                        shizukuAvailable = freezeManager.isShizukuAvailable(),
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, errorMessage = "扫描失败：${e.message ?: "未知"}") }
            }
        }
    }

    fun setQuery(q: String) {
        _state.update { it.copy(query = q) }
    }

    /**
     * 冻结 / 解冻。
     *
     * 修复 P1-1：此前雪花图标只是装饰（showChevron=false、无任何 onClick），
     * 点击完全没反应，但 README/DESIGN_SPEC/INSTALL/PRIVACY 都在宣传「冻结应用」。
     * 现在真正调用 [AppFreezeManager] 通过 Shizuku 执行 `pm suspend`。
     */
    fun toggleFreeze(app: AppInfo) {
        viewModelScope.launch {
            val pkg = app.packageName
            if (!freezeManager.isShizukuAvailable()) {
                _state.update {
                    it.copy(toast = "需要 Shizuku 授权才能冻结应用（可逆操作，不会删除数据）")
                }
                return@launch
            }
            val isFrozen = pkg in _state.value.frozen
            val ok = if (isFrozen) freezeManager.unfreeze(pkg) else freezeManager.freeze(pkg)
            _state.update {
                it.copy(
                    shizukuAvailable = freezeManager.isShizukuAvailable(),
                    toast = if (ok) {
                        if (isFrozen) "已解冻 ${app.label}" else "已冻结 ${app.label}（数据保留，随时可恢复）"
                    } else {
                        "操作失败，请检查 Shizuku 是否已授权"
                    },
                )
            }
        }
    }

    fun clearToast() = _state.update { it.copy(toast = null) }
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

        state.toast?.let { msg ->
            Box(Modifier.padding(horizontal = OneUiSpacing.xl)) {
                OneUiCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        msg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(OneUiSpacing.sm))
        }

        if (state.loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                    AppListItem(
                        app = app,
                        isFrozen = app.packageName in state.frozen,
                        onFreezeToggle = { vm.toggleFreeze(app) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppListItem(
    app: AppInfo,
    isFrozen: Boolean,
    onFreezeToggle: () -> Unit,
) {
    OneUiListRow(
        title = app.label,
        subtitle = buildString {
            if (app.isSystemApp) append("系统应用 · ")
            append("v${app.versionName}")
            // 缓存/数据大小来自 StorageStatsManager 的真实读数（P1-2）
            if (app.totalSizeMb >= 1f) append(" · 占用 %.0f MB".format(app.totalSizeMb))
            if (app.cacheSizeMb >= 1f) append(" · 缓存 %.0f MB".format(app.cacheSizeMb))
            if (app.lastUsedDays >= 0) append(" · ${app.lastUsedDays} 天前用")
            if (isFrozen) append(" · 已冻结")
        },
        leading = Icons.Rounded.Apps,
        leadingTint = if (app.isSystemApp) MaterialTheme.colorScheme.outline
        else MaterialTheme.colorScheme.primary,
        showChevron = false,
        trailing = {
            if (app.isSystemApp) {
                // 系统应用不提供冻结入口，避免用户误冻结关键组件
                Icon(
                    imageVector = Icons.Rounded.Bedtime,
                    contentDescription = "系统应用",
                    tint = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                IconButton(onClick = onFreezeToggle) {
                    Icon(
                        imageVector = Icons.Rounded.AcUnit,
                        contentDescription = if (isFrozen) "解冻" else "冻结",
                        tint = if (isFrozen) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
    )
}
