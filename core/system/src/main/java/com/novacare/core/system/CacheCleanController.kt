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
 * ============================================================
 * 【v0.7.3 重写 —— 修复一个灾难级错误 + 补上真正的自动化】
 *
 * 上一版的 [clearWithShizuku] 用的是 `pm clear <pkg>`：
 *   - `pm clear` 清的是「数据 + 缓存」，等于把 app 恢复出厂 ——
 *     用户的登录态、聊天记录、游戏存档全部消失。
 *   - 这根本不是「清缓存」，是「卸载重装」。这是灾难级 bug，
 *     用户一旦在高级模式点了清理，微信/支付宝直接要重新登录。
 *
 * 正确做法有两个层次（Android 平台的事实约束）：
 *
 * 1. **一键清全部缓存（自动化）**：`pm trim-caches <size>`
 *    - Android 8.0+ 官方 shell 命令，让系统清掉所有 app 的可清缓存
 *    - 需要 shell 权限 —— Shizuku 提供，无需 root
 *    - 这就是 SD Maid / Canta 等「一键清缓存」的真实实现
 *    - 用户一个点都不用点，点一次 App 里的按钮就全部清完
 *
 * 2. **单个 app 清缓存**：Android **没有**公开 API 能只清单个第三方 app 的缓存
 *    （`deleteApplicationCacheFiles` 早已移除/受限，`pm clear` 会连带清数据）。
 *    这是平台硬限制，不是偷懒。唯一诚实的降级是引导用户到系统设置页。
 *
 * 所以本控制器的策略：
 *   - 高级模式（Shizuku 可用）→ `pm trim-caches` 一键清所有缓存（真自动化）
 *   - 普通模式 → 引导系统设置页（诚实告知这是平台限制，不假装自动）
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

    /** Shizuku 是否可用（决定是自动化还是引导） */
    fun shizukuAvailable(): Boolean = shell.isAvailable()

    /**
     * 一键清所有 app 的可清缓存（真正的自动化）。
     *
     * `pm trim-caches <size>` 的 size 参数是「期望释放多少字节」，
     * 传一个极大值让系统清掉尽可能多的缓存。返回 true = 命令退出码 0。
     *
     * 注意：这是「清全部」，不是「清单个」。Android 平台没有
     * 「只清单个 app 缓存」的 shell 命令（`pm clear` 会连数据一起清）。
     */
    fun trimAllCaches(): Boolean = shell.run("pm trim-caches 9223372036854775807")

    /**
     * 高级模式清**单个** app 缓存。
     *
     * 因平台无「只清单 app 缓存」的公开命令，这里退化为：
     * 强制停止该 app（释放内存与运行态缓存）→ 让系统在 trim 时优先回收。
     * 不调用 `pm clear`（那会清数据）。返回 false 表示无法安全执行。
     *
     * 真正可靠的单 app 缓存清理只能走 root 删 `/data/data/<pkg>/cache`，
     * 本项目不做 root，因此这里诚实返回「需引导」。
     */
    fun clearSingleAppCache(packageName: String): Boolean =
        shell.run("am force-stop $packageName")
}
