package com.novacare.core.system

import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 通过 Shizuku 执行 shell 命令（ADB 级权限）
 *
 * 背景：Shizuku 13.x 的 `Shizuku.newProcess(...)` 是 **private**，
 * 公开 API 并未提供「直接起进程」的入口（官方推荐绑定 User Service，需额外 AIDL）。
 * 因此这里以反射调用该内部方法，并做严格降级：
 * - 未授权 / 反射失败 / 命令非零退出 → 一律返回 false
 * - 调用方据此如实提示「不可用」，**绝不假装成功**
 */
@Singleton
class ShizukuShell @Inject constructor() {

    fun isAvailable(): Boolean = runCatching {
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /** @return true = 退出码 0；false = 未授权 / 反射失败 / 命令失败 */
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

            val process = method.invoke(null, arrayOf("sh", "-c", command), null, "/")
                ?: return false

            val waitFor = process.javaClass.getMethod("waitFor")
            val exit = waitFor.invoke(process) as? Int ?: -1
            Log.i(TAG, "cmd='$command' exit=$exit")
            exit == 0
        }.onFailure { Log.w(TAG, "Failed to run: $command", it) }.getOrDefault(false)
    }

    private companion object {
        const val TAG = "ShizukuShell"
    }
}
