package com.novacare.core.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.novacare.core.model.FreezeMethod
import com.novacare.core.model.FreezeResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 应用冻结（三层系统控制策略，蓝图 §5）
 *
 * 已核实的事实：Android 官方 App Hibernation API **不能**让第三方 App 休眠别的应用
 * （isAutoRevokeWhitelisted 只查自己，官方文档明确「不建议使用」）。
 * 这就是 Hail 必须用 Shizuku 的根本原因。
 *
 * 因此分层：
 * - 普通模式：引导到系统设置页（官方路径，零 hack，零门槛）
 * - 高级模式：Shizuku `pm suspend`（可逆，覆盖 Android 11+）
 */
@Singleton
class FreezeController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shell: ShizukuShell,
) {
    // Shizuku 可用性在构造时探测一次，避免每次 UI recompose 都走反射
    private val shizukuAvailable: Boolean = shell.isAvailable()

    fun availableMethods(): List<FreezeMethod> = buildList {
        add(FreezeMethod.OFFICIAL_GUIDE)
        if (shizukuAvailable) add(FreezeMethod.SHIZUKU_SUSPEND)
    }

    /** 普通模式：打开系统「应用详情」页，由用户操作（零风险） */
    fun openAppDetails(packageName: String): Boolean {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    suspend fun freeze(packageName: String, preferShizuku: Boolean): FreezeResult {
        if (preferShizuku && shell.isAvailable()) {
            val ok = shell.run("pm suspend $packageName")
            return FreezeResult(
                packageName = packageName,
                success = ok,
                method = FreezeMethod.SHIZUKU_SUSPEND,
                message = if (ok) "已通过 Shizuku 冻结" else "Shizuku 冻结失败",
            )
        }
        val opened = openAppDetails(packageName)
        return FreezeResult(
            packageName = packageName,
            success = opened,
            method = FreezeMethod.OFFICIAL_GUIDE,
            message = if (opened) "已打开系统设置页，请在其中停用该应用" else "无法打开设置页",
        )
    }

    suspend fun unfreeze(packageName: String): FreezeResult {
        if (shell.isAvailable()) {
            val ok = shell.run("pm unsuspend $packageName")
            return FreezeResult(
                packageName = packageName,
                success = ok,
                method = FreezeMethod.SHIZUKU_SUSPEND,
                message = if (ok) "已解除冻结" else "解除冻结失败",
            )
        }
        val opened = openAppDetails(packageName)
        return FreezeResult(
            packageName = packageName,
            success = opened,
            method = FreezeMethod.OFFICIAL_GUIDE,
            message = if (opened) "已打开系统设置页，请手动启用" else "无法打开设置页",
        )
    }
}
