package com.novacare.core.system

import android.Manifest
import android.app.AppOpsManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 权限闸门与**授权引导**。
 *
 * 事故复盘（P0）：
 *   上一版只有 hasXxx() 的**查询**方法，全工程没有任何一处调用
 *   requestPermissions / ACTION_USAGE_ACCESS_SETTINGS / 任何权限请求对话框。
 *   结果：用户装上后，系统从未询问过任何权限，所有依赖权限的功能
 *   全部静默降级 —— 这正是"装上了但什么功能都没有"的直接原因之一。
 *
 * 本类现在同时承担两件事：
 *   1. 查询状态（hasXxx）
 *   2. 返回**可执行的授权意图**（xxxIntent），由 UI 层发起
 *
 * 设计原则：按需申请、最小化、每次都给用户一个"为什么需要"的理由。
 */
@Singleton
class SystemPermissions @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    // ============================================================
    // 查询
    // ============================================================

    /**
     * 使用情况访问权限（判定「不常用应用」的唯一依据）。
     *
     * 注意：这是 AppOps 权限，**不走运行时权限对话框** ——
     * 必须跳转到系统设置页由用户手动开启。这是全工程最容易漏掉的一步。
     */
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

    /** 通知权限（Android 13+ 是运行时权限） */
    fun hasNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * 是否具备完整的「所有文件访问」能力。
     *
     * Android 11+ 起，直接按路径读写 /storage/emulated/0 受分区存储限制：
     * 未授予 MANAGE_EXTERNAL_STORAGE 时，Rust 内核 walkdir 到他人目录会 EACCES，
     * 扫描结果会明显偏少（用户会认为"扫描不出东西"）。
     */
    fun hasAllFilesAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

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

    /**
     * Shizuku 是否可用（冻结 / 高级清理的最强通道）。
     *
     * 必须同时满足两个条件，缺一不可：
     *   1. Shizuku 进程已启动（binder 可达）；
     *   2. 已授权本应用（checkSelfPermission == PERMISSION_GRANTED）。
     * 上一版只查 binder：用户装了 Shizuku 但没点「允许」时会被误判为可用，
     * UI 据此展示「可一键冻结」，真正执行时 shell.run 却静默返回 false ——
     * 这是「功能看起来有、点了没反应」的直接根因。
     */
    fun isShizukuAvailable(): Boolean = runCatching {
        val cls = Class.forName("rikka.shizuku.Shizuku")
        // 旧实现取到 binder 后根本没用它做可达性判断（变量赋值完就丢），
        // 于是「装了 Shizuku 但没授权 / 进程没起」会被判成可用 —— 正是
        // 「UI 显示可一键冻结、点了没反应」的根因。这里必须 ping。
        val alive = cls.getMethod("pingBinder").invoke(null) as? Boolean ?: return@runCatching false
        if (!alive) return@runCatching false
        val granted = cls.getMethod("checkSelfPermission").invoke(null) as? Int
        granted == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    // ============================================================
    // 授权意图（交给 UI 层 startActivity）
    // ============================================================

    /** 使用情况访问设置页（优先直达本应用条目） */
    fun usageStatsSettingsIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            data = Uri.parse("package:${context.packageName}")
        }

    /** Android 11+ 的「所有文件访问」设置页 */
    fun allFilesAccessIntent(): Intent {
        val pkg = context.packageName
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                data = Uri.parse("package:$pkg")
            }
        } else {
            appDetailsIntent()
        }
    }

    /** 本应用详情页（兜底：任何设置项跳不过去时用这个） */
    fun appDetailsIntent(): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            data = Uri.parse("package:${context.packageName}")
        }

    /** 电池优化白名单设置页（长任务不被杀） */
    fun batteryOptimizationIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /**
     * 安全地启动一个设置意图。
     * 某些 ROM 会裁掉特定的 Settings action，此时回退到应用详情页 ——
     * 而不是抛 ActivityNotFoundException 让 App 崩溃。
     */
    fun launchSettings(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            runCatching { context.startActivity(appDetailsIntent()) }
        } catch (_: SecurityException) {
            runCatching { context.startActivity(appDetailsIntent()) }
        }
    }

    /** 按能力类型直接跳到对应的授权页 */
    fun launchGrantFor(capability: MissingCapability) {
        launchSettings(
            when (capability) {
                MissingCapability.USAGE_STATS -> usageStatsSettingsIntent()
                MissingCapability.ALL_FILES -> allFilesAccessIntent()
                // 通知权限：Android 13+ 跳到通知专项页（不是应用详情页），
                // 否则用户得从详情页再点一次"通知"。低版本没有专项页，
                // 回落应用详情。
                MissingCapability.NOTIFICATIONS ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    } else {
                        appDetailsIntent()
                    }
            }
        )
    }

    // ============================================================
    // 汇总
    // ============================================================

    /** 当前缺失的能力清单（UI 用它决定显示哪些授权引导卡） */
    fun missingCapabilities(): List<MissingCapability> = buildList {
        if (!hasUsageStats()) add(MissingCapability.USAGE_STATS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !hasAllFilesAccess()) {
            add(MissingCapability.ALL_FILES)
        }
        if (!hasNotifications()) add(MissingCapability.NOTIFICATIONS)
    }
}

/** 缺失的能力 —— 每一项都能映射到一个"用户可以去点的按钮" */
enum class MissingCapability {
    /** 使用情况访问：判定不常用应用 */
    USAGE_STATS,

    /** 所有文件访问：让内核能完整扫描 */
    ALL_FILES,

    /** 通知：长任务进度与结果提醒 */
    NOTIFICATIONS,
}
