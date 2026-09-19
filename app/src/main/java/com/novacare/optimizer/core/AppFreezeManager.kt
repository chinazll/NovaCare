package com.novacare.optimizer.core

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton

private val Context.freezeDataStore: DataStore<Preferences> by preferencesDataStore(name = "frozen_apps")

/**
 * 应用冻结 / 解冻管理器（基于 Shizuku 执行 `pm suspend`）
 *
 * 修复 P1-1：此前 Shizuku 依赖被引入但**从未使用**，AppsScreen 的雪花图标只是装饰，
 * 点击没有任何反应，却在 README / DESIGN_SPEC / INSTALL / PRIVACY 中大肆宣传「冻结应用」。
 *
 * 现在的实现：
 * - 冻结状态持久化到 DataStore（重启后仍然记得）
 * - 通过 Shizuku 以 ADB 权限执行 `pm suspend <pkg>` / `pm unsuspend <pkg>`
 * - Shizuku 未安装 / 未授权时，[isShizukuAvailable] 返回 false，UI 明确提示「需要 Shizuku」
 *   （而不是静默失败或假装成功）
 *
 * 为什么用 `pm suspend` 而不是卸载：
 * `pm suspend` 是**可逆**的 —— 应用被冻结后从启动器消失、无法后台运行，
 * 但数据与 APK 完全保留，随时可解冻。这比卸载安全得多（UAD-NG / Canta 同样采用此策略）。
 */
@Singleton
class AppFreezeManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private companion object {
        const val TAG = "FreezeManager"
        val FROZEN_KEY = stringSetPreferencesKey("frozen_packages")
    }

    /** 已冻结包名集合（持久化） */
    val frozenPackages: Flow<Set<String>> = context.freezeDataStore.data
        .map { prefs -> prefs[FROZEN_KEY] ?: emptySet() }

    suspend fun frozenCount(): Int = runCatching { frozenPackages.first().size }.getOrDefault(0)

    suspend fun isFrozen(pkg: String): Boolean =
        runCatching { frozenPackages.first().contains(pkg) }.getOrDefault(false)

    /** Shizuku 服务是否已绑定且已授权 */
    fun isShizukuAvailable(): Boolean = runCatching {
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /**
     * 冻结应用。
     * @return true = 成功；false = 失败（未授权 / 命令失败 / 异常）
     */
    suspend fun freeze(pkg: String): Boolean = runCommand("pm suspend $pkg", pkg, freeze = true)

    /** 解冻应用 */
    suspend fun unfreeze(pkg: String): Boolean = runCommand("pm unsuspend $pkg", pkg, freeze = false)

    private suspend fun runCommand(command: String, pkg: String, freeze: Boolean): Boolean {
        if (!isShizukuAvailable()) {
            Log.w(TAG, "Shizuku unavailable — cannot ${if (freeze) "freeze" else "unfreeze"} $pkg")
            return false
        }
        val ok = runCatching {
            val process = Shizuku.newProcess(
                arrayOf("sh", "-c", command),
                null,
                "/",
            )
            val exit = process.waitFor()
            exit == 0
        }.onFailure { Log.w(TAG, "Command failed: $command", it) }.getOrDefault(false)

        if (ok) {
            context.freezeDataStore.edit { prefs ->
                val current = prefs[FROZEN_KEY] ?: emptySet()
                prefs[FROZEN_KEY] = if (freeze) current + pkg else current - pkg
            }
        }
        return ok
    }
}
