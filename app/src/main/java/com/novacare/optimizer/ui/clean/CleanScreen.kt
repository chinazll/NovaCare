package com.novacare.optimizer.ui.clean

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacare.optimizer.core.DeviceRepository
import com.novacare.optimizer.ui.components.OneUiCard
import com.novacare.optimizer.ui.components.OneUiLargeHeader
import com.novacare.optimizer.ui.components.OneUiRow
import com.novacare.optimizer.ui.components.PillButton
import com.novacare.optimizer.ui.components.SectionTitle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CleanUiState(
    val scanning: Boolean = false,
    val cleaning: Boolean = false,
    val junk: List<DeviceRepository.JunkItem> = emptyList(),
    val selected: Set<String> = emptySet(),
    val freedMb: Float? = null,
)

@HiltViewModel
class CleanViewModel @Inject constructor(
    private val repo: DeviceRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CleanUiState())
    val state = _state.asStateFlow()

    init { scan() }

    fun scan() {
        viewModelScope.launch {
            _state.value = _state.value.copy(scanning = true, freedMb = null)
            val junk = repo.scanJunk()
            _state.value = _state.value.copy(
                scanning = false,
                junk = junk,
                selected = junk.filter { it.isSafe }.map { it.path }.toSet(),
            )
        }
    }

    fun toggle(path: String, checked: Boolean) {
        val s = _state.value.selected.toMutableSet()
        if (checked) s += path else s -= path
        _state.value = _state.value.copy(selected = s)
    }

    fun clean() {
        viewModelScope.launch {
            _state.value = _state.value.copy(cleaning = true)
            val items = _state.value.junk.filter { it.path in _state.value.selected }
            val freed = repo.cleanJunk(items)
            _state.value = _state.value.copy(
                cleaning = false,
                freedMb = freed / 1024f,
                junk = emptyList(),
                selected = emptySet(),
            )
        }
    }
}

/**
 * 垃圾清理（借鉴 SD Maid AppCleaner）：
 * 安全项默认勾选，风险项（崩溃转储/残留数据）需手动开启——UAD 三级安全评级思想
 */
@Composable
fun CleanScreen(vm: CleanViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val totalMb = state.junk.filter { it.path in state.selected }.sumOf { it.sizeMb.toDouble() }.toFloat()
    val freedAnimated by animateIntAsState((state.freedMb ?: 0f).toInt(), label = "freed")

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
    ) {
        OneUiLargeHeader(
            title = "垃圾清理",
            subtitle = if (state.freedMb != null) "本次已释放 ${freedAnimated} MB" else "扫描结果 %d 项".format(state.junk.size),
        )
        Spacer(Modifier.height(16.dp))

        SectionTitle("可清理项目")
        Column(Modifier.padding(horizontal = 20.dp)) {
            OneUiCard(Modifier.fillMaxWidth()) {
                if (state.scanning) {
                    Text("正在扫描缓存、临时文件与残留数据…", style = MaterialTheme.typography.bodyMedium)
                } else if (state.junk.isEmpty() && state.freedMb == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CleaningServices, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("非常干净，没有发现垃圾", style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    state.junk.forEach { item ->
                        OneUiRow(
                            title = item.label,
                            subtitle = "%.1f MB · ${if (item.isSafe) "安全" else "建议检查后清理"}".format(item.sizeMb),
                            icon = {
                                Icon(
                                    if (item.isSafe) Icons.Rounded.DeleteSweep else Icons.Rounded.RestoreFromTrash,
                                    null,
                                    tint = if (item.isSafe) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error,
                                )
                            },
                            iconTint = if (item.isSafe) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                            trailing = {
                                Switch(
                                    checked = item.path in state.selected,
                                    onCheckedChange = { vm.toggle(item.path, it) },
                                )
                            },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        if (!state.scanning && state.junk.isNotEmpty()) {
            Box(Modifier.fillMaxWidth()) {
                PillButton(
                    text = "清理 %.0f MB".format(totalMb),
                    onClick = { vm.clean() },
                    enabled = !state.cleaning && totalMb > 0,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth()) {
            PillButton(
                text = "重新扫描",
                onClick = { vm.scan() },
                enabled = !state.scanning && !state.cleaning,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            )
        }
    }
}
