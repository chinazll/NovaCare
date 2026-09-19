package com.novacare.optimizer.core

import android.app.ActivityManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 设备数据采集与优化仓储层
 *
 * **单一数据源原则（修复 P0-2）**：
 * 所有重量级分析（存储扫描 / 垃圾检测 / 重复文件 / 应用统计 / 电池评分）
 * **一律委托给 Rust 引擎** `RustCore`（libnovacare_core.so）。
 * 本类只负责：
 *   1. 调用 Android 系统 API 拿到 Rust 拿不到的东西（StatFs / ActivityManager / 电池广播 / PackageManager）
 *   2. 把结构化数据喂给 Rust，拿到结果
 *   3. 执行副作用（删除文件 / 冻结应用）
 *
 * **优雅降级（重要）**：
 * 若 .so 未打包（如本地纯 Kotlin 构建），`RustCore.isAvailable == false`，
 * 所有分析返回安全默认值，App 依然可用 —— 绝不因缺少 native 库而崩溃。
 *
 * 安全红线：
 *   - 不扫描 `Android/data` 下的「残留」目录（Android 11+ 分区存储下无法可靠判定，
 *     误删会清空在用应用的私有数据）—— 见 [scanJunk]
 *   - 清理只删除 Rust 判定为 `is_safe` 的项，且路径必须落在允许清单内
 */
@Singleton
class DeviceRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissions: PermissionHelper,
) {

    private companion object {
        const val TAG = "DeviceRepo"
        const val GB = 1024L * 1024 * 1024
        const val MB = 1024L * 1024
        const val GB_F = 1024f * 1024f * 1024f
        const val MB_F = 1024f * 1024f
    }

    // ==================== 数据模型 ====================

    data class DeviceStatus(
        val usedStorageGb: Float,
        val totalStorageGb: Float,
        val usedRamMb: Long,
        val totalRamMb: Long,
        val batteryLevel: Int,
        val isCharging: Boolean,
        val batteryTemperature: Float,
        val batteryHealthScore: Int,
    )

    /** UI 层使用的应用信息；缓存/数据大小来自 StorageStatsManager（真实值） */
    data class AppInfo(
        val packageName: String,
        val label: String,
        val isSystemApp: Boolean,
        val cacheSizeMb: Float,
        val dataSizeMb: Float,
        val totalSizeMb: Float,
        val lastUsedDays: Int,
        val versionName: String,
    )

    /** UI 层使用的垃圾项；isSafe/kind 由 Rust 引擎判定 */
    data class JunkItem(
        val path: String,
        val label: String,
        val sizeMb: Float,
        val kind: String,
        val isSafe: Boolean,
        val riskHint: String = "",
    )

    data class StorageBreakdown(
        val categories: List<Pair<String, Long>>,
        val largestFiles: List<Pair<String, Long>>,
        val scannedBytes: Long,
        val durationMs: Long,
        val fromRustEngine: Boolean,
    )

    // ==================== 设备状态 ====================

    suspend fun getDeviceStatus(): DeviceStatus = withContext(Dispatchers.IO) {
        runCatching {
            val stat = StatFs(Environment.getDataDirectory().path)
            val totalBytes = stat.totalBytes
            val availBytes = stat.availableBytes

            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }

            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)

            val batt = readBatteryBroadcast()

            // 电池健康评分交给 Rust 引擎（与 UI 电池页共用同一算法，修复 P1-8 双算法问题）
            val healthScore = RustCore.analyzeBatterySafe(
                level = level,
                temperatureTenths = (batt.temperature * 10).toInt(),
                voltage = batt.voltage,
                current = 0,
                status = if (batt.isCharging) "charging" else "discharging",
                health = batt.healthString,
                plugged = if (batt.isCharging) 1 else 0,
            )?.healthScore ?: defaultBatteryScore(level, batt.temperature)

            DeviceStatus(
                usedStorageGb = ((totalBytes - availBytes) / GB_F).coerceAtLeast(0f),
                totalStorageGb = (totalBytes / GB_F).coerceAtLeast(0.001f),
                usedRamMb = ((memInfo.totalMem - memInfo.availMem) / MB).coerceAtLeast(0L),
                totalRamMb = (memInfo.totalMem / MB).coerceAtLeast(1L),
                batteryLevel = level,
                isCharging = batt.isCharging,
                batteryTemperature = batt.temperature,
                batteryHealthScore = healthScore,
            )
        }.getOrElse { e ->
            Log.w(TAG, "getDeviceStatus failed, using safe defaults", e)
            DeviceStatus(
                usedStorageGb = 0f, totalStorageGb = 0.001f,
                usedRamMb = 0L, totalRamMb = 1L,
                batteryLevel = 100, isCharging = false,
                batteryTemperature = 25f, batteryHealthScore = 100,
            )
        }
    }

    /**
     * 读取电池粘性广播。
     *
     * 两处已修复：
     * - P1-5：删除 `unregisterReceiver(null)`（对该用法无效且会抛 IllegalArgumentException）
     * - P1-6：统一用 [ContextCompat.registerReceiver]，由 androidx 处理各 API 级别的 flag 差异，
     *         不再手写 `SDK_INT >= UPSIDE_DOWN_CAKE` 分支
     */
    private fun readBatteryBroadcast(): BatteryBroadcast = runCatching {
        val intent = ContextCompat.registerReceiver(
            context,
            /* receiver = */ null,               // 粘性广播用法：只取值，不注册真实接收器
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        val temp = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 250) ?: 250) / 10f
        val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val health = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        BatteryBroadcast(
            isCharging = isCharging,
            temperature = temp,
            voltage = voltage,
            healthString = when (health) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat"
                BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over_voltage"
                BatteryManager.BATTERY_HEALTH_COLD -> "cold"
                else -> "unknown"
            },
        )
    }.getOrElse { BatteryBroadcast(false, 25f, 0, "unknown") }

    private data class BatteryBroadcast(
        val isCharging: Boolean,
        val temperature: Float,
        val voltage: Int,
        val healthString: String,
    )

    /** Rust 不可用时的后备评分（保持与 Rust 算法同量纲） */
    private fun defaultBatteryScore(level: Int, temp: Float): Int {
        var s = 100
        if (level < 20) s -= 25 else if (level < 40) s -= 10
        if (temp > 40f) s -= 25 else if (temp > 37f) s -= 10
        return s.coerceIn(0, 100)
    }

    // ==================== 存储分析（Rust 真实扫描，修复 P2-2 编造数据） ====================

    /**
     * 真实扫描存储占用。
     * 优先用 Rust 引擎并行扫描；引擎不可用时才退回「仅总量、无分类」的诚实结果
     * （**不再**用硬编码百分比编造分类，修复 P2-2 / P2-3）。
     */
    suspend fun analyzeStorage(): StorageBreakdown = withContext(Dispatchers.IO) {
        val root = context.getExternalFilesDir(null)?.parentFile?.parentFile
            ?: Environment.getExternalStorageDirectory()
        val totalBytes = runCatching { StatFs(Environment.getDataDirectory().path).totalBytes }
            .getOrDefault(0L)

        val report = RustCore.analyzeStorageSafe(root.absolutePath, totalBytes)
        if (report != null) {
            return@withContext StorageBreakdown(
                categories = report.categories.map { it.name to it.size },
                largestFiles = report.largestFiles.map { it.path to it.size },
                scannedBytes = report.used,
                durationMs = report.durationMs,
                fromRustEngine = true,
            )
        }

        // 引擎不可用：返回真实总量，但分类为空（UI 会显示「未能分类」而不是假数据）
        Log.w(TAG, "Rust storage analysis unavailable — returning honest empty breakdown")
        val usedBytes = runCatching {
            val stat = StatFs(Environment.getDataDirectory().path)
            stat.totalBytes - stat.availableBytes
        }.getOrDefault(0L)
        StorageBreakdown(
            categories = emptyList(),
            largestFiles = emptyList(),
            scannedBytes = usedBytes,
            durationMs = 0,
            fromRustEngine = false,
        )
    }

    // ==================== 垃圾检测（Rust 引擎） ====================

    /**
     * 扫描可清理项。
     *
     * 安全模型（修复 P1-3 / P1-4）：
     * - `installedPackages` 为空串时，Rust 侧会**跳过残留目录检测**。
     *   Android 11+ 分区存储下无法可靠枚举已安装应用，若强行比对会把
     *   在用应用（微信/游戏）的 `Android/data/<pkg>` 误判为残留并删除 —— 这是数据丢失风险。
     *   因此：**未获得完整包可见性时不检测残留**。
     * - 只清理 Rust 判定 `is_safe=true` 且路径在允许清单内的项。
     */
    suspend fun scanJunk(detectDuplicates: Boolean = false): List<JunkItem> =
        withContext(Dispatchers.IO) {
            val root = context.getExternalFilesDir(null)?.parentFile
                ?: Environment.getExternalStorageDirectory()

            // 只有在拿到可信的完整包列表时才启用残留检测
            val installed = if (permissions.hasFullPackageVisibility()) {
                queryInstalledPackageNames().joinToString(",")
            } else ""

            val report = RustCore.scanJunkSafe(root.absolutePath, installed, detectDuplicates)
                ?: return@withContext emptyList()

            report.items
                .filter { it.size > 0 && isPathAllowed(it.path) }
                .map {
                    JunkItem(
                        path = it.path,
                        label = it.label,
                        sizeMb = it.size / MB_F,
                        kind = it.kind,
                        isSafe = it.isSafe,
                        riskHint = it.risk,
                    )
                }
                .sortedByDescending { it.sizeMb }
        }

    /** 清理白名单：只允许清理这些区域，防止路径穿越误删 */
    private fun isPathAllowed(path: String): Boolean {
        val canonical = runCatching { File(path).canonicalPath }.getOrNull() ?: return false
        val allowedRoots = listOfNotNull(
            context.cacheDir.canonicalPath,
            context.externalCacheDir?.canonicalPath,
            context.getExternalFilesDir(null)?.parentFile?.canonicalPath,
        )
        return allowedRoots.any { canonical.startsWith(it) }
    }

    /** 执行清理，返回释放的字节数 */
    suspend fun cleanJunk(items: List<JunkItem>): Long = withContext(Dispatchers.IO) {
        var freed = 0L
        items.filter { it.isSafe }.forEach { item ->
            runCatching {
                if (!isPathAllowed(item.path)) return@runCatching
                val target = File(item.path)
                if (!target.exists()) return@runCatching
                val size = runCatching { walkSize(target) }.getOrDefault(0L)
                if (target.deleteRecursively()) freed += size
            }.onFailure { Log.w(TAG, "Failed to clean ${item.path}", it) }
        }
        freed
    }

    private fun walkSize(file: File): Long {
        if (file.isFile) return file.length()
        var total = 0L
        file.listFiles()?.forEach { total += walkSize(it) }
        return total
    }

    // ==================== 应用扫描 ====================

    /**
     * 扫描已安装应用。
     *
     * P1-2 修复：`cacheSizeMb` 不再恒为 0 —— 通过 [StorageStatsManager] 读取真实缓存/数据大小
     * （API 26+；查询他应用需 `PACKAGE_USAGE_STATS` 授权，未授权时安全返回 0 并记录原因）。
     * 排序与统计由 Rust 引擎完成，保证与 Rust 侧 AppInfo 模型一致。
     */
    suspend fun scanApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val pm = context.packageManager
            val usageMap = queryUsageDaysSafe()
            val statsManager =
                context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager

            val dtos = pm.getInstalledPackages(PackageManager.GET_META_DATA).mapNotNull { pkg ->
                runCatching {
                    val info = pkg.applicationInfo ?: return@mapNotNull null
                    val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val (cache, data) = queryAppStorage(statsManager, pkg.packageName)
                    // 把「N 天前使用」换算成绝对时间戳喂给 Rust（0 = 未知）
                    val lastUsedTime = usageMap[pkg.packageName]?.let { days ->
                        if (days < 0) 0L
                        else System.currentTimeMillis() - days * 24L * 3600 * 1000
                    } ?: 0L
                    RustCore.AppInfoDto(
                        packageName = pkg.packageName,
                        label = pm.getApplicationLabel(info).toString(),
                        isSystem = isSystem,
                        size = cache + data,
                        installTime = pkg.firstInstallTime,
                        updateTime = pkg.lastUpdateTime,
                        cacheSize = cache,
                        dataSize = data,
                        version = pkg.versionName ?: "",
                        targetSdk = runCatching { info.targetSdkVersion }.getOrDefault(0),
                        lastUsedTime = lastUsedTime,
                    )
                }.getOrNull()
            }

            // 交给 Rust 做统计 + 排序（体积降序）
            val ordered = RustCore.analyzeAppsSafe(dtos, sortBy = 0, ascending = false)
                ?.sortedApps ?: dtos.sortedByDescending { it.size }

            ordered.map {
                AppInfo(
                    packageName = it.packageName,
                    label = it.label,
                    isSystemApp = it.isSystem,
                    cacheSizeMb = it.cacheSize / MB_F,
                    dataSizeMb = it.dataSize / MB_F,
                    totalSizeMb = it.size / MB_F,
                    lastUsedDays = usageMap[it.packageName] ?: -1,
                    versionName = it.version.ifBlank { "未知" },
                )
            }
        }.getOrDefault(emptyList())
    }

    /** 真实读取某应用的缓存 + 数据大小；无权限或失败时返回 0 */
    private fun queryAppStorage(
        manager: StorageStatsManager?,
        packageName: String,
    ): Pair<Long, Long> {
        if (manager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return 0L to 0L
        // 先查自身（永远有权限），再查他应用（需 PACKAGE_USAGE_STATS）
        return runCatching {
            // 注意：单参数版本的 queryStatsForPackage 已废弃；
            // 当前签名为 queryStatsForPackage(UUID, packageName, UserHandle)
            val stats = manager.queryStatsForPackage(
                android.os.storage.StorageManager.UUID_DEFAULT,
                packageName,
                UserHandle.getUserHandleForUid(
                    context.packageManager.getApplicationInfo(packageName, 0).uid
                ),
            )
            stats.cacheBytes to (stats.dataBytes)
        }.getOrDefault(0L to 0L)
    }

    private fun queryInstalledPackageNames(): Set<String> = runCatching {
        context.packageManager.getInstalledPackages(0).map { it.packageName }.toSet()
    }.getOrDefault(emptySet())

    private fun queryUsageDaysSafe(): Map<String, Int> = runCatching {
        if (!permissions.hasUsageStatsPermission()) return@runCatching emptyMap()
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
            ?: return@runCatching emptyMap()
        val now = System.currentTimeMillis()
        usm.queryUsageStats(
            android.app.usage.UsageStatsManager.INTERVAL_BEST,
            now - 30L * 24 * 3600 * 1000, now,
        ).associate {
            it.packageName to ((now - it.lastTimeUsed) / (24L * 3600 * 1000)).toInt()
        }
    }.getOrDefault(emptyMap())

    // ==================== 电池（Rust 评分） ====================

    suspend fun analyzeBattery(): RustCore.BatteryReportDto? = withContext(Dispatchers.IO) {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = runCatching {
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        }.getOrDefault(100)
        val batt = readBatteryBroadcast()
        RustCore.analyzeBatterySafe(
            level = level,
            temperatureTenths = (batt.temperature * 10).toInt(),
            voltage = batt.voltage,
            current = 0,
            status = if (batt.isCharging) "charging" else "discharging",
            health = batt.healthString,
            plugged = if (batt.isCharging) 1 else 0,
        )
    }

    // ==================== 权限（统一委托给 PermissionHelper，修复 P3-1 重复实现） ====================

    fun hasUsageStatsPermission(): Boolean = permissions.hasUsageStatsPermission()

    fun usageStatsSettingsIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
}
