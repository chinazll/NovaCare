package com.novacare.optimizer.ui.storage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacare.optimizer.core.DeviceRepository
import com.novacare.optimizer.ui.components.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StorageUiState(
    val loading: Boolean = true,
    val categories: List<DeviceRepository.StorageCategory> = emptyList(),
    val usedGb: Float = 0f,
    val totalGb: Float = 0f,
    val bigFiles: List<Pair<String, Float>> = emptyList(),
)

@HiltViewModel
class StorageViewModel @Inject constructor(
    private val repo: DeviceRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(StorageUiState())
    val state = _state.asStateFlow()

    init { analyze() }

    fun analyze() {
        viewModelScope.launch {
            val status = repo.getDeviceStatus()
            val cats = repo.analyzeStorage()
            _state.value = StorageUiState(
                loading = false,
                categories = cats,
                usedGb = status.usedStorageGb,
                totalGb = status.totalGb,
            )
        }
    }
}

/**
 * 存储管家：One UI "我的文件-存储分析" 语法
 * 上半区可视化（分类色带），下半区可操作列表
 */
@Composable
fun StorageScreen(vm: StorageViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
    ) {
        OneUiLargeHeader(
            title = "存储管家",
            subtitle = "%.1f GB / %.0f GB".format(state.usedGb, state.totalGb),
        )
        Spacer(Modifier.height(16.dp))

        // ---- 分类色带（观看区可视化）----
        Column(Modifier.padding(horizontal = 20.dp)) {
            OneUiCard(Modifier.fillMaxWidth()) {
                if (state.loading) {
                    Text("正在分析…", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(14.dp),
                    ) {
                        val total = state.categories.sumOf { it.sizeGb.toDouble() }.toFloat()
                        state.categories.forEach { cat ->
                            if (cat.sizeGb > 0) {
                                Box(
                                    Modifier
                                        .weight((cat.sizeGb / total).toFloat().coerceAtLeast(0.02f))
                                        .fillMaxHeight()
                                        .background(Color(cat.color)),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    state.categories.forEach { cat ->
                        OneUiRow(
                            title = cat.name,
                            subtitle = "%.2f GB".format(cat.sizeGb),
                            icon = { Icon(pickIcon(cat.name), null, tint = Color(cat.color)) },
                            iconTint = Color(cat.color),
                        )
                    }
                }
            }
        }
    }
}

private fun pickIcon(name: String) = when (name) {
    "照片" -> Icons.Rounded.Photo
    "视频" -> Icons.Rounded.Movie
    "音频" -> Icons.Rounded.MusicNote
    "文档" -> Icons.Rounded.Description
    else -> Icons.Rounded.Apps
}
