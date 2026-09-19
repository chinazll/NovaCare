package com.novacare.optimizer.core

import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 存储维护：fstrim（闪存整理 / TRIM）
 *
 * 修复 P1-1：README 与 DESIGN_SPEC 宣传「每晚自动 fstrim」，
 * 但 NightlyMaintenanceWorker 里**根本没有 fstrim 调用**，注释是撒谎。
 *
 * 现实约束：
 * `fstrim` 需要 root 或 ADB 权限（系统级操作），普通应用无法直接调用。
 * 因此本项目通过 **Shizuku** 以 ADB 权限执行 `sm fstrim`：
 * - Shizuku 可用 → 真正执行 TRIM，延长闪存寿命、恢复写入性能
 * - Shizuku 不可用 → 明确返回 false，绝不假装执行成功
 *
 * 文档已同步修正：fstrim 为「需 Shizuku 的可选能力」，而非无条件生效的功能。
 */
@Singleton
class StorageMaintenance @Inject constructor() {

    private companion object {
        const val TAG = "StorageMaintenance"
    }

    /** 是否具备执行 TRIM 的条件 */
    fun canTrim(): Boolean = runCatching {
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /**
     * 执行 fstrim。
     * @return true = 已成功下发 TRIM 命令；false = 无权限或执行失败
     */
    suspend fun trim(): Boolean {
        if (!canTrim()) {
            Log.i(TAG, "Shizuku unavailable — skipping fstrim (no fake success)")
            return false
        }
        return runCatching {
            val process = Shizuku.newProcess(arrayOf("sm", "fstrim"), null, "/")
            val exit = process.waitFor()
            Log.i(TAG, "sm fstrim exit=$exit")
            exit == 0
        }.onFailure { Log.w(TAG, "fstrim failed", it) }.getOrDefault(false)
    }
}
