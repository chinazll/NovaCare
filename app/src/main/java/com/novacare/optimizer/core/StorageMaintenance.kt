package com.novacare.optimizer.core

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 存储维护：fstrim 闪存整理
 *
 * fstrim 会通知闪存回收无效数据块，长期不执行会让写入变慢。
 * 但执行它需要 **ADB 级权限**（`sm fstrim`），普通应用完全无法调用。
 *
 * 本项目的诚实做法：
 * - 有 Shizuku 授权 → 真正执行 `sm fstrim`
 * - 没有 → **跳过并返回 false**，由调用方记录「未执行」
 *   （绝不像早期版本那样在注释里宣称做了 fstrim、实际什么都没做）
 */
@Singleton
class StorageMaintenance @Inject constructor(
    private val shell: ShizukuShell,
) {

    private companion object {
        const val TAG = "StorageMaintenance"
    }

    /** Shizuku 是否可用（决定 fstrim 能否执行） */
    fun isAvailable(): Boolean = shell.isAvailable()

    /**
     * 执行 fstrim。
     * @return true = 成功；false = 无权限或执行失败
     */
    fun trim(): Boolean {
        if (!shell.isAvailable()) {
            Log.w(TAG, "Shizuku unavailable - cannot run sm fstrim")
            return false
        }
        val ok = shell.run("sm fstrim")
        Log.i(TAG, "sm fstrim executed=$ok")
        return ok
    }
}
