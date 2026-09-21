package com.novacare.feature.export

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacare.ui.designsystem.NovaSuccess
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ExportRepository,
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Preview(val csv: String, val rowCount: Int, val truncated: Boolean) : UiState
        data class Exported(val uri: android.net.Uri, val fileName: String, val rowCount: Int) : UiState
        data class Failed(val message: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        loadPreview()
    }

    fun loadPreview() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            runCatching { repository.preview() }
                .onSuccess { _state.value = UiState.Preview(it.csv, it.rowCount, it.truncated) }
                .onFailure { _state.value = UiState.Failed(it.message ?: "读取失败") }
        }
    }

    fun export() {
        viewModelScope.launch {
            runCatching { repository.exportToDownloads() }
                .onSuccess { result ->
                    _state.value = UiState.Exported(result.uri, result.fileName, result.rowCount)
                    NovaSuccess(context)
                    share(result.uri, result.fileName)
                }
                .onFailure { _state.value = UiState.Failed(it.message ?: "导出失败") }
        }
    }

    private fun share(uri: android.net.Uri, fileName: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            context.startActivity(
                Intent.createChooser(intent, "导出 CSV").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
