package com.novacare.optimizer.ui.clean

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacare.optimizer.core.DeviceRepository
import com.novacare.optimizer.core.DeviceRepository.JunkItem
import com.novacare.optimizer.ui.components.*
import com.novacare.optimizer.ui.theme.OneUiSpacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CleanState(
    val scanning: Boolean = false,
    val cleaning: Boolean = false,
    val items: List<JunkItem> = emptyList(),
    val selected: Set<String> = emptySet(),
    val freedMb: Float? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class CleanViewModel @Inject constructor(
    private val repo: DeviceRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CleanState())
    val state: StateFlow<CleanState> = _state.asStateFlow()

    init { scan() }

    fun scan() {
        viewModelScope.launch {
            _state.update { it.copy(scanning = true, freedMb = null, errorMessage = null) }
            try {
                val junk = repo.scanJunk()
                _state.update {
                    it.copy(
                        scanning = false,
                        items = junk,
                        selected = junk.filter { j -> j.isSafe }.map { j -> j.path }.toSet(),
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(scanning = false, errorMessage = "扫描失败：${e.message ?: "未知"}")
                }
            }
        }
    }

    fun toggle(path: String, checked: Boolean) {
        val s = _state.value.selected.toMutableSet()
        if (checked) s += path else s -= path
        _state.update { it.copy(selected = s) }
    }

    fun clean() {
        viewModelScope.launch {
            _state.update { it.copy(cleaning = true) }
            try {
                val items = _state.value.items.filter { it.path in _state.value.selected }
                val freed = repo.cleanJunk(items)
                _state.update {
                    it.copy(
                        cleaning = false,
                        freedMb = freed / 1024f / 1024f,
                        items = emptyList(),
                        selected = emptySet(),
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(cleaning = false, errorMessage = "清理失败：${e.message ?: "未知"}") }
            }
        }
    }
}

@Composable
fun CleanScreen(vm: CleanViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val totalMb = state.items.filter { it.path in state.selected }.sumOf { it.sizeMb.toDouble() }.toFloat()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(bottom = OneUiSpacing.xxxl),
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        OneUiLargeHeader(
            title = "垃圾清理",
            subtitle = state.errorMessage ?: when {
                state.freedMb != null -> "本次已释放 %.1f MB".format(state.freedMb)
                state.scanning -> "正在扫描..."
                else -> "共发现 ${state.items.size} 项"
            },
        )
        Spacer(Modifier.height(OneUiSpacing.lg))

        if (state.scanning) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(OneUiSpacing.xxxl),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(strokeWidth = 3.dp)
            }
        } else if (state.items.isEmpty() && state.freedMb == null) {
            Box(modifier = Modifier.padding(horizontal = OneUiSpacing.xl)) {
                EmptyState(
                    icon = Icons.Rounded.CleaningServices,
                    title = "非常干净",
                    description = "没有发现可清理的垃圾文件",
                )
            }
        } else {
            Column(
                modifier = Modifier.padding(horizontal = OneUiSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(OneUiSpacing.sm),
            ) {
                OneUiCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        state.items.forEach { item ->
                            JunkRow(
                                item = item,
                                checked = item.path in state.selected,
                                onToggle = { c -> vm.toggle(item.path, c) },
                            )
                        }
                    }
                }
            }
        }

        if (state.freedMb != null) {
            Spacer(Modifier.height(OneUiSpacing.lg))
            Box(modifier = Modifier.padding(horizontal = OneUiSpacing.xl)) {
                OneUiCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        "已成功释放 %.1f MB".format(state.freedMb),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        if (state.items.isNotEmpty() && state.freedMb == null) {
            Spacer(Modifier.height(OneUiSpacing.lg))
            Box(modifier = Modifier.padding(horizontal = OneUiSpacing.xl)) {
                PillButton(
                    text = if (state.cleaning) "清理中..." else "清理 %.1f MB".format(totalMb),
                    onClick = { vm.clean() },
                    enabled = !state.cleaning && totalMb > 0,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(OneUiSpacing.md))
        Box(modifier = Modifier.padding(horizontal = OneUiSpacing.xl)) {
            PillOutlineButton(
                text = if (state.scanning) "扫描中..." else "重新扫描",
                onClick = { vm.scan() },
                enabled = !state.scanning,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable
private fun JunkRow(
    item: JunkItem,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = OneUiSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SquircleIconBg(
            icon = if (item.isSafe) Icons.Rounded.DeleteSweep else Icons.Rounded.RestoreFromTrash,
            tint = if (item.isSafe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            size = 40.dp,
        )
        Spacer(Modifier.width(OneUiSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                item.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                buildString {
                    append("%.1f MB".format(item.sizeMb))
                    if (item.riskHint.isNotEmpty()) append(" · ${item.riskHint}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (item.isSafe) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.error,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
        )
    }
}