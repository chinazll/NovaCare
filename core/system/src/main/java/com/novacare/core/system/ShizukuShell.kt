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
 *
 * ============================================================
 * 【v0.7.5 —— 补上 Shizuku 授权入口】
 * 上一版 [isAvailable] 只检查、不授权：用户永远停在"Shizuku 不可用"，
 * 没有任何入口去请求授权 —— 缓存自动化（pm trim-caches）和冻结功能
 * 因此**静默失效**。这是"功能没实际价值"的一部分根因。
 *
 * 这一版补齐官方授权流：
 *   - [requestPermission]：`Shizuku.requestPermission(code)` 弹出系统授权框
 *   - [setPermissionListener]：注册 `OnRequestPermissionResultListener` 回调
 *   - [ensureListenerRegistered]：每次查可用性前确保监听器已注册
 * ============================================================
 */
@Singleton
class ShizukuShell @Inject constructor() {

    // 由 Shizuku binder 回调线程读写、主线程写入，必须 volatile 保证跨线程可见
    @Volatile
    private var permissionListener: ((Boolean) -> Unit)? = null
    private val listener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        permissionListener?.invoke(grantResult == PackageManager.PERMISSION_GRANTED)
        permissionListener = null
    }

    fun isAvailable(): Boolean = runCatching {
        ensureListenerRegistered()
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /** Shizuku 进程是否在运行（未授权时也能 ping 通，用于区分"没装"和"没授权"） */
    fun isAlive(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    /**
     * 请求 Shizuku 授权（弹出系统授权框）。
     *
     * @param onResult 授权结果回调（在结果返回前，多次调用会覆盖前一个回调）
     * @return true = 已发起请求；false = Shizuku 服务不可达（可能未启动）
     */
    fun requestPermission(onResult: (Boolean) -> Unit): Boolean {
        return runCatching {
            ensureListenerRegistered()
            permissionListener = onResult
            // 已授权时 requestPermission 仍会触发 result listener 并返回 granted
            Shizuku.requestPermission(REQUEST_CODE)
            true
        }.onFailure { Log.w(TAG, "requestPermission failed", it) }.getOrDefault(false)
    }

    private fun ensureListenerRegistered() {
        try {
            Shizuku.addRequestPermissionResultListener(listener)
        } catch (_: Throwable) {
            // 重复 add 会抛异常，忽略即可
        }
    }

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

            // 必须把子进程的 stdout/stderr 重定向掉：shell 命令输出一旦超过管道缓冲区，
            // 子进程会阻塞在 write 上，waitFor() 永不返回 → 调用线程永久挂死。
            // 本 API 只关心退出码，不消费输出，因此直接丢弃。
            val process = method.invoke(
                null,
                arrayOf("sh", "-c", "$command >/dev/null 2>&1"),
                null,
                "/",
            ) ?: return false

            try {
                val waitFor = process.javaClass.getMethod("waitFor")
                val exit = waitFor.invoke(process) as? Int ?: -1
                Log.i(TAG, "cmd='$command' exit=$exit")
                exit == 0
            } finally {
                // 释放进程占用的管道 fd
                runCatching { process.javaClass.getMethod("destroy").invoke(process) }
            }
        }.onFailure { Log.w(TAG, "Failed to run: $command", it) }.getOrDefault(false)
    }

    private companion object {
        const val TAG = "ShizukuShell"
        const val REQUEST_CODE = 1000
    }
}
