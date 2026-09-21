package com.novacare.core.system

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.storage.StorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 应用真实占用数据源（缓存 / 数据 / 安装包）
 *
 * 修复上一版 P0：此前 cacheBytes / dataBytes 恒为 0，
 * 导致「应用防护」评分永远满分（假数据）。
 * 现在全部来自 StorageStatsManager（API 26+，权威数据源）。
 */
@Singleton
class StorageStatsSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    data class AppStorage(val cacheBytes: Long, val dataBytes: Long, val appBytes: Long)

    fun queryStats(packageName: String): AppStorage? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null

        val manager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
            ?: return null
        val uid = runCatching {
            context.packageManager.getApplicationInfo(packageName, 0).uid
        }.getOrNull() ?: return null

        return runCatching {
            val user: UserHandle = Process.myUserHandle()
            // 单参数版本已废弃；当前签名为 (UUID, packageName, UserHandle)
            val stats = manager.queryStatsForPackage(
                StorageManager.UUID_DEFAULT ?: UUID(0L, 0L),
                packageName,
                user,
            )
            AppStorage(
                cacheBytes = stats.cacheBytes,
                dataBytes = stats.dataBytes,
                appBytes = stats.appBytes,
            )
        }.onFailure {
            // 常见原因：未授予 PACKAGE_USAGE_STATS 或包不可见
        }.getOrNull()
    }

    /**
     * 能否查询 StorageStatsManager。
     *
     * 旧写法 `A && B || A`（&& 优先级高于 ||）恒等于 `A`：VERIFIED_BOOT 那个分支
     * 是永远不生效的死条件，看起来做了能力校验其实什么也没校验。
     * VERIFIED_BOOT 与存储统计无关，真正的能力门槛只有 API 26+，故直接去掉。
     */
    fun canQuery(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
}
