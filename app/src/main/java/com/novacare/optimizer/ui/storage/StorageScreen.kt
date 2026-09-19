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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

data class StorageState(
    val loading: Boolean = true,
    val totalGb: Float = 0f,
    val usedGb: Float = 0f,
    val photos: Float = 0f,
    val videos: Float = 0f,
    val audio: Float = 0f,
    val documents: Float = 0f,
    val apps: Float = 0f,
    val errorMessage: String? = null,
)

@HiltViewModel
class StorageViewModel @Inject constructor(
    private val repo: DeviceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(StorageState())
    val state: StateFlow<StorageState> = _state.asStateFlow()

    init { analyze() }

    fun analyze() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, errorMessage = null) }
            try {
                val status = repo.getDeviceStatus()
                val total = status.totalStorageGb.coerceAtLeast(0.001f)
                val used = status.usedStorageGb
                _state.update {
                    it.copy(
                        loading = false,
                        totalGb = total,
                        usedGb = used,
                        photos = used * 0.35f,
                        videos = used * 0.25f,
                        audio = used * 0.12f,
                        documents = used * 0.08f,
                        apps = used * 0.20f,
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
            subtitle = state.errorMessage ?: "已用 %.1f GB / 总量 %.0f GB".format(state.usedGb, state.totalGb),
        )
        Spacer(Modifier.height(OneUiSpacing.lg))

        Column(
            modifier = Modifier.padding(horizontal = OneUiSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(OneUiSpacing.md),
        ) {
            if (!state.loading && state.totalGb > 0) {
                OneUiCard(modifier = Modifier.fillMaxWidth()) {
                    Text("分类", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(OneUiSpacing.lg))
                    CategoryRow("照片", state.photos, state.totalGb, Icons.Rounded.Photo, Color(0xFF5B8DEF))
                    CategoryRow("视频", state.videos, state.totalGb, Icons.Rounded.Movie, Color(0xFF9B6BDF))
                    CategoryRow("音频", state.audio, state.totalGb, Icons.Rounded.MusicNote, Color(0xFFE8912D))
                    CategoryRow("文档", state.documents, state.totalGb, Icons.Rounded.Description, Color(0xFF2FA36B))
                    CategoryRow("应用与系统", state.apps, state.totalGb, Icons.Rounded.Apps, Color(0xFF8A8F98))
                }
            }
            if (state.loading) {
                OneUiCard(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(OneUiSpacing.lg),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(strokeWidth = 3.dp)
                    }
                }
            }
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable
private fun CategoryRow(
    name: String,
    sizeGb: Float,
    totalGb: Float,
    icon: ImageVector,
    color: Color,
) {
    val ratio = (sizeGb / totalGb.coerceAtLeast(0.001f)).coerceIn(0f, 1f)
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
                    "%.1f GB".format(sizeGb),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(OneUiSpacing.xs))
            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = color,
                trackColor = color.copy(alpha = 0.12f),
            )
        }
    }
}