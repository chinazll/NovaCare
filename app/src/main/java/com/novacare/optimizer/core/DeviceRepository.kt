package com.novacare.optimizer.core

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 设备状态采集器 —— 全部本地完成，零网络
 */
data class DeviceStatus(
    val usedStorageGb: Float,
    val totalStorageGb: Float,
    val usedRamMb: Long,
    val totalRamMb: Long,
    val batteryLevel: Int,
    val isCharging: Boolean,
    val batteryTemperature: Float,
)

data class AppInfo(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean,
    val cacheSizeMb: Float,
    val lastUsedDays: Int,
)

@Singleton
class DeviceRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun getDeviceStatus(): DeviceStatus = withContext(Dispatchers.IO) {
        val stat = StatFs(Environment.getDataDirectory().path)
        val totalGb = stat.totalBytes / GB
        val availGb = stat.availableBytes / GB

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val totalRamMb = memInfo.totalMem / MB
        val usedRamMb = (memInfo.totalMem - memInfo.availMem) / MB

        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val temp = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_TEMPERATURE) / 10f
        val charging = context.registerReceiver(null, Intent.ACTION_BATTERY_CHANGED)
            ?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            ?.let { it == BatteryManager.BATTERY_STATUS_CHARGING } ?: false

        DeviceStatus(
            usedStorageGb = totalGb - availGb,
            totalStorageGb = totalGb,
            usedRamMb = usedRamMb,
            totalRamMb = totalRamMb,
            batteryLevel = level,
            isCharging = charging,
            batteryTemperature = temp,
        )
    }

    /**
     * 存储分类分析（借鉴 SD Maid StorageAnalyzer 思路）：
     * 按公共目录归类媒体/文档/应用/系统
     */
    data class StorageCategory(val name: String, val sizeGb: Float, val color: Long)

    suspend fun analyzeStorage(): List<StorageCategory> = withContext(Dispatchers.IO) {
        val status = getDeviceStatus()
        val ext = Environment.getExternalStorageDirectory()

        fun dirSize(dir: File?): Long {
            if (dir == null || !dir.exists()) return 0
            return dir.walkBottomUp().filter { it.isFile }
                .fold(0L) { acc, f -> acc + f.length() }
        }

        val pictures = dirSize(File(ext, "DCIM")) + dirSize(File(ext, "Pictures"))
        val videos = dirSize(File(ext, "Movies"))
        val music = dirSize(File(ext, "Music")) + dirSize(File(ext, "Podcasts"))
        val docs = dirSize(File(ext, "Documents")) + dirSize(File(ext, "Download"))
        val other = ((status.usedStorageGb * GB) - pictures - videos - music - docs)
            .coerceAtLeast(0L)

        listOf(
            StorageCategory("照片", pictures / GB, 0xFF5B8DEF),
            StorageCategory("视频", videos / GB, 0xFF9B6BDF),
            StorageCategory("音频", music / GB, 0xFFE8912D),
            StorageCategory("文档", docs / GB, 0xFF2FA36B),
            StorageCategory("应用与系统", other / GB, 0xFF8A8F98),
        )
    }

    /**
     * 应用扫描（借鉴 AppManager + UAD 分级思路）
     */
    suspend fun scanApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val usageMap = queryUsageStatsDays()
        pm.getInstalledPackages(PackageManager.GET_META_DATA).map { pkg ->
            val appInfo = pkg.applicationInfo
            val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            // 缓存目录体积估算（免 Root 场景下的公开目录 + DataDir 采样）
            val cacheBytes = try {
                dirSizeOrNull(File(appInfo.cacheDir ?: "")) ?: 0L
            } catch (_: Exception) { 0L }
            AppInfo(
                packageName = pkg.packageName,
                label = pm.getApplicationLabel(pkg.applicationInfo).toString(),
                isSystemApp = isSystem,
                cacheSizeMb = cacheBytes / MBf,
                lastUsedDays = usageMap[pkg.packageName] ?: -1,
            )
        }.sortedByDescending { it.cacheSizeMb }
    }

    private fun dirSizeOrNull(dir: File): Long? {
        if (!dir.exists()) return null
        return dir.walkBottomUp().filter { it.isFile }.fold(0L) { acc, f -> acc + f.length() }
    }

    private fun queryUsageStatsDays(): Map<String, Int> {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE)
                as android.app.usage.UsageStatsManager
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(
                android.app.usage.UsageStatsManager.INTERVAL_BEST,
                now - 30L * 24 * 3600 * 1000, now,
            )
            stats.associate {
                it.packageName to ((now - it.lastTimeUsed) / (24L * 3600 * 1000)).toInt()
            }
        } catch (_: SecurityException) {
            emptyMap() // 未授予使用情况权限时降级
        }
    }

    /**
     * 垃圾扫描（借鉴 SD Maid AppCleaner）：
     * 公共缓存目录 + 已卸载应用残留目录（CorpseFinder 简化版）
     */
    data class JunkItem(val path: String, val label: String, val sizeMb: Float, val isSafe: Boolean)

    suspend fun scanJunk(): List<JunkItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<JunkItem>()
        val ext = Environment.getExternalStorageDirectory()

        data class JunkCandidate(val dir: File, val label: String, val isSafe: Boolean)
        val candidates = listOf(
            JunkCandidate(File(ext, "Android/data/cache"), "公共缓存", true),
            JunkCandidate(File(ext, "Download/.tmp"), "下载临时文件", true),
            JunkCandidate(File(context.cacheDir, "thumbnails"), "缩略图缓存", true),
            JunkCandidate(File(ext, "DCIM/.thumbnails"), "相册缩略图", true),
            JunkCandidate(File(ext, "tombstones"), "系统崩溃转储", false),
            JunkCandidate(File(ext, "log"), "应用日志", false),
        )
        for (c in candidates) {
            val size = dirSizeOrNull(c.dir) ?: continue
            if (size > 0) {
                items += JunkItem(c.dir.absolutePath, c.label, size / MBf, c.isSafe)
            }
        }
        // 残留检测：Android/data 下无对应已安装应用的目录
        try {
            val dataDir = File(ext, "Android/data")
            val installed = context.packageManager.getInstalledPackages(0)
                .map { it.packageName }.toSet()
            dataDir.listFiles()?.forEach { f ->
                if (f.isDirectory && f.name.startsWith(".") == false) {
                    val pkg = f.name.removeSuffix("")
                    if (pkg !in installed && pkg.isNotBlank()) {
                        val size = dirSizeOrNull(f) ?: 0L
                        if (size > 512 * 1024) {
                            items += JunkItem(f.absolutePath, "残留数据：$pkg", size / MBf, false)
                        }
                    }
                }
            }
        } catch (_: Exception) { /* 部分系统限制访问 Android/data，静默降级 */ }
        items.sortedByDescending { it.sizeMb }
    }

    suspend fun cleanJunk(items: List<JunkItem>): Long = withContext(Dispatchers.IO) {
        var freed = 0L
        for (item in items) {
            val dir = File(item.path)
            val size = dirSizeOrNull(dir) ?: continue
            if (item.isSafe || dir.absolutePath.startsWith(context.cacheDir.absolutePath)) {
                dir.deleteRecursively()
                freed += size
            }
        }
        freed
    }

    companion object {
        const val GB = 1024L * 1024 * 1024
        const val MB = 1024L * 1024
        const val MBf = 1024f * 1024
    }
}
