package com.novacare.optimizer.core

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
        val totalBytes = stat.totalBytes
        val availBytes = stat.availableBytes
        val totalGb = totalBytes / GB_F
        val usedGb = (totalBytes - availBytes) / GB_F

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val totalRamMb = memInfo.totalMem / MB
        val usedRamMb = (memInfo.totalMem - memInfo.availMem) / MB

        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)?.div(10f) ?: 0f
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        DeviceStatus(
            usedStorageGb = usedGb,
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
            if (dir == null || !dir.exists()) return 0L
            return dir.walkBottomUp().filter { it.isFile }
                .fold(0L) { acc, f -> acc + f.length() }
        }

        val pictures = dirSize(File(ext, "DCIM")) + dirSize(File(ext, "Pictures"))
        val videos = dirSize(File(ext, "Movies"))
        val music = dirSize(File(ext, "Music")) + dirSize(File(ext, "Podcasts"))
        val docs = dirSize(File(ext, "Documents")) + dirSize(File(ext, "Download"))
        val usedBytes = (status.usedStorageGb * GB_F).toLong()
        val other = (usedBytes - pictures - videos - music - docs).coerceAtLeast(0L)

        listOf(
            StorageCategory("照片", pictures / GB_F, 0xFF5B8DEF),
            StorageCategory("视频", videos / GB_F, 0xFF9B6BDF),
            StorageCategory("音频", music / GB_F, 0xFFE8912D),
            StorageCategory("文档", docs / GB_F, 0xFF2FA36B),
            StorageCategory("应用与系统", other / GB_F, 0xFF8A8F98),
        )
    }

    /**
     * 应用扫描（借鉴 AppManager + UAD 分级思路）
     */
    suspend fun scanApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val usageMap = queryUsageStatsDays()
        pm.getInstalledPackages(PackageManager.GET_META_DATA).mapNotNull { pkg ->
            val appInfo = pkg.applicationInfo ?: return@mapNotNull null
            val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            // 缓存估算（无 root 时无法读取他应用 cacheDir,显示 0）
            AppInfo(
                packageName = pkg.packageName,
                label = pm.getApplicationLabel(appInfo).toString(),
                isSystemApp = isSystem,
                cacheSizeMb = 0f,
                lastUsedDays = usageMap[pkg.packageName] ?: -1,
            )
        }.sortedByDescending { it.cacheSizeMb }
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
            emptyMap()
        }
    }

    /**
     * 垃圾扫描（借鉴 SD Maid AppCleaner）：
     * 公共缓存目录 + 已卸载应用残留目录（CorpseFinder 简化版）
     */
    data class JunkItem(val path: String, val label: String, val sizeMb: Float, val isSafe: Boolean)

    data class JunkCandidate(val dir: File, val label: String, val isSafe: Boolean)

    private fun dirSizeMb(dir: File?): Float? {
        if (dir == null || !dir.exists()) return null
        return dir.walkBottomUp().filter { it.isFile }
            .fold(0L) { acc, f -> acc + f.length() } / MB_F
    }

    suspend fun scanJunk(): List<JunkItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<JunkItem>()
        val ext = Environment.getExternalStorageDirectory()

        val candidates = listOf(
            JunkCandidate(File(ext, "Android/data/cache"), "公共缓存", true),
            JunkCandidate(File(ext, "Download/.tmp"), "下载临时文件", true),
            JunkCandidate(File(context.cacheDir, "thumbnails"), "缩略图缓存", true),
            JunkCandidate(File(ext, "DCIM/.thumbnails"), "相册缩略图", true),
            JunkCandidate(File(ext, "tombstones"), "系统崩溃转储", false),
            JunkCandidate(File(ext, "log"), "应用日志", false),
        )
        for (c in candidates) {
            val sizeMb = dirSizeMb(c.dir) ?: continue
            if (sizeMb > 0) {
                items += JunkItem(c.dir.absolutePath, c.label, sizeMb, c.isSafe)
            }
        }
        // 残留检测
        try {
            val dataDir = File(ext, "Android/data")
            val installed = context.packageManager.getInstalledPackages(0)
                .map { it.packageName }.toSet()
            dataDir.listFiles()?.forEach { f ->
                if (f.isDirectory && !f.name.startsWith(".")) {
                    val pkg = f.name
                    if (pkg !in installed && pkg.isNotBlank()) {
                        val sizeMb = dirSizeMb(f) ?: 0f
                        if (sizeMb > 0.5f) {
                            items += JunkItem(f.absolutePath, "残留数据：$pkg", sizeMb, false)
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
            val size = dirSizeMb(dir) ?: continue
            if (item.isSafe || dir.absolutePath.startsWith(context.cacheDir.absolutePath)) {
                if (dir.deleteRecursively()) {
                    freed += (size * MB_F).toLong()
                }
            }
        }
        freed
    }

    companion object {
        const val GB: Long = 1024L * 1024 * 1024
        const val MB: Long = 1024L * 1024
        const val GB_F: Float = 1024f * 1024f * 1024f
        const val MB_F: Float = 1024f * 1024f
    }
}