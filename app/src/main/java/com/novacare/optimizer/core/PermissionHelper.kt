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
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) {
    /** 检测是否已获得使用情况访问权限 */
    fun hasUsageStatsPermission(): Boolean = runCatching {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    /**
     * 是否持有完整包可见性（QUERY_ALL_PACKAGES）。
     *
     * 本项目**主动放弃**该权限（Google Play 严格管控，且侵犯用户隐私），
     * 因此本方法恒定返回 false，其作用是驱动 [DeviceRepository.scanJunk] 关闭
     * 「残留目录检测」—— 因为 Android 11+ 分区存储下无法可靠枚举已安装应用，
     * 强行比对会把在用应用的 `Android/data/<pkg>` 误判为残留并删除（P1-4 数据丢失风险）。
     */
    fun hasFullPackageVisibility(): Boolean = runCatching {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return true
        context.checkSelfPermission(android.Manifest.permission.QUERY_ALL_PACKAGES) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

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