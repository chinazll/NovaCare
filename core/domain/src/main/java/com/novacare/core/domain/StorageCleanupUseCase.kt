package com.novacare.core.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 删除用户勾选的存储项，并给出**真实**的释放量。
 *
 * 两条铁律：
 * 1. 释放量 = 删除前实测的文件大小之和，删除后校验文件确实不存在。
 *    绝不"按选中项估算"——没删掉的不算数，这是最容易注水的数字。
 * 2. 删不掉就如实返回失败路径，由 UI 引导去授权，而不是假装成功。
 *
 * 为什么不用 [com.novacare.core.system.RecycleBin]：
 *   回收站的实现是"先复制再删源"。对 GB 级视频来说，copy 会翻倍 I/O、
 *   还可能撑爆 App 的私有目录（filesDir）。大文件清理场景下这个代价不可接受，
 *   因此这里走直接删除，UI 侧用显式确认弹窗承担"不可撤销"的告知责任。
 */
@Singleton
class StorageCleanupUseCase @Inject constructor() {

    suspend operator fun invoke(paths: List<String>): CleanupOutcome =
        withContext(Dispatchers.IO) {
            val deleted = ArrayList<String>()
            val failed = ArrayList<String>()
            var freed = 0L

            for (path in paths) {
                val file = File(path)
                if (!file.exists()) {
                    // 已经不在了（被系统或其他应用清掉）—— 不算成功也不算失败，直接跳过
                    continue
                }
                val sizeBefore = runCatching { sizeOf(file) }.getOrDefault(0L)
                val removed = runCatching {
                    if (file.isDirectory) file.deleteRecursively() else file.delete()
                }.getOrDefault(false)

                if (removed && !File(path).exists()) {
                    deleted += path
                    freed += sizeBefore
                } else {
                    failed += path
                }
            }
            CleanupOutcome(deleted = deleted, failed = failed, freedBytes = freed)
        }

    private fun sizeOf(file: File): Long {
        if (file.isFile) return file.length()
        var sum = 0L
        file.walkTopDown()
            .onFail { _, _ -> }
            .forEach { if (it.isFile) sum += it.length() }
        return sum
    }
}

data class CleanupOutcome(
    /** 确认已删除的路径 */
    val deleted: List<String>,
    /** 删除失败的路径（多数是权限问题，UI 需引导授权） */
    val failed: List<String>,
    /** 真实释放字节数（只统计确认删除成功的项） */
    val freedBytes: Long,
)
