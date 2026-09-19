package com.novacare.optimizer.core

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * NovaCare Rust 核心引擎 —— JNI 桥接层
 *
 * 这是 Kotlin 与 Rust 之间**唯一**的连接点。
 * 所有重量级计算（文件扫描 / 存储分析 / 垃圾检测 / 重复文件 BLAKE3 去重 /
 * 应用统计排序 / 电池分析）全部由 `libnovacare_core.so` 完成。
 *
 * 设计契约：
 * - Rust 侧函数名：`Java_com_novacare_optimizer_core_RustCore_*`（见 rust/src/jni_bindings.rs）
 * - 通信格式：JSON 字符串（Rust 用 serde_json 序列化，此处用 org.json 解析）
 * - 返回值：成功返回 JSON String；失败（含 panic 被 catch_unwind 捕获）返回 null
 * - 异常策略：Rust 侧 panic 一律被 `panic::catch_unwind` 捕获，绝不跨 JNI 边界传播
 *
 * 为什么用 org.json 而不是 kotlinx.serialization：
 * 避免为「解析几个 DTO」引入 KSP 插件与额外构建步骤，降低构建复杂度。
 */
object RustCore {

    private const val TAG = "NovaCore"

    /** 引擎是否可用（.so 加载失败时为 false，UI 降级到安全默认值） */
    @Volatile
    var isAvailable: Boolean = false
        private set

    init {
        isAvailable = runCatching {
            System.loadLibrary("novacare_core")
            // 初始化 Rust 侧日志（失败不影响可用性）
            runCatching { init() }.onFailure {
                Log.w(TAG, "Rust init() failed (non-fatal)", it)
            }
            true
        }.getOrElse { e ->
            Log.e(TAG, "Failed to load libnovacare_core.so — falling back to safe defaults", e)
            false
        }
    }

    // ==================== JNI 原生方法 ====================
    // 签名与 rust/src/jni_bindings.rs 严格一一对应

    /** 初始化 Rust 侧日志 */
    @JvmStatic
    external fun init(): Boolean

    /** 引擎版本字符串 */
    @JvmStatic
    external fun version(): String?

    /**
     * 存储分析（真实并行扫描）
     * @param rootPath 扫描根目录
     * @param totalBytes 设备总容量（由 Android StatFs 提供，Rust 侧回填到 report.total）
     * @return StorageReport JSON，失败返回 null
     */
    @JvmStatic
    external fun analyzeStorage(rootPath: String, totalBytes: Long): String?

    /**
     * 垃圾扫描（缓存 / 缩略图 / 日志 / 临时文件 / 残留 / 重复文件）
     * @param rootPath 扫描根目录
     * @param installedPackages 已安装包名（逗号分隔），用于残留判定
     * @param detectDuplicates 是否做 BLAKE3 内容哈希去重
     * @return JunkReport JSON，失败返回 null
     */
    @JvmStatic
    external fun scanJunk(
        rootPath: String,
        installedPackages: String,
        detectDuplicates: Boolean,
    ): String?

    /**
     * 电池分析
     * @param temperatureTenths 温度 ×10（Android EXTRA_TEMPERATURE 单位）
     */
    @JvmStatic
    external fun analyzeBattery(
        level: Int,
        temperatureTenths: Int,
        voltage: Int,
        current: Int,
        status: String,
        health: String,
        plugged: Int,
    ): String?

    /**
     * 应用分析（统计 + 排序，纯计算，不含 IO）
     * @param appsJson AppInfo 数组 JSON
     * @param sortBy 0=Size 1=Name 2=InstallTime 3=UpdateTime
     */
    @JvmStatic
    external fun analyzeApps(appsJson: String, sortBy: Int, ascending: Boolean): String?

    // ==================== DTO（与 rust/src/models.rs 字段严格对齐） ====================

    data class FileNodeDto(
        val path: String,
        val size: Long,
        val isDir: Boolean,
        val modified: Long?,
        val children: List<FileNodeDto>,
    )

    data class StorageCategoryDto(
        val name: String,
        val size: Long,
        val ratio: Double,
        val colorId: Int,
    )

    data class StorageReportDto(
        val total: Long,
        val used: Long,
        val categories: List<StorageCategoryDto>,
        val largestFiles: List<FileNodeDto>,
        val durationMs: Long,
    )

    data class JunkItemDto(
        val path: String,
        val label: String,
        val size: Long,
        val kind: String,
        val isSafe: Boolean,
        val risk: String,
    )

    data class JunkReportDto(
        val items: List<JunkItemDto>,
        val totalSize: Long,
        val safeSize: Long,
        val durationMs: Long,
    )

    data class BatteryReportDto(
        val healthScore: Int,
        val cycleCount: Int,
        val temperature: Float,
        val voltage: Int,
        val level: Int,
        val status: String,
        val suggestions: List<String>,
    )

    /** 与 Rust AppInfo 对齐；Kotlin → Rust 方向也要序列化为此结构 */
    data class AppInfoDto(
        val packageName: String,
        val label: String,
        val isSystem: Boolean,
        val size: Long,
        val installTime: Long,
        val updateTime: Long,
        val cacheSize: Long,
        val dataSize: Long,
        val version: String,
        val targetSdk: Int,
    )

    data class AppsReportDto(
        val stats: AppStatsDto,
        val sortedApps: List<AppInfoDto>,
    )

    data class AppStatsDto(
        val totalApps: Int,
        val systemApps: Int,
        val userApps: Int,
        val totalSize: Long,
        val totalCache: Long,
        val totalData: Long,
    )

    // ==================== 高层封装（带 null 安全 + JSON 解析） ====================

    /** 安全调用：引擎不可用或返回 null 时返回 null，绝不抛异常 */
    private inline fun <T> safeCall(block: () -> T?): T? =
        if (!isAvailable) null else runCatching { block() }.onFailure {
            Log.w(TAG, "Rust call failed", it)
        }.getOrNull()

    fun versionSafe(): String = safeCall { version() } ?: "Rust engine unavailable"

    fun analyzeStorageSafe(rootPath: String, totalBytes: Long): StorageReportDto? =
        safeCall { analyzeStorage(rootPath, totalBytes) }?.let { parseStorageReport(it) }

    fun scanJunkSafe(
        rootPath: String,
        installedPackages: String,
        detectDuplicates: Boolean,
    ): JunkReportDto? =
        safeCall { scanJunk(rootPath, installedPackages, detectDuplicates) }?.let { parseJunkReport(it) }

    fun analyzeBatterySafe(
        level: Int,
        temperatureTenths: Int,
        voltage: Int,
        current: Int,
        status: String,
        health: String,
        plugged: Int,
    ): BatteryReportDto? = safeCall {
        analyzeBattery(level, temperatureTenths, voltage, current, status, health, plugged)
    }?.let { parseBatteryReport(it) }

    fun analyzeAppsSafe(apps: List<AppInfoDto>, sortBy: Int, ascending: Boolean): AppsReportDto? =
        safeCall { analyzeApps(serializeApps(apps), sortBy, ascending) }?.let { parseAppsReport(it) }

    // ==================== JSON 解析 ====================

    private fun parseStorageReport(json: String): StorageReportDto? = runCatching {
        val o = JSONObject(json)
        StorageReportDto(
            total = o.optLong("total"),
            used = o.optLong("used"),
            categories = o.optJSONArray("categories").toList { c ->
                StorageCategoryDto(
                    name = c.optString("name"),
                    size = c.optLong("size"),
                    ratio = c.optDouble("ratio"),
                    colorId = c.optInt("color_id"),
                )
            },
            largestFiles = o.optJSONArray("largest_files").mapJson { parseFileNode(it) },
            durationMs = o.optLong("duration_ms"),
        )
    }.onFailure { Log.w(TAG, "parseStorageReport failed", it) }.getOrNull()

    private fun parseFileNode(o: JSONObject): FileNodeDto = FileNodeDto(
        path = o.optString("path"),
        size = o.optLong("size"),
        isDir = o.optBoolean("is_dir"),
        modified = if (o.has("modified") && !o.isNull("modified")) o.optLong("modified") else null,
        children = o.optJSONArray("children").toList { parseFileNode(it) },
    )

    private fun parseJunkReport(json: String): JunkReportDto? = runCatching {
        val o = JSONObject(json)
        JunkReportDto(
            items = o.optJSONArray("items").mapJson { i ->
                JunkItemDto(
                    path = i.optString("path"),
                    label = i.optString("label"),
                    size = i.optLong("size"),
                    kind = i.optString("kind"),
                    isSafe = i.optBoolean("is_safe"),
                    risk = i.optString("risk"),
                )
            },
            totalSize = o.optLong("total_size"),
            safeSize = o.optLong("safe_size"),
            durationMs = o.optLong("duration_ms"),
        )
    }.onFailure { Log.w(TAG, "parseJunkReport failed", it) }.getOrNull()

    private fun parseBatteryReport(json: String): BatteryReportDto? = runCatching {
        val o = JSONObject(json)
        BatteryReportDto(
            healthScore = o.optInt("health_score"),
            // Rust 侧未知时不序列化该字段 → 这里用 -1 表示「未知」
            cycleCount = if (o.has("cycle_count")) o.optInt("cycle_count") else -1,
            temperature = o.optDouble("temperature").toFloat(),
            voltage = o.optInt("voltage"),
            level = o.optInt("level"),
            status = o.optString("status"),
            suggestions = o.optJSONArray("suggestions").toStringList(),
        )
    }.onFailure { Log.w(TAG, "parseBatteryReport failed", it) }.getOrNull()

    private fun parseAppsReport(json: String): AppsReportDto? = runCatching {
        val o = JSONObject(json)
        val s = o.optJSONObject("stats") ?: JSONObject()
        AppsReportDto(
            stats = AppStatsDto(
                totalApps = s.optInt("total_apps"),
                systemApps = s.optInt("system_apps"),
                userApps = s.optInt("user_apps"),
                totalSize = s.optLong("total_size"),
                totalCache = s.optLong("total_cache"),
                totalData = s.optLong("total_data"),
            ),
            sortedApps = o.optJSONArray("sorted_apps").toList { a ->
                AppInfoDto(
                    packageName = a.optString("package_name"),
                    label = a.optString("label"),
                    isSystem = a.optBoolean("is_system"),
                    size = a.optLong("size"),
                    installTime = a.optLong("install_time"),
                    updateTime = a.optLong("update_time"),
                    cacheSize = a.optLong("cache_size"),
                    dataSize = a.optLong("data_size"),
                    version = a.optString("version"),
                    targetSdk = a.optInt("target_sdk"),
                )
            },
        )
    }.onFailure { Log.w(TAG, "parseAppsReport failed", it) }.getOrNull()

    /** Kotlin → Rust：把应用列表序列化成 Rust AppInfo 期望的 JSON */
    fun serializeApps(apps: List<AppInfoDto>): String = runCatching {
        JSONArray().apply {
            apps.forEach { a ->
                put(JSONObject().apply {
                    put("package_name", a.packageName)
                    put("label", a.label)
                    put("is_system", a.isSystem)
                    put("size", a.size)
                    put("install_time", a.installTime)
                    put("update_time", a.updateTime)
                    put("cache_size", a.cacheSize)
                    put("data_size", a.dataSize)
                    put("version", a.version)
                    put("target_sdk", a.targetSdk)
                    put("last_used_time", a.lastUsedTime)
                })
            }
        }.toString()
    }.getOrDefault("[]")

    // ==================== org.json 扩展（null 安全） ====================

    /** null 安全的 JSONArray 映射：任一元素解析失败都跳过，绝不抛异常 */
    private fun <T> JSONArray?.mapJson(mapper: (JSONObject) -> T): List<T> =
        this?.let { arr ->
            (0 until arr.length()).mapNotNull { idx ->
                runCatching { mapper(arr.getJSONObject(idx)) }.getOrNull()
            }
        } ?: emptyList()

    private fun JSONArray?.toStringList(): List<String> =
        this?.let { arr -> (0 until arr.length()).mapNotNull { runCatching { arr.getString(it) }.getOrNull() } }
            ?: emptyList()
}
