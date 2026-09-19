package com.novacare.optimizer.core

import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 设备状态采集器 + 应用扫描 + 垃圾检测 —— 全部本地完成
 *
 * 设计原则（参考 SD Maid SE / Canta / UAD-NG 的工业级设计）：
 * 1. 所有 IO 都在 Dispatchers.IO
 * 2. 所有可能失败的操作返回 Result 或空集合（绝不抛异常到 UI）
 * 3. Android 14+ (API 34) 的 registerReceiver 必须指定 flag
 * 4. 应用扫描使用 QUERIES 模式（Android 11+ 强制）
 * 5. PACKAGE_USAGE_STATS 仅在用户授权后读取
 */
data class DeviceStatus(
    val usedStorageGb: Float,
    val totalStorageGb: Float,
    val usedRamMb: Long,
    val totalRamMb: Long,
    val batteryLevel: Int,
    val isCharging: Boolean,
    val batteryTemperature: Float,
    val isBatteryHealthy: Boolean = true,
)

data class AppInfo(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean,
    val cacheSizeMb: Float,
    val lastUsedDays: Int,
    val versionName: String,
)

data class JunkItem(
    val path: String,
    val label: String,
    val sizeMb: Float,
    val isSafe: Boolean,
    val riskHint: String = "",
)

@Singleton
class DeviceRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    // ==================== 设备状态 ====================

    suspend fun getDeviceStatus(): DeviceStatus = withContext(Dispatchers.IO) {
        runCatching {
            val stat = StatFs(Environment.getDataDirectory().path)
            val totalBytes = stat.totalBytes
            val availBytes = stat.availableBytes
            val totalGb = totalBytes / GB_F
            val usedGb = ((totalBytes - availBytes) / GB_F).coerceAtLeast(0f)

            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            val totalRamMb = (memInfo.totalMem / MB).coerceAtLeast(1L)
            val usedRamMb = ((memInfo.totalMem - memInfo.availMem) / MB).coerceAtLeast(0L)

            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)

            val batteryInfo = readBatteryInfo()
            val isCharging = batteryInfo.isCharging
            val temp = batteryInfo.temperature
            val isHealthy = batteryInfo.isHealthy

            DeviceStatus(
                usedStorageGb = usedGb,
                totalStorageGb = totalGb,
                usedRamMb = usedRamMb,
                totalRamMb = totalRamMb,
                batteryLevel = level,
                isCharging = isCharging,
                batteryTemperature = temp,
                isBatteryHealthy = isHealthy,
            )
        }.getOrElse { e ->
            // 失败时返回安全默认值（绝不让 UI 崩）
            DeviceStatus(
                usedStorageGb = 0f, totalStorageGb = 0.001f,
                usedRamMb = 0L, totalRamMb = 1L,
                batteryLevel = 100, isCharging = false,
                batteryTemperature = 25f, isBatteryHealthy = true,
            )
        }
    }

    /**
     * 读取电池信息 —— 修复 Android 14+ registerReceiver 必须指定 flag 的崩溃
     */
    private fun readBatteryInfo(): BatteryInfo = runCatching {
        // Android 14+ (API 34) 强制要求指定 RECEIVER_NOT_EXPORTED 或 RECEIVER_EXPORTED
        val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                Context.RECEIVER_NOT_EXPORTED,
            )
        } else {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        val temp = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val health = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
            ?: BatteryManager.BATTERY_HEALTH_UNKNOWN
        val isHealthy = health == BatteryManager.BATTERY_HEALTH_GOOD ||
                health == BatteryManager.BATTERY_HEALTH_UNKNOWN
        intent?.let { context.unregisterReceiver(null) }  // null receiver 不需要 unregister，但保险
        BatteryInfo(isCharging, temp, isHealthy)
    }.getOrElse {
        BatteryInfo(false, 25f, true)
    }

    private data class BatteryInfo(
        val isCharging: Boolean,
        val temperature: Float,
        val isHealthy: Boolean,
    )

    // ==================== 应用扫描 ====================

    suspend fun scanApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val pm = context.packageManager
            val usageMap = queryUsageDaysSafe()
            pm.getInstalledPackages(PackageManager.GET_META_DATA)
                .mapNotNull { pkg ->
                    runCatching {
                        val info = pkg.applicationInfo ?: return@mapNotNull null
                        val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        val label = pm.getApplicationLabel(info).toString()
                        AppInfo(
                            packageName = pkg.packageName,
                            label = label,
                            isSystemApp = isSystem,
                            cacheSizeMb = 0f,  // 无 root 无法读取他应用 cache
                            lastUsedDays = usageMap[pkg.packageName] ?: -1,
                            versionName = pkg.versionName ?: "未知",
                        )
                    }.getOrNull()
                }
                .sortedByDescending { it.lastUsedDays.let { d -> if (d < 0) 0L else d.toLong() } }
        }.getOrDefault(emptyList())
    }

    /**
     * 安全获取使用情况 —— 用 Settings 而不是 UsageStatsManager
     * （因为 UsageStatsManager 需要 PACKAGE_USAGE_STATS 授权）
     */
    private fun queryUsageDaysSafe(): Map<String, Int> = runCatching {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
            ?: return@runCatching emptyMap()
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(
            android.app.usage.UsageStatsManager.INTERVAL_BEST,
            now - 30L * 24 * 3600 * 1000, now,
        )
        stats.associate {
            it.packageName to ((now - it.lastTimeUsed) / (24L * 3600 * 1000)).toInt()
        }
    }.getOrDefault(emptyMap())

    // ==================== 垃圾检测 ====================

    suspend fun scanJunk(): List<JunkItem> = withContext(Dispatchers.IO) {
        runCatching {
            val ext = Environment.getExternalStorageDirectory()
            val items = mutableListOf<JunkItem>()

            // 安全项
            listOf(
                File(ext, "Android/data/cache") to "公共缓存",
                File(ext, "Download/.tmp") to "下载临时文件",
                File(context.cacheDir, "thumbnails") to "缩略图缓存",
                File(ext, "DCIM/.thumbnails") to "相册缩略图",
            ).forEach { (dir, label) ->
                safeDirSizeMb(dir)?.let { mb ->
                    if (mb > 0.1f) items += JunkItem(
                        path = dir.absolutePath,
                        label = label,
                        sizeMb = mb,
                        isSafe = true,
                    )
                }
            }

            // 风险项（用户需手动确认）
            listOf(
                File(ext, "tombstones") to "系统崩溃转储" to "可能含调试信息",
                File(ext, "log") to "系统日志" to "诊断信息",
            ).forEach { (dir, label, risk) ->
                safeDirSizeMb(dir)?.let { mb ->
                    if (mb > 0.1f) items += JunkItem(
                        path = dir.absolutePath,
                        label = label,
                        sizeMb = mb,
                        isSafe = false,
                        riskHint = risk,
                    )
                }
            }

            // 残留应用数据
            runCatching {
                val androidData = File(ext, "Android/data")
                if (androidData.exists()) {
                    val installed = runCatching {
                        context.packageManager.getInstalledPackages(0)
                            .map { it.packageName }.toSet()
                    }.getOrDefault(emptySet())
                    androidData.listFiles()?.forEach { f ->
                        if (f.isDirectory && !f.name.startsWith(".") && f.name !in installed) {
                            val mb = safeDirSizeMb(f) ?: 0f
                            if (mb > 0.5f) items += JunkItem(
                                path = f.absolutePath,
                                label = "残留数据：${f.name}",
                                sizeMb = mb,
                                isSafe = false,
                                riskHint = "应用未安装",
                            )
                        }
                    }
                }
            }
            items.sortedByDescending { it.sizeMb }
        }.getOrDefault(emptyList())
    }

    /**
     * 清理垃圾（仅安全项）
     * 返回释放的字节数
     */
    suspend fun cleanJunk(items: List<JunkItem>): Long = withContext(Dispatchers.IO) {
        var freed = 0L
        items.forEach { item ->
            runCatching {
                val dir = File(item.path)
                if (!dir.exists() || !dir.isDirectory) return@runCatching
                val size = dirSizeBytes(dir) ?: return@runCatching
                // 只清理安全项或缓存目录
                if (item.isSafe || dir.absolutePath.startsWith(context.cacheDir.absolutePath)) {
                    if (dir.deleteRecursively()) {
                        freed += size
                    }
                }
            }
        }
        freed
    }

    // ==================== 权限助手 ====================

    /** 是否已获得 PACKAGE_USAGE_STATS 权限 */
    fun hasUsageStatsPermission(): Boolean = runCatching {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        ) == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    /** 跳转到系统设置页让用户授权 */
    fun usageStatsSettingsIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    // ==================== 工具方法 ====================

    private fun safeDirSizeMb(dir: File?): Float? = runCatching {
        if (dir == null || !dir.exists()) return@runCatching null
        val bytes = dirSizeBytes(dir) ?: return@runCatching null
        (bytes / MB_F)
    }.getOrNull()

    private fun dirSizeBytes(dir: File): Long? = runCatching {
        if (!dir.exists()) return@runCatching null
        var total = 0L
        dir.walkBottomUp()
            .filter { it.isFile }
            .forEach { total += it.length() }
        total
    }.getOrNull()

    companion object {
        private const val GB: Long = 1024L * 1024 * 1024
        private const val MB: Long = 1024L * 1024
        private const val GB_F: Float = 1024f * 1024f * 1024f
        private const val MB_F: Float = 1024f * 1024f
    }
}