package com.novacare.core.system

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 权限闸门
 *
 * 原则（蓝图 §5.5.2）：按需申请、最小化。
 * 更重要的是：**没权限就如实说没权限**，不让上层显示「一切正常」的假象。
 */
@Singleton
class SystemPermissions @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** 使用情况访问权限（F1/F3 的判定依据，未授权时结论置信度会降低） */
    fun hasUsageStats(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasNotifications(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    fun canSeeAllPackages(): Boolean {
        // Android 11+ 未声明 QUERY_ALL_PACKAGES 时只能看到有限包；本 App 已在 Manifest 声明
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                context.packageManager.getInstalledPackages(
                    PackageManager.PackageInfoFlags.of(0),
                ).size > 1
            }.getOrDefault(false)
        } else {
            true
        }
    }
}
