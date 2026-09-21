package com.novacare.feature.appdetail

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacare.core.system.AppRepository
import com.novacare.core.system.StorageStatsSource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * App 详情 ViewModel（v0.21.0 新增）
 *
 * 数据来源（按"真实度优先"排列）：
 *   - 包名 / 版本 / 安装时间 / 标签：PackageManager.getPackageInfo —— 系统权威
 *   - 缓存 / 数据占用：StorageStatsSource（API 26+）—— 系统按应用聚合的真实账目
 *   - 安装包大小：stats.appBytes（StorageStatsManager 给出）
 *   - 最后使用时间：AppRepository（已合并 UsageStatsManager），未授权为 null
 *
 * 数据真实原则：拿不到就 null / 0 / "系统未提供"，**绝不**用 0 冒充"不占空间"。
 */
@HiltViewModel
class AppDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appRepository: AppRepository,
    private val storageStats: StorageStatsSource,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val packageName: String =
        savedStateHandle.get<String>(ARG_PACKAGE_NAME)?.takeIf { it.isNotBlank() }
            ?: error("AppDetailScreen requires packageName navigation arg")

    sealed interface UiState {
        data object Loading : UiState
        data class Ready(val detail: AppDetail) : UiState
        data class Failed(val message: String) : UiState
    }

    data class AppDetail(
        val packageName: String,
        val label: String,
        val versionName: String,
        val targetSdk: Int,
        val isSystem: Boolean,
        val isEnabled: Boolean,
        val installTimeEpochMs: Long,
        val updateTimeEpochMs: Long,
        val lastUsedEpochMs: Long?,
        /** 应用包安装大小（bytes） */
        val apkBytes: Long,
        /** 缓存大小（bytes） */
        val cacheBytes: Long,
        /** 数据大小（bytes） */
        val dataBytes: Long,
        /** 应用总占用（cache + data + code） */
        val totalBytes: Long,
        /** 已请求的权限数 */
        val permissionCount: Int,
        val sourceLabel: String,
    )

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _actionNotice = MutableStateFlow<String?>(null)
    val actionNotice: StateFlow<String?> = _actionNotice.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            runCatching { withContext(Dispatchers.IO) { fetchDetail() } }
                .onSuccess { _state.value = UiState.Ready(it) }
                .onFailure { _state.value = UiState.Failed(it.message ?: "读取应用详情失败") }
        }
    }

    private suspend fun fetchDetail(): AppDetail {
        val pm = context.packageManager
        val flags = PackageManager.PackageInfoFlags.of(0)
        @Suppress("DEPRECATION")
        val info: PackageInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, flags)
            } else {
                pm.getPackageInfo(packageName, 0)
            }
        }.getOrElse { e ->
            throw IllegalStateException("找不到包：$packageName（${e.message ?: "未知错误"}）")
        }

        val appInfo = info.applicationInfo
            ?: throw IllegalStateException("应用信息不可用：$packageName")
        val label = runCatching { pm.getApplicationLabel(appInfo).toString() }
            .getOrDefault(packageName)

        val stats = storageStats.queryStats(packageName)
        val apkBytes = stats?.appBytes ?: 0L
        val cacheBytes = stats?.cacheBytes ?: 0L
        val dataBytes = stats?.dataBytes ?: 0L
        val totalBytes = cacheBytes + dataBytes + apkBytes

        // 最后使用时间从 AppRepository 取 —— 它已合并 UsageStatsManager
        val nowMs = System.currentTimeMillis()
        val lastUsedMs = appRepository.loadInstalledApps(nowMs)
            .firstOrNull { it.packageName == packageName }
            ?.lastUsedEpochMs

        val isSystem =
            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

        val isEnabled = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                pm.getApplicationEnabledSetting(packageName) !=
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            } else {
                @Suppress("DEPRECATION")
                appInfo.enabled
            }
        }.getOrDefault(true)

        val permissionCount = info.requestedPermissions?.size ?: 0

        val sourceLabel = when {
            stats == null && lastUsedMs == null -> "数据来源：系统未提供任何维度（ROM 可能屏蔽）"
            stats == null -> "缓存/数据：API < 26 或权限不足，系统未给出"
            lastUsedMs == null -> "最后使用：未授予「使用情况访问」"
            else -> "数据来源：PackageManager + StorageStatsManager + UsageStatsManager"
        }

        return AppDetail(
            packageName = packageName,
            label = label,
            versionName = info.versionName ?: "—",
            targetSdk = appInfo.targetSdkVersion,
            isSystem = isSystem,
            isEnabled = isEnabled,
            installTimeEpochMs = info.firstInstallTime,
            updateTimeEpochMs = info.lastUpdateTime,
            lastUsedEpochMs = lastUsedMs,
            apkBytes = apkBytes,
            cacheBytes = cacheBytes,
            dataBytes = dataBytes,
            totalBytes = totalBytes,
            permissionCount = permissionCount,
            sourceLabel = sourceLabel,
        )
    }

    /**
     * 清除本应用缓存。
     *
     * PackageManager 没有公开的 deleteCache；自清缓存用 context.cacheDir.deleteRecursively()。
     * Android 沙箱把其他应用的 cache 目录保护在各自 UID 下，第三方应用**没有**
     * 直接删其他应用 cache 的能力。
     *
     * 对其他应用：跳 ACTION_APPLICATION_DETAILS_SETTINGS，由用户自己点。
     */
    fun clearCache() {
        if (packageName == context.packageName) {
            val dir = context.cacheDir
            val deleted: Boolean = try {
                if (dir.exists() && dir.isDirectory) dir.deleteRecursively() else true
            } catch (e: Throwable) {
                _actionNotice.value = "清除本应用缓存失败：${e.message ?: "未知错误"}"
                return
            }
            _actionNotice.value = if (deleted) {
                "已尝试清除本应用缓存。\n仅 cache 目录；data 目录由系统保护。"
            } else {
                "本应用无需清除缓存"
            }
            load()
            return
        }
        _actionNotice.value =
            "Android 不允许第三方直接清理其他应用的缓存目录。\n" +
                "已为你打开系统「应用信息」页，请在那里点「存储占用 → 清除缓存」。"
        openAppDetailsSettings()
    }

    /**
     * 停用应用。
     *
     * PackageManager.setApplicationEnabledSetting 是公开 API，不需要权限。
     * 实际生效依赖厂商 ROM —— 部分 ROM 完全屏蔽第三方对系统应用的停用请求。
     */
    fun disableApp() {
        runCatching {
            context.packageManager.setApplicationEnabledSetting(
                packageName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                0,
            )
        }.onSuccess {
            _actionNotice.value =
                "已发送停用指令：$packageName\n" +
                    "实际生效以系统/ROM 为准。\n" +
                    "部分 ROM 会忽略第三方应用的停用请求。\n" +
                    "需要恢复时可使用「冻结」模块的解冻，或在系统应用页手动启用。"
            load()
        }.onFailure { e ->
            _actionNotice.value = "停用失败：${e.message ?: "未知错误"}（部分 ROM 拒绝第三方停用请求）"
        }
    }

    /**
     * 卸载：跳系统确认页（必须由用户在系统页点确认，第三方无法静默卸载）。
     */
    fun uninstallApp() {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { _actionNotice.value = "无法启动卸载流程：${it.message ?: "未知错误"}" }
    }

    /** 打开系统的"应用信息"页（用户在系统页里点清除缓存/停用/卸载） */
    fun openAppDetailsSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { _actionNotice.value = "无法打开系统应用页：${it.message ?: "未知错误"}" }
    }

    fun consumeActionNotice() {
        _actionNotice.value = null
    }

    companion object {
        const val ARG_PACKAGE_NAME = "packageName"
    }
}
