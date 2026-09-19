package com.novacare.core.system

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.novacare.core.model.AppInfo
import com.novacare.core.model.AppUsageStats
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 已安装应用仓库
 *
 * 占用数据来自 StorageStatsManager（真实），最后使用时间来自 UsageStatsManager（真实）。
 * 两者都拿不到时如实为 0 / null —— 上层不得据此编造。
 */
@Singleton
class AppRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageStats: StorageStatsSource,
    private val usageStats: UsageStatsSource,
) {

    suspend fun loadInstalledApps(nowMs: Long): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val packages: List<PackageInfo> = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(0)
            }
        }.getOrDefault(emptyList())

        val usage = usageStats.query(nowMs)

        packages.mapNotNull { info ->
            val pkg = info.packageName ?: return@mapNotNull null
            val appInfo = info.applicationInfo ?: return@mapNotNull null
            val label = runCatching { pm.getApplicationLabel(appInfo).toString() }
                .getOrDefault(pkg)
            val stats = storageStats.queryStats(pkg)
            val usageInfo: AppUsageStats? = usage[pkg]

            AppInfo(
                packageName = pkg,
                label = label,
                isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0,
                sizeBytes = (stats?.dataBytes ?: 0L) + (stats?.cacheBytes ?: 0L),
                cacheBytes = stats?.cacheBytes ?: 0L,
                dataBytes = stats?.dataBytes ?: 0L,
                versionName = info.versionName ?: "",
                targetSdk = appInfo.targetSdkVersion,
                installTimeEpochMs = info.firstInstallTime,
                updateTimeEpochMs = info.lastUpdateTime,
                lastUsedEpochMs = usageInfo?.lastUsedEpochMs?.takeIf { it > 0 },
            )
        }
    }

    suspend fun loadUsage(nowMs: Long): Map<String, AppUsageStats> =
        withContext(Dispatchers.IO) { usageStats.query(nowMs) }
}
