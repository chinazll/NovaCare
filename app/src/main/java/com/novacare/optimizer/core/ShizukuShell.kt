package com.novacare.optimizer.core

import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 通过 Shizuku 执行 shell 命令（ADB 级权限）
 *
 * 背景：Shizuku 的 `Shizuku.newProcess(...)` 在 13.x 中是 **private**，
 * 公开 API 并未提供「直接起进程」的入口（官方推荐的做法是绑定 User Service，
 * 那需要额外写 AIDL，代价过大）。
 *
 * 因此这里以反射调用该内部方法，并做严格降级：
 * - 反射失败 / 未授权 / 命令非零退出 → 一律返回 false
 * - 调用方（[AppFreezeManager]、[StorageMaintenance]）据此如实提示「不可用」，
 *   **绝不假装成功**
 */
@Singleton
class ShizukuShell @Inject constructor() {

    private companion object {
        const val TAG = "ShizukuShell"
    }

    /** Shizuku 是否可用（已绑定服务 + 已授权） */
    fun isAvailable(): Boolean = runCatching {
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /**
     * 执行命令。
     * @return true = 退出码 0；false = 未授权、反射失败或命令失败
     */
    fun run(command: String): Boolean {
        if (!isAvailable()) {
            Log.w(TAG, "Shizuku unavailable, skip: $command")
            return false
        }
        return runCatching {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            ).apply { isAccessible = true }

            val process = method.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                "/",
            ) ?: return false

            // 不假设返回值的具体类型：反射取 waitFor 最稳妥
            val waitFor = process.javaClass.getMethod("waitFor")
            val exit = waitFor.invoke(process) as? Int ?: -1
            Log.i(TAG, "cmd='$command' exit=$exit")
            exit == 0
        }.onFailure { Log.w(TAG, "Failed to run: $command", it) }.getOrDefault(false)
    }
}
