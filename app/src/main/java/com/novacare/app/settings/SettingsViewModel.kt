package com.novacare.app.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacare.core.data.SettingsRepository
import com.novacare.core.data.ThemeMode
import com.novacare.core.engine.NovaEngine
import com.novacare.core.model.CloudModel
import com.novacare.core.system.MissingCapability
import com.novacare.core.system.ShizukuShell
import com.novacare.core.system.SystemPermissions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 设置页 ViewModel
 *
 * 除了读写 [SettingsRepository]，还负责把「当前每项能力的真实状态」
 * 暴露给界面 —— 这一页的价值恰恰在于它显示的是实况，
 * 而不是一份写死的功能清单。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val app: Application,
    private val repository: SettingsRepository,
    private val permissions: SystemPermissions,
    private val engine: NovaEngine,
    private val shizuku: ShizukuShell,
) : ViewModel() {

    /** 权限卡片的图标分类（避免把 icon 直接塞进 ViewModel 层） */
    enum class CapabilityIcon { USAGE, FILES, NOTIFICATIONS }

    /** 单项能力的实时状态 —— granted 为 false 时界面必须给出"会失去什么"和跳转入口 */
    data class CapabilityState(
        val capability: MissingCapability,
        val icon: CapabilityIcon,
        val title: String,
        /** 为什么需要（说人话，不写权限名） */
        val why: String,
        /** 未授予时具体会失去什么能力 */
        val consequence: String,
        /** 按钮文案 */
        val actionLabel: String,
        val granted: Boolean,
    )

    val settings = repository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SettingsRepository.Settings(),
    )

    private val _capabilities = MutableStateFlow(readCapabilities())
    val capabilities: StateFlow<List<CapabilityState>> = _capabilities.asStateFlow()

    private val _engineAvailable = MutableStateFlow(engine.isAvailable)
    val engineAvailable: StateFlow<Boolean> = _engineAvailable.asStateFlow()

    private val _engineVersion = MutableStateFlow(engine.version())
    val engineVersion: StateFlow<String> = _engineVersion.asStateFlow()

    private val _shizukuAvailable = MutableStateFlow(shizuku.isAvailable())
    val shizukuAvailable: StateFlow<Boolean> = _shizukuAvailable.asStateFlow()

    /** 从系统设置页回来后必须重查，否则用户授了权回来看到状态还是"未授予" */
    fun refreshCapabilities() {
        _capabilities.value = readCapabilities()
        _engineAvailable.value = engine.isAvailable
        _engineVersion.value = engine.version()
        _shizukuAvailable.value = shizuku.isAvailable()
    }

    fun grant(capability: MissingCapability) = permissions.launchGrantFor(capability)

    /** 请求 Shizuku 授权（弹出系统授权框），结果回来时刷新状态 */
    fun requestShizuku() {
        shizuku.requestPermission { granted ->
            _shizukuAvailable.value = granted
            if (granted) {
                _notice.value = "Shizuku 已授权。缓存自动化与冻结功能现已可用。"
            } else {
                _notice.value = "Shizuku 未授权，高级功能仍不可用。"
            }
        }
    }

    fun setAdvanced(enabled: Boolean) = viewModelScope.launch {
        repository.setAdvancedMode(enabled)
    }

    fun setCloudAi(enabled: Boolean) = viewModelScope.launch {
        repository.setCloudAi(enabled)
    }

    fun setModel(model: CloudModel) = viewModelScope.launch {
        repository.setCloudModel(model)
    }

    fun setApiKey(key: String) = viewModelScope.launch {
        repository.setCloudApiKey(key)
    }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch {
        repository.setThemeMode(mode)
    }

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()
    fun consumeNotice() { _notice.value = null }

    /** 打开 GitHub Issues（反馈入口必须是能真的点开的） */
    fun openIssues() {
        val result = runCatching {
            app.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(ISSUES_URL)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
            true
        }
        if (result.isFailure) {
            // 设备上没装任何浏览器或浏览器拦截 → 上一版只 runCatching 静默吞错，
            // 用户点完什么都没发生，还以为 App 坏了。
            // 现在把失败暴露给 UI 显示一条 Snackbar，并附仓库 URL 让用户复制打开。
            _notice.value = "没找到可用的浏览器。请打开浏览器访问：$ISSUES_URL"
        }
    }

    /**
     * 读取实况。
     *
     * 注意「所有文件访问」只在 Android 11+ 才有意义 —— 低版本上这个开关不存在，
     * 因此低版本直接不展示该项，而不是永远显示一个"未授予"的红点让用户徒劳点击。
     */
    private fun readCapabilities(): List<CapabilityState> = buildList {
        add(
            CapabilityState(
                capability = MissingCapability.USAGE_STATS,
                icon = CapabilityIcon.USAGE,
                title = "使用情况访问",
                why = "判断一个应用最近是否被打开过，这是识别不常用应用的唯一依据。",
                consequence = "未授予时无法区分常用与不常用应用，冻结功能会失去主要依据。",
                actionLabel = "去系统设置开启",
                granted = permissions.hasUsageStats(),
            ),
        )

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            add(
                CapabilityState(
                    capability = MissingCapability.ALL_FILES,
                    icon = CapabilityIcon.FILES,
                    title = "所有文件访问",
                    why = "让分析内核能完整遍历外部存储。没有它时，内核无法进入其他应用的目录。",
                    consequence = "未授予时扫描结果会明显偏少，你会觉得明明占了很多空间却扫不出东西。",
                    actionLabel = "去系统设置开启",
                    granted = permissions.hasAllFilesAccess(),
                ),
            )
        }

        add(
            CapabilityState(
                capability = MissingCapability.NOTIFICATIONS,
                icon = CapabilityIcon.NOTIFICATIONS,
                title = "通知权限",
                why = "在耗时较长的清理过程中展示进度，完成后告知结果。",
                consequence = "未授予时清理过程没有任何提示，你会不知道它是否还在运行。",
                actionLabel = "去系统设置开启",
                granted = permissions.hasNotifications(),
            ),
        )
    }

    private companion object {
        const val ISSUES_URL = "https://github.com/chinazll/NovaCare/issues"
    }
}
