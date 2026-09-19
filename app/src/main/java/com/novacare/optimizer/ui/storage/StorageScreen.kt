package com.novacare.optimizer.ui.storage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacare.optimizer.core.DeviceRepository
import com.novacare.optimizer.ui.components.*
import com.novacare.optimizer.ui.theme.NovaSemanticColors
import com.novacare.optimizer.ui.theme.OneUiSpacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StorageState(
    val loading: Boolean = true,
    val totalGb: Float = 0f,
    val usedGb: Float = 0f,
    /** 真实扫描得到的分类（name → bytes）；空 = 未能分类 */
    val categories: List<Pair<String, Long>> = emptyList(),
    /** 体积最大的文件（path → bytes） */
    val largestFiles: List<Pair<String, Long>> = emptyList(),
    /** 分类数据是否来自 Rust 引擎（false = 引擎不可用，UI 需如实说明） */
    val fromEngine: Boolean = true,
    val errorMessage: String? = null,
)

@HiltViewModel
class StorageViewModel @Inject constructor(
    private val repo: DeviceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(StorageState())
    val state: StateFlow<StorageState> = _state.asStateFlow()

    init { analyze() }

    /**
     * 真实存储分析。
     *
     * 修复 P2-2 / P2-3：此前分类大小是 `used * 0.35f / 0.25f / ...` 编造出来的，
     * 且比例之和恰好凑成 100%，是精心构造的假象，用户看到的「照片占 35%」毫无依据。
     * 现在全部来自 Rust 引擎的实际目录扫描。
     */
    fun analyze() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, errorMessage = null) }
            try {
                val status = repo.getDeviceStatus()
                val breakdown = repo.analyzeStorage()
                _state.update {
                    it.copy(
                        loading = false,
                        totalGb = status.totalStorageGb.coerceAtLeast(0.001f),
                        usedGb = status.usedStorageGb,
                        categories = breakdown.categories,
                        largestFiles = breakdown.largestFiles,
                        fromEngine = breakdown.fromRustEngine,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(loading = false, errorMessage = "分析失败：${e.message ?: "未知"}")
                }
            }
        }
    }
}

@Composable
fun StorageScreen(vm: StorageViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scroll)
            .padding(bottom = OneUiSpacing.xxxl),
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        OneUiLargeHeader(
            title = "存储管家",
            subtitle = state.errorMessage
                ?: "已用 %.1f GB / 总量 %.0f GB".format(state.usedGb, state.totalGb),
        )
        Spacer(Modifier.height(OneUiSpacing.lg))

        Column(
            modifier = Modifier.padding(horizontal = OneUiSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(OneUiSpacing.md),
        ) {
            when {
                state.loading -> {
                    OneUiCard(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(OneUiSpacing.lg),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator(strokeWidth = 3.dp) }
                    }
                }

                state.categories.isEmpty() -> {
                    // 诚实告知：引擎不可用时**不展示任何分类**，而不是编造比例
                    OneUiCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "暂无法分类",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "存储分析需要 Rust 扫描引擎（libnovacare_core）。" +
                                "当前引擎不可用，因此不展示分类数据 —— 宁可留空，也不展示估算的假数据。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> {
                    OneUiCard(modifier = Modifier.fillMaxWidth()) {
                        Text("分类", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(OneUiSpacing.lg))
                        state.categories.forEach { (name, bytes) ->
                            CategoryRow(
                                name = name,
                                bytes = bytes,
                                totalBytes = (state.totalGb * 1024 * 1024 * 1024).toLong(),
                                icon = iconForCategory(name),
                                color = colorForCategory(name),
                            )
                        }
                    }

                    // 大文件分析（此前 DESIGN_SPEC 宣传过但页面里根本没有）
                    if (state.largestFiles.isNotEmpty()) {
                        OneUiCard(modifier = Modifier.fillMaxWidth()) {
                            Text("最大的文件", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(OneUiSpacing.md))
                            state.largestFiles.forEach { (path, bytes) ->
                                LargestFileRow(path = path, bytes = bytes)
                                Spacer(Modifier.height(OneUiSpacing.xs))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

private fun iconForCategory(name: String): ImageVector = when {
    name.contains("照片") || name.contains("图片") -> Icons.Rounded.Photo
    name.contains("视频") -> Icons.Rounded.Movie
    name.contains("音频") || name.contains("音乐") -> Icons.Rounded.MusicNote
    name.contains("文档") -> Icons.Rounded.Description
    else -> Icons.Rounded.Apps
}

private fun colorForCategory(name: String): Color = when {
    name.contains("照片") || name.contains("图片") -> NovaSemanticColors.Storage
    name.contains("视频") -> NovaSemanticColors.Memory
    name.contains("音频") || name.contains("音乐") -> NovaSemanticColors.AppSafety
    name.contains("文档") -> NovaSemanticColors.Battery
    else -> Color(0xFF8A8F98)
}

@Composable
private fun CategoryRow(
    name: String,
    bytes: Long,
    totalBytes: Long,
    icon: ImageVector,
    color: Color,
) {
    val sizeGb = bytes / 1024f / 1024f / 1024f
    val ratio = (bytes.toFloat() / totalBytes.coerceAtLeast(1)).coerceIn(0f, 1f)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = OneUiSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(OneUiSpacing.md))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    "%.2f GB".format(sizeGb),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(OneUiSpacing.xs))
            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = color,
                trackColor = color.copy(alpha = 0.12f),
            )
        }
    }
}

@Composable
private fun LargestFileRow(path: String, bytes: Long) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = path.substringAfterLast('/'),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = path,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(OneUiSpacing.md))
        Text(
            text = "%.1f MB".format(bytes / 1024f / 1024f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
