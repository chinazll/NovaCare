package com.novacare.core.domain

import com.novacare.core.engine.NovaEngine
import com.novacare.core.system.AppRepository
import com.novacare.core.system.DeviceStatusSource
import com.novacare.core.system.RecentUsageSource
import com.novacare.core.system.SystemPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 电池守护（Battery Guardian 的数据来源）
 *
 * ============================================================
 * 【关于「耗电排行」必须说清楚的一件事】
 *   Android **不向第三方应用提供按应用的真实耗电量**。
 *   BatteryStats 需要 `android.permission.BATTERY_STATS`（系统签名级），
 *   系统设置里的耗电排行是系统自己的特权。
 *
 *   所以这里给出的排行是**前台时长**，是耗电的代理指标，不是耗电本身。
 *   字段名就叫 [AppUsageRank.foregroundMs]，UI 上必须写"前台时长"而不是"耗电"。
 *   把代理指标说成真实耗电，就是本项目定义的"伪造"。
 * ============================================================
 *
 * 其余数据全部是真实读数：
 *   - 电量/温度/电压/电流/状态/健康度：ACTION_BATTERY_CHANGED（BatteryManager）
 *   - 健康评分与建议：Rust 内核确定性推导（不可用时为 null，不编造）
 *   - 前台时长：UsageStatsManager（未授权时为空，不编造）
 */
@Singleton
class BatteryInsightsUseCase @Inject constructor(
    private val device: DeviceStatusSource,
    private val engine: NovaEngine,
    private val apps: AppRepository,
    private val usage: RecentUsageSource,
    private val permissions: SystemPermissions,
) {

    suspend operator fun invoke(): BatteryInsights = withContext(Dispatchers.Default) {
        val now = System.currentTimeMillis()
        val battery = device.battery()
        val engineReport = engine.analyzeBattery(
            level = battery.levelPercent,
            temperatureTenths = battery.temperatureTenths,
            voltage = battery.voltageMv,
            current = battery.currentNow,
            status = battery.status,
            health = battery.health,
            plugged = battery.plugged,
        )

        val usageGranted = permissions.hasUsageStats()
        val window = if (usageGranted) usage.query(now - WINDOW_MS, now) else emptyMap()
        val labels = if (usageGranted) {
            apps.loadInstalledApps(now).associate { it.packageName to it.label }
        } else {
            emptyMap()
        }

        val ranking = window.entries
            .map { (pkg, u) ->
                AppUsageRank(
                    packageName = pkg,
                    label = labels[pkg] ?: pkg,
                    foregroundMs = u.foregroundMs,
                    lastUsedEpochMs = u.lastUsedEpochMs,
                )
            }
            .sortedByDescending { it.foregroundMs }
            .take(TOP_N)

        val suspects = window.entries
            .mapNotNull { (pkg, u) ->
                val touchedRecently = u.lastUsedEpochMs >= now - WINDOW_MS && u.lastUsedEpochMs > 0
                // 窗口内被记录到活动，但累计前台时长不足 1 分钟 → 推断存在后台活动
                if (!touchedRecently || u.foregroundMs >= SUSPECT_FOREGROUND_MS) return@mapNotNull null
                BackgroundSuspect(
                    packageName = pkg,
                    label = labels[pkg] ?: pkg,
                    foregroundMs = u.foregroundMs,
                    lastUsedEpochMs = u.lastUsedEpochMs,
                    evidence = "最近 ${hoursAgo(now, u.lastUsedEpochMs)} 有活动记录，" +
                        "但同期前台时长只有 ${u.foregroundMs / 1000} 秒",
                )
            }
            .sortedByDescending { it.lastUsedEpochMs }
            .take(SUSPECT_N)

        BatteryInsights(
            nowMs = now,
            levelPercent = battery.levelPercent,
            temperatureCelsius = battery.temperatureTenths / 10.0f,
            voltageMv = battery.voltageMv,
            currentMa = battery.currentNow,
            status = battery.status,
            health = battery.health,
            plugged = battery.plugged != 0,
            engineAvailable = engine.isAvailable,
            engineHealthScore = engineReport?.healthScore,
            usagePermissionGranted = usageGranted,
            windowHours = (WINDOW_MS / 3_600_000L).toInt(),
            foregroundRanking = ranking,
            backgroundSuspects = suspects,
            advices = buildAdvices(
                battery = battery,
                engineSuggestions = engineReport?.suggestions.orEmpty(),
                usageGranted = usageGranted,
                suspects = suspects,
            ),
        )
    }

    // ============================================================
    // 建议生成（每条都必须给出"为什么"）
    // ============================================================

    private fun buildAdvices(
        battery: DeviceStatusSource.BatteryStatus,
        engineSuggestions: List<String>,
        usageGranted: Boolean,
        suspects: List<BackgroundSuspect>,
    ): List<GuardianAdvice> = buildList {
        val temp = battery.temperatureTenths / 10.0f
        val charging = battery.plugged != 0

        if (temp >= HOT_CELSIUS) {
            add(
                GuardianAdvice(
                    title = "机身温度偏高：${"%.1f".format(temp)}°C",
                    why = "这个数字由系统 BatteryManager 上报。锂电池长期在 40°C 以上工作" +
                        "会明显加速容量衰减，高温时段建议拔掉充电器、避免边充边玩。",
                    tone = GuardianAdvice.Tone.CAUTION,
                ),
            )
        }

        if (battery.health != "good" && battery.health != "unknown") {
            add(
                GuardianAdvice(
                    title = "系统上报电池状态异常：${healthLabel(battery.health)}",
                    why = "来自 BatteryManager.EXTRA_HEALTH。这个字段是系统对电池本身的" +
                        "诊断结果，第三方应用无法修复硬件问题，建议到售后检测。",
                    tone = GuardianAdvice.Tone.RISK,
                ),
            )
        }

        if (charging && battery.levelPercent >= HIGH_LEVEL && battery.status == "charging") {
            add(
                GuardianAdvice(
                    title = "已充至 ${battery.levelPercent}%，可以拔掉了",
                    why = "电量来自系统上报。长期维持在满电（尤其是边充边用导致的持续高压）" +
                        "会增加电芯压力。本 App 无法替你停止充电 —— Android 没有给第三方" +
                        "这个权限，只能提示。",
                    tone = GuardianAdvice.Tone.INFO,
                ),
            )
        }

        if (!charging && battery.levelPercent <= LOW_LEVEL) {
            add(
                GuardianAdvice(
                    title = "电量 ${battery.levelPercent}%，建议开启省电模式",
                    why = "省电模式由系统统一管理，第三方应用**无法**直接开启" +
                        "（需要系统级权限）。点下面按钮会跳到系统设置页，由你确认开启。",
                    action = GuardianAction.BATTERY_SAVER_SETTINGS,
                    actionLabel = "去系统设置",
                    tone = if (battery.levelPercent <= 10) {
                        GuardianAdvice.Tone.RISK
                    } else {
                        GuardianAdvice.Tone.CAUTION
                    },
                ),
            )
        }

        if (!charging && battery.currentNow <= HEAVY_DRAIN_MA) {
            add(
                GuardianAdvice(
                    title = "当前放电电流约 ${-battery.currentNow} mA，属于高负载",
                    why = "电流读数来自 BatteryManager.BATTERY_PROPERTY_CURRENT_NOW（实时值）。" +
                        "持续大电流放电会让电量掉得比预期快，通常是屏幕亮度 + 游戏/导航/热点" +
                        "这类组合。本 App 无法限制其他应用的功耗。",
                    tone = GuardianAdvice.Tone.CAUTION,
                ),
            )
        }

        if (!usageGranted) {
            add(
                GuardianAdvice(
                    title = "未授予「使用情况访问」，无法统计前台时长",
                    why = "这是 AppOps 权限，不走运行时权限弹窗 —— 必须你到系统设置页手动开启。" +
                        "没它就没有下面的使用排行，本 App 不会用其他数据去凑一个假排行。",
                    action = GuardianAction.USAGE_ACCESS_SETTINGS,
                    actionLabel = "去授权",
                    tone = GuardianAdvice.Tone.CAUTION,
                ),
            )
        }

        suspects.forEach { suspect ->
            add(
                GuardianAdvice(
                    title = "「${suspect.label}」疑似在后台活动",
                    why = "${suspect.evidence}。这是**推断**而非测量：Android 不向第三方" +
                        "提供真实的后台唤醒次数与耗电。如果你不需要它常驻后台，" +
                        "可到它的系统详情页自行处理。",
                    action = GuardianAction.APP_DETAILS,
                    actionLabel = "查看应用",
                    targetPackage = suspect.packageName,
                    tone = GuardianAdvice.Tone.INFO,
                ),
            )
        }

        // 内核确定性建议（Rust battery_monitor 基于电量/温度/电压/状态推导）
        engineSuggestions.forEach { text ->
            add(
                GuardianAdvice(
                    title = text,
                    why = "由 Nova 内核根据电量、温度、电压与健康状态确定性推导（非大模型生成）。",
                    tone = GuardianAdvice.Tone.INFO,
                ),
            )
        }
    }

    private fun hoursAgo(nowMs: Long, atMs: Long): String {
        val minutes = ((nowMs - atMs) / 60_000L).coerceAtLeast(0)
        return when {
            minutes < 60 -> "$minutes 分钟"
            minutes < 24 * 60 -> "${minutes / 60} 小时"
            else -> "${minutes / (24 * 60)} 天"
        }
    }

    fun statusLabel(status: String): String = when (status) {
        "charging" -> "充电中"
        "discharging" -> "放电中"
        "full" -> "已充满"
        "not_charging" -> "未充电"
        else -> "未知"
    }

    fun healthLabel(health: String): String = when (health) {
        "good" -> "良好"
        "overheat" -> "过热"
        "dead" -> "已损坏"
        "over_voltage" -> "电压过高"
        "cold" -> "过冷"
        else -> "未知"
    }

    private companion object {
        const val WINDOW_MS = 24L * 60 * 60 * 1000
        const val TOP_N = 20
        const val SUSPECT_N = 5
        /** 窗口内前台时长低于这个值、却有活动记录 → 疑似后台活动 */
        const val SUSPECT_FOREGROUND_MS = 60_000L
        const val HOT_CELSIUS = 40.0f
        const val HIGH_LEVEL = 85
        const val LOW_LEVEL = 20
        /** 放电电流低于 -800mA 视为高负载 */
        const val HEAVY_DRAIN_MA = -800
    }
}

/** 应用前台时长排行项 —— 注意：这是**时长**不是耗电量 */
data class AppUsageRank(
    val packageName: String,
    val label: String,
    val foregroundMs: Long,
    val lastUsedEpochMs: Long,
)

/** 疑似后台活跃的应用（推断，附证据） */
data class BackgroundSuspect(
    val packageName: String,
    val label: String,
    val foregroundMs: Long,
    val lastUsedEpochMs: Long,
    /** 证据文本：把推断依据直接摆给用户看 */
    val evidence: String,
)

data class BatteryInsights(
    val nowMs: Long,
    /** 系统上报电量百分比 */
    val levelPercent: Int,
    val temperatureCelsius: Float,
    val voltageMv: Int,
    /** 电流（mA，负值=放电）；0 = 系统未给出读数 */
    val currentMa: Int,
    val status: String,
    val health: String,
    val plugged: Boolean,
    val engineAvailable: Boolean,
    /** 内核健康评分；null = 内核不可用（UI 不得显示占位分数） */
    val engineHealthScore: Int?,
    val usagePermissionGranted: Boolean,
    /** 统计窗口长度（小时） */
    val windowHours: Int,
    val foregroundRanking: List<AppUsageRank>,
    val backgroundSuspects: List<BackgroundSuspect>,
    val advices: List<GuardianAdvice>,
)
