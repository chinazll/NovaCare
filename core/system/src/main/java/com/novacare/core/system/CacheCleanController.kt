package com.novacare.core.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 缓存清理执行器
 *
 * 硬约束（蓝图 §4.5.4 F1）：Android **没有公开 API 能直接清第三方 App 缓存**
 * （deleteApplicationCacheFiles 已移除/受限）。
 *
 * 因此：
 * - 普通模式：引导到系统设置页 → 用户点「清除缓存」（官方路径）
 * - 高级模式：Shizuku `pm clear`（真实可用，但会清空数据，必须二次确认）
 *
 * AI 只负责「删哪个 / 删多少 / 代价多大」的决策，执行要么用户确认、要么权限内。
 */
@Singleton
class CacheCleanController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shell: ShizukuShell,
) {

    enum class Mode { GUIDE_ONLY, SHIZUKU }

    fun guideToSettings(packageName: String): Boolean {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }

    /**
     * 高级模式清缓存。
     * 注意：`pm clear` 会同时清除数据 —— 因此只对用户**明确勾选**的目标执行。
     */
    fun clearWithShizuku(packageName: String): Boolean =
        shell.run("pm clear $packageName")
}
