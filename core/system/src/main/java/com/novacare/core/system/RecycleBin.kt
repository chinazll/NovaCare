package com.novacare.core.system

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 回收站：删除 = 移动到临时目录，7 天内可撤销
 *
 * 蓝图 §4.5.2 硬约束：全程可撤销，不直接 delete()。
 */
@Singleton
class RecycleBin @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val root: File
        get() = File(context.filesDir, "recycle").apply { mkdirs() }

    /** @return 回收站中的目标路径；失败返回 null（此时调用方不得删除原文件） */
    fun moveToBin(path: String): String? {
        val src = File(path)
        if (!src.exists()) return null
        val stamp = System.currentTimeMillis()
        val destDir = File(root, stamp.toString())
        if (!destDir.mkdirs()) return null
        val dest = File(destDir, src.name)
        return runCatching {
            src.copyTo(dest, overwrite = true)
            src.deleteRecursively()
            dest.absolutePath
        }.getOrNull()
    }

    fun restore(binPath: String, originalPath: String): Boolean {
        val src = File(binPath)
        if (!src.exists()) return false
        return runCatching {
            val dest = File(originalPath)
            dest.parentFile?.mkdirs()
            src.copyTo(dest, overwrite = true)
            src.deleteRecursively()
            true
        }.getOrDefault(false)
    }

    /** 清理超过 7 天的回收站内容 */
    fun purgeExpired(nowMs: Long = System.currentTimeMillis()): Int {
        val expiry = nowMs - RETENTION_MS
        var removed = 0
        root.listFiles()?.forEach { dir ->
            val ts = dir.name.toLongOrNull() ?: return@forEach
            if (ts < expiry) {
                if (dir.deleteRecursively()) removed++
            }
        }
        return removed
    }

    private companion object {
        const val RETENTION_MS = 7L * 24 * 60 * 60 * 1000
    }
}
