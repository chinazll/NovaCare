package com.novacare.optimizer.core

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 运行时权限助手 —— 严格遵循最小权限原则
 *
 * PACKAGE_USAGE_STATS 是 Android 11+ 严格管控的"特殊权限"，
 * 用户必须在「设置 → 特殊应用权限 → 使用情况访问权限」中手动授权。
 * 本助手只做"检测 + 引导"，绝不偷偷申请。
 */
@Singleton
class PermissionHelper @Inject constructor(
    private val context: Context,
) {
    /** 检测是否已获得使用情况访问权限 */
    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** 跳转到系统设置页让用户授权 */
    fun usageStatsSettingsIntent(): Intent {
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * 应用扫描权限说明：
     * - Android 11+ (API 30+) 默认只能看到 <queries> 中声明的应用
     * - 已通过 AndroidManifest 中 <queries> 声明 LAUNCHER intent
     * - 不申请 QUERY_ALL_PACKAGES，最大化用户隐私
     */
    fun explainAppScanScope(): String =
        "应用扫描仅显示你启动器中可见的应用（Android 11+ 默认隐私保护）。" +
                "NovaCare 不会上传任何应用数据。"
}