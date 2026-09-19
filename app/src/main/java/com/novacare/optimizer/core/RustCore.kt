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
 * - Rust 侧函数名：`Java_com_novacare_optimizer_core_RustCore_*`
 * - 通信格式：JSON 字符串（Rust 用 serde_json 序列化，此处用 org.json 解析）
 * - 返回值：成功返回 JSON String；失败（含 panic 被 catch_unwind 捕获）返回 null
 *
 * 解析层刻意使用**显式循环**而非泛型 map 扩展：
 * 此前用 `fun <T> JSONArray?.mapJson(mapper: (JSONObject) -> T)` 时，
 * Kotlin 在 platform type 上无法推断 lambda 参数类型，导致一连串
 * "Cannot infer type" + "Unresolved reference" 级联错误。显式循环最稳。
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
            true
        }.getOrElse { e ->
            Log.e(TAG, "Failed to load libnovacare_core.so - falling back to safe defaults", e)
            false
        }
        if (isAvailable) {
            runCatching { init() }.onFailure { Log.w(TAG, "Rust init() failed", it) }
        }
    }

    // ==================== JNI 原生方法 ====================

    @JvmStatic
    external fun init(): Boolean

    @JvmStatic
    external fun version(): String?

    /** @param totalBytes 设备总容量（由 StatFs 提供） */
    @JvmStatic
    external fun analyzeStorage(rootPath: String, totalBytes: Long): String?

    /** @param installedPackages 已安装包名（逗号分隔）；**空串 = 跳过残留检测** */
    @JvmStatic
    external fun scanJunk(
        rootPath: String,
        installedPackages: String,
        detectDuplicates: Boolean,
    ): String?

    /** @param temperatureTenths 温度 ×10 */
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

    /** @param sortBy 0=Size 1=Name 2=InstallTime 3=UpdateTime */
    @JvmStatic
    external fun analyzeApps(appsJson: String, sortBy: Int, ascending: Boolean): String?

    // ==================== DTO（与 rust/src/models.rs 字段对齐） ====================

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
        /** 充电周期；-1 = 未知（真实周期需 root，本项目不伪造） */
        val cycleCount: Int,
        val temperature: Float,
        val voltage: Int,
        val level: Int,
        val status: String,
        val suggestions: List<String>,
    )

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
        /** 最后使用时间（Unix 秒）；0 = 未知 */
        val lastUsedTime: Long = 0,
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

    // ==================== 高层封装 ====================

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
        safeCall { scanJunk(rootPath, installedPackages, detectDuplicates) }
            ?.let { parseJunkReport(it) }

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
        safeCall { analyzeApps(serializeApps(apps), sortBy, ascending) }
            ?.let { parseAppsReport(it) }

    // ==================== JSON 解析 ====================

    private fun parseStorageReport(json: String): StorageReportDto? = runCatching {
        val o = JSONObject(json)

        val categories = mutableListOf<StorageCategoryDto>()
        val catsArr: JSONArray? = o.optJSONArray("categories")
        if (catsArr != null) {
            for (i in 0 until catsArr.length()) {
                val c = catsArr.getJSONObject(i)
                categories.add(
                    StorageCategoryDto(
                        name = c.optString("name", ""),
                        size = c.optLong("size", 0L),
                        ratio = c.optDouble("ratio", 0.0),
                        colorId = c.optInt("color_id", 0),
                    )
                )
            }
        }

        val largest = mutableListOf<FileNodeDto>()
        val filesArr: JSONArray? = o.optJSONArray("largest_files")
        if (filesArr != null) {
            for (i in 0 until filesArr.length()) {
                largest.add(parseFileNode(filesArr.getJSONObject(i)))
            }
        }

        StorageReportDto(
            total = o.optLong("total", 0L),
            used = o.optLong("used", 0L),
            categories = categories,
            largestFiles = largest,
            durationMs = o.optLong("duration_ms", 0L),
        )
    }.onFailure { Log.w(TAG, "parseStorageReport failed", it) }.getOrNull()

    private fun parseFileNode(o: JSONObject): FileNodeDto {
        val children = mutableListOf<FileNodeDto>()
        val childArr: JSONArray? = o.optJSONArray("children")
        if (childArr != null) {
            for (i in 0 until childArr.length()) {
                children.add(parseFileNode(childArr.getJSONObject(i)))
            }
        }
        return FileNodeDto(
            path = o.optString("path", ""),
            size = o.optLong("size", 0L),
            isDir = o.optBoolean("is_dir", false),
            modified = if (o.has("modified") && !o.isNull("modified")) o.optLong("modified") else null,
            children = children,
        )
    }

    private fun parseJunkReport(json: String): JunkReportDto? = runCatching {
        val o = JSONObject(json)
        val items = mutableListOf<JunkItemDto>()
        val arr: JSONArray? = o.optJSONArray("items")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val it = arr.getJSONObject(i)
                items.add(
                    JunkItemDto(
                        path = it.optString("path", ""),
                        label = it.optString("label", ""),
                        size = it.optLong("size", 0L),
                        kind = it.optString("kind", ""),
                        isSafe = it.optBoolean("is_safe", false),
                        risk = it.optString("risk", ""),
                    )
                )
            }
        }
        JunkReportDto(
            items = items,
            totalSize = o.optLong("total_size", 0L),
            safeSize = o.optLong("safe_size", 0L),
            durationMs = o.optLong("duration_ms", 0L),
        )
    }.onFailure { Log.w(TAG, "parseJunkReport failed", it) }.getOrNull()

    private fun parseBatteryReport(json: String): BatteryReportDto? = runCatching {
        val o = JSONObject(json)
        val suggestions = mutableListOf<String>()
        val arr: JSONArray? = o.optJSONArray("suggestions")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                suggestions.add(arr.optString(i, ""))
            }
        }
        BatteryReportDto(
            healthScore = o.optInt("health_score", 0),
            cycleCount = if (o.has("cycle_count")) o.optInt("cycle_count", 0) else -1,
            temperature = o.optDouble("temperature", 0.0).toFloat(),
            voltage = o.optInt("voltage", 0),
            level = o.optInt("level", 0),
            status = o.optString("status", ""),
            suggestions = suggestions,
        )
    }.onFailure { Log.w(TAG, "parseBatteryReport failed", it) }.getOrNull()

    private fun parseAppsReport(json: String): AppsReportDto? = runCatching {
        val o = JSONObject(json)
        val s: JSONObject? = o.optJSONObject("stats")

        val sorted = mutableListOf<AppInfoDto>()
        val arr: JSONArray? = o.optJSONArray("sorted_apps")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val a = arr.getJSONObject(i)
                sorted.add(
                    AppInfoDto(
                        packageName = a.optString("package_name", ""),
                        label = a.optString("label", ""),
                        isSystem = a.optBoolean("is_system", false),
                        size = a.optLong("size", 0L),
                        installTime = a.optLong("install_time", 0L),
                        updateTime = a.optLong("update_time", 0L),
                        cacheSize = a.optLong("cache_size", 0L),
                        dataSize = a.optLong("data_size", 0L),
                        version = a.optString("version", ""),
                        targetSdk = a.optInt("target_sdk", 0),
                        lastUsedTime = a.optLong("last_used_time", 0L),
                    )
                )
            }
        }

        AppsReportDto(
            stats = AppStatsDto(
                totalApps = s?.optInt("total_apps", 0) ?: 0,
                systemApps = s?.optInt("system_apps", 0) ?: 0,
                userApps = s?.optInt("user_apps", 0) ?: 0,
                totalSize = s?.optLong("total_size", 0L) ?: 0L,
                totalCache = s?.optLong("total_cache", 0L) ?: 0L,
                totalData = s?.optLong("total_data", 0L) ?: 0L,
            ),
            sortedApps = sorted,
        )
    }.onFailure { Log.w(TAG, "parseAppsReport failed", it) }.getOrNull()

    /** Kotlin → Rust：把应用列表序列化成 Rust AppInfo 期望的 JSON */
    fun serializeApps(apps: List<AppInfoDto>): String = runCatching {
        val arr = JSONArray()
        for (a in apps) {
            val o = JSONObject()
            o.put("package_name", a.packageName)
            o.put("label", a.label)
            o.put("is_system", a.isSystem)
            o.put("size", a.size)
            o.put("install_time", a.installTime)
            o.put("update_time", a.updateTime)
            o.put("cache_size", a.cacheSize)
            o.put("data_size", a.dataSize)
            o.put("version", a.version)
            o.put("target_sdk", a.targetSdk)
            o.put("last_used_time", a.lastUsedTime)
            arr.put(o)
        }
        arr.toString()
    }.getOrDefault("[]")
}
