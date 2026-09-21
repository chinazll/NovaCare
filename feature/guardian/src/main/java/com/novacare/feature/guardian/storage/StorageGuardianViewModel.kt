package com.novacare.feature.guardian.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacare.core.domain.CleanupOutcome
import com.novacare.core.domain.StorageCleanupUseCase
import com.novacare.core.domain.StorageInsights
import com.novacare.core.domain.StorageInsightsUseCase
import com.novacare.core.system.MissingCapability
import com.novacare.core.system.SystemPermissions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 存储守护 ViewModel
 *
 * 【为什么重复文件要单独一个按钮才扫】
 *   Rust 内核的重复检测会对每个 ≥1MB 的文件做完整 BLAKE3 哈希，
 *   在一台用了两年的手机上这是几十秒级别的活。
 *   进页面就跑 = 用户盯着转圈。所以默认快扫（不含哈希），
 *   重复文件由用户显式触发，并提前说明代价。
 */
@HiltViewModel
class StorageGuardianViewModel @Inject constructor(
    private val insightsUseCase: StorageInsightsUseCase,
    private val cleanupUseCase: StorageCleanupUseCase,
    private val permissions: SystemPermissions,
) : ViewModel() {

    sealed interface UiState {
        data object Idle : UiState
        data object Scanning : UiState
        data class Ready(val data: StorageInsights) : UiState
        data class Failed(val message: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** 是否正在跑耗时的重复文件哈希扫描 */
    private val _scanningDuplicates = MutableStateFlow(false)
    val scanningDuplicates: StateFlow<Boolean> = _scanningDuplicates.asStateFlow()

    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    private val _deleting = MutableStateFlow(false)
    val deleting: StateFlow<Boolean> = _deleting.asStateFlow()

    private val _result = MutableStateFlow<CleanupOutcome?>(null)
    val result: StateFlow<CleanupOutcome?> = _result.asStateFlow()

    private var lastRootPath: String = ""

    fun load(rootPath: String, withDuplicates: Boolean = false, force: Boolean = false) {
        if (!force && _state.value is UiState.Ready && !withDuplicates) return
        lastRootPath = rootPath
        viewModelScope.launch {
            if (withDuplicates) {
                // 重复扫描是叠加在已有结果上的增量动作：保留当前结果，只标记 loading
                _scanningDuplicates.value = true
            } else {
                _state.value = UiState.Scanning
            }
            runCatching { insightsUseCase(rootPath, withDuplicates) }
                .onSuccess { data ->
                    _state.value = UiState.Ready(data)
                    // 扫描结果变了，旧的勾选必须失效（路径可能已不存在）
                    _selected.value = emptySet()
                }
                .onFailure { e ->
                    _state.value = UiState.Failed(e.message ?: "扫描失败")
                }
            _scanningDuplicates.value = false
        }
    }

    fun scanDuplicates() {
        if (lastRootPath.isBlank()) return
        load(lastRootPath, withDuplicates = true, force = true)
    }

    fun toggle(path: String) {
        _selected.update { if (path in it) it - path else it + path }
    }

    fun setSelection(paths: Collection<String>, selected: Boolean) {
        _selected.update { current ->
            if (selected) current + paths else current - paths.toSet()
        }
    }

    fun clearSelection() {
        _selected.value = emptySet()
    }

    fun deleteSelected() {
        val paths = _selected.value.toList()
        if (paths.isEmpty() || _deleting.value) return
        _deleting.value = true
        viewModelScope.launch {
            val outcome = runCatching { cleanupUseCase(paths) }
                .getOrDefault(CleanupOutcome(emptyList(), paths, 0L))
            _result.value = outcome
            _deleting.value = false
            _selected.value = emptySet()
            // 删除后重新扫一遍，让容量与列表反映真实状态（不靠减法估算）
            if (lastRootPath.isNotBlank()) {
                load(lastRootPath, withDuplicates = false, force = true)
            }
        }
    }

    fun dismissResult() {
        _result.value = null
    }

    /** 缺少「所有文件访问」时引导到系统设置页（本 App 无法自己授予自己这个权限） */
    fun grantAllFiles() {
        permissions.launchGrantFor(MissingCapability.ALL_FILES)
    }
}
