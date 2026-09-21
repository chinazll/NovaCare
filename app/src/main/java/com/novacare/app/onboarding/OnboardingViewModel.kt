package com.novacare.app.onboarding

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacare.core.data.SettingsRepository
import com.novacare.core.system.CacheCleanAccessibilityService
import com.novacare.core.system.MissingCapability
import com.novacare.core.system.ShizukuShell
import com.novacare.core.system.SystemPermissions
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 引导流程里出现的每一项授权/能力 */
enum class OnboardingPermissionKind {
    USAGE_STATS,
    ALL_FILES,
    ACCESSIBILITY,
    SHIZUKU,
    NOTIFICATIONS,
}

/**
 * 引导页的一项权限说明。
 *
 * 文案纪律（与设置页一致）：只写"它换来什么"和"不给会失去什么"，
 * 不写"不授权也能全功能" —— 那是骗人，用户点进功能后发现没反应会更糟。
 *
 * @param required true=核心能力（不给会明显降级）；false=可选增强
 */
data class OnboardingPermissionItem(
    val kind: OnboardingPermissionKind,
    val title: String,
    val why: String,
    val consequence: String,
    val required: Boolean,
    val granted: Boolean,
    val actionLabel: String,
)

/**
 * Onboarding 状态层。
 *
 * 职责边界：
 *   - 读**实况**（每次 refresh 重新查询系统，而不是缓存一份写死的清单）
 *   - 把每一项映射到一个"用户可点的按钮"
 *   - 结束时落库 [SettingsRepository.setOnboardingCompleted]
 *
 * 不负责导航：完成只回调 [complete] 的 onCompleted，由调用方决定去哪。
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SettingsRepository,
    private val permissions: SystemPermissions,
    private val shizuku: ShizukuShell,
) : ViewModel() {

    private val _permissionItems = MutableStateFlow(readItems())
    val permissionItems: StateFlow<List<OnboardingPermissionItem>> = _permissionItems.asStateFlow()

    private val settings = repository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SettingsRepository.Settings(),
    )

    val cloudAiEnabled: StateFlow<Boolean> = settings
        .map { it.cloudAiEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val apiKeySet: StateFlow<Boolean> = settings
        .map { it.cloudApiKey.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * 重查实况。
     * 用户从系统设置页返回后必须调用 —— 否则授了权回来还显示"未开启"。
     */
    fun refresh() {
        _permissionItems.value = readItems()
    }

    /** 对某一项发起真实授权（跳系统设置页 / 弹 Shizuku 授权框） */
    fun grant(item: OnboardingPermissionItem) {
        when (item.kind) {
            OnboardingPermissionKind.USAGE_STATS ->
                permissions.launchGrantFor(MissingCapability.USAGE_STATS)

            OnboardingPermissionKind.ALL_FILES ->
                permissions.launchGrantFor(MissingCapability.ALL_FILES)

            OnboardingPermissionKind.NOTIFICATIONS ->
                permissions.launchGrantFor(MissingCapability.NOTIFICATIONS)

            OnboardingPermissionKind.ACCESSIBILITY -> runCatching {
                context.startActivity(
                    CacheCleanAccessibilityService.settingsIntent(context).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
            }

            OnboardingPermissionKind.SHIZUKU -> {
                // 结果是异步回调；成功即刷新，失败也刷新（状态没变就保持原样）
                shizuku.requestPermission { refresh() }
            }
        }
    }

    /** 完成或跳过引导：持久化后再回调，调用方据此离开引导页 */
    fun complete(onCompleted: () -> Unit) {
        viewModelScope.launch {
            repository.setOnboardingCompleted(true)
            onCompleted()
        }
    }

    private fun readItems(): List<OnboardingPermissionItem> = buildList {
        val usageGranted = runCatching { permissions.hasUsageStats() }.getOrDefault(false)
        add(
            OnboardingPermissionItem(
                kind = OnboardingPermissionKind.USAGE_STATS,
                title = "使用情况访问",
                why = "判断一个应用最近有没有被打开过。这是识别「不常用应用」的唯一依据。",
                consequence = "不授予就无法知道哪些应用长期没被用过，冻结与清理建议会失去主要依据，只能给出非常保守的结果。",
                required = true,
                granted = usageGranted,
                actionLabel = if (usageGranted) "已开启" else "去开启",
            ),
        )

        // 「所有文件访问」只在 Android 11+ 存在。低版本上这个开关根本不存在，
        // 展示一个永远点不动的红项是骗人，因此直接不展示。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val filesGranted = runCatching { permissions.hasAllFilesAccess() }.getOrDefault(false)
            add(
                OnboardingPermissionItem(
                    kind = OnboardingPermissionKind.ALL_FILES,
                    title = "所有文件访问",
                    why = "让扫描内核能完整遍历外部存储，看到真正占空间的东西。",
                    consequence = "不授予时扫描结果会明显偏少 —— 你会觉得「明明占了很多空间，怎么扫不出来」。",
                    required = true,
                    granted = filesGranted,
                    actionLabel = if (filesGranted) "已开启" else "去开启",
                ),
            )
        }

        val accessibilityGranted =
            runCatching { CacheCleanAccessibilityService.isEnabled(context) }.getOrDefault(false)
        add(
            OnboardingPermissionItem(
                kind = OnboardingPermissionKind.ACCESSIBILITY,
                title = "无障碍服务",
                why = "在没有 Shizuku 时，代替你在系统设置页上点那一下「清除缓存」。只在你主动发起清理时动作，不后台自行点击。",
                consequence = "不授予、且没有 Shizuku 时，清理只能帮你打开对应应用的系统设置页，最后那一下「清除缓存」要你自己点。",
                required = false,
                granted = accessibilityGranted,
                actionLabel = if (accessibilityGranted) "已开启" else "去开启",
            ),
        )

        val shizukuGranted = runCatching { shizuku.isAvailable() }.getOrDefault(false)
        add(
            OnboardingPermissionItem(
                kind = OnboardingPermissionKind.SHIZUKU,
                title = "Shizuku（可选）",
                why = "让 NovaCare 以更高权限直接执行清理与冻结，省掉手动那一步。需要你先装 Shizuku 并授权。",
                consequence = "不授予不影响基本使用，只是清理与冻结需要多一次手动确认。",
                required = false,
                granted = shizukuGranted,
                actionLabel = if (shizukuGranted) "已授权" else "去授权",
            ),
        )

        val notifyGranted = runCatching { permissions.hasNotifications() }.getOrDefault(false)
        add(
            OnboardingPermissionItem(
                kind = OnboardingPermissionKind.NOTIFICATIONS,
                title = "通知",
                why = "在耗时较长的清理过程中显示进度，完成后告诉你结果。",
                consequence = "不授予时清理过程没有任何提示，你无法判断它是不是还在运行。",
                required = false,
                granted = notifyGranted,
                actionLabel = if (notifyGranted) "已开启" else "去开启",
            ),
        )
    }
}
