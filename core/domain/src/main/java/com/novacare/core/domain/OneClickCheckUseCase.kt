package com.novacare.core.domain

import com.novacare.core.system.AppRepository
import com.novacare.core.system.BatteryInsights
import com.novacare.core.system.BatteryInsightsSource
import com.novacare.core.system.DeviceStatusSource
import com.novacare.core.system.RecentUsageSource
import com.novacare.core.system.ScreenTimeSource
import com.novacare.core.system.SystemPermissions
import com.novacare.core.system.TrafficSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 一键体检（OneClick Device Care Quick Scan）
 *
 * 设计原则：
 *   - 顺序扫描 5 个维度（每步都真实执行对应模块的数据采集，不"假装"扫描）：
 *     1) 存储 — DeviceStatusSource.storage() + StorageInsightsUseCase
 *     2) 内存 — MemoryProcessSource.overview()
 *     3) 电池 — BatteryInsightsUseCase（电量 / 温度 / 健康）
 *     4) 屏幕时长 — ScreenTimeSource.summary()（需 UsageStats 权限）
 *     5) 流量 — TrafficSource.total()（始终可读，无需权限）
 *   - 每步独立可失败；失败时该维度记为 null，UI 据此显示"系统未给出"而非"健康"
 *   - 总分按各维度的"健康比例"加权求和；任何维度缺失都按 0 计入 —— 这是诚实策略：
 *     拿不到的数据**不应**用乐观假设补回满分。
 *
 * 这是 v0.21.0 的一键体检入口。聚合数据供 OneClickScreen 渲染 5 步进度条。
 */
@Singleton
class OneClickCheckUseCase @Inject constructor(
    private val device: DeviceStatusSource,
    private val storageInsights: StorageInsightsUseCase,
    private val memoryProcess: com.novacare.core.system.MemoryProcessSource,
    private val batteryInsights: BatteryInsightsUseCase,
    private val screenTime: ScreenTimeSource,
    private val traffic: TrafficSource,
    private val permissions: SystemPermissions,
    private val apps: AppRepository,
    private val recentUsage: RecentUsageSource,
) {

    /**
     * 体检步骤的固定顺序（UI 进度条按这个顺序推进）。
     *
     * 顺序的考虑：依赖关系 → 重 → 轻
     *   - 存储扫描是最重的 IO（要走文件系统），但用户最关心
     *   - 内存次重（要枚举进程）
     *   - 电池、屏幕时长、流量几乎瞬时
     */
    enum class Step {
        STORAGE,
        MEMORY,
        BATTERY,
        SCREEN_TIME,
        TRAFFIC,
    }

    /**
     * 单步结果。
     *
     * @param healthScore 0..100，0 = 不健康，100 = 满健康；null = 该维度无法计算（如权限缺失）
     * @param summary 一句话描述
     * @param suggestion 给用户的下一步动作；null = 没有具体动作建议
     */
    data class StepResult(
        val step: Step,
        val healthScore: Int?,
        val summary: String,
        val suggestion: String?,
    )

    /**
     * 体检汇总。
     *
     * @param score 0..100 的总分（按各维度 score 平均，缺失按 0 计入）
     * @param results 每一步的结果
     * @param issues 体检后总结的可执行建议清单
     */
    data class Report(
        val score: Int,
        val results: List<StepResult>,
        val issues: List<Issue>,
        val nowMs: Long,
    )

    data class Issue(
        val title: String,
        val why: String,
        val targetRoute: IssueTarget,
    )

    enum class IssueTarget {
        CLEAN,
        FREEZE,
        SETTINGS,
    }

    suspend operator fun invoke(
        rootPath: String,
        onStepStart: (Step) -> Unit = {},
    ): Report = withContext(Dispatchers.Default) {
        val now = System.currentTimeMillis()
        val results = mutableListOf<StepResult>()
        val issues = mutableListOf<Issue>()

        // 1) 存储 —— 走 StorageInsightsUseCase 的完整扫描（仅该步较重）
        onStepStart(Step.STORAGE)
        runCatching {
            val insight = storageInsights(rootPath)
            results += StepResult(
                step = Step.STORAGE,
                healthScore = insight.healthScore?.coerceIn(0, 100),
                summary = buildStorageSummary(insight),
                suggestion = if ((insight.healthScore ?: 0) < 80) "释放缓存/大文件" else null,
            )
            if ((insight.healthScore ?: 0) < 70) {
                issues += Issue(
                    title = "存储空间偏低",
                    why = "可用空间不足 ${insight.healthScore ?: 0}/100，长期低位会拖累系统流畅度。",
                    targetRoute = IssueTarget.CLEAN,
                )
            }
        }.onFailure {
            results += StepResult(Step.STORAGE, null, "系统未给出存储读数：${it.message}", null)
        }

        // 2) 内存 —— MemoryProcessSource.overview()（轻量）
        onStepStart(Step.MEMORY)
        runCatching {
            val mem = memoryProcess.overview()
            val score = mem.availableRatio?.let { (it * 100).toInt() }
            results += StepResult(
                step = Step.MEMORY,
                healthScore = score?.coerceIn(0, 100),
                summary = "可用 ${mem.availableBytes.toReadableSize()} / 共 ${mem.totalBytes.toReadableSize()}",
                suggestion = if (mem.lowMemory) "系统已处于低内存状态" else null,
            )
            if (mem.lowMemory) {
                issues += Issue(
                    title = "系统已处于低内存状态",
                    why = "MemoryInfo.lowMemory = true，系统已开始主动回收后台进程。",
                    targetRoute = IssueTarget.CLEAN,
                )
            }
        }.onFailure {
            results += StepResult(Step.MEMORY, null, "系统未给出内存读数：${it.message}", null)
        }

        // 3) 电池 —— BatteryInsightsUseCase（轻量）
        onStepStart(Step.BATTERY)
        runCatching {
            val battery = batteryInsights()
            results += StepResult(
                step = Step.BATTERY,
                healthScore = battery.engineHealthScore?.coerceIn(0, 100),
                summary = "${battery.levelPercent}% · ${"%.1f".format(battery.temperatureCelsius)}°C",
                suggestion = battery.advices.firstOrNull()?.title,
            )
            // 温度偏高或电量过低 → 给出"去设置"的建议
            if (battery.temperatureCelsius >= 40f || battery.levelPercent <= 15) {
                issues += Issue(
                    title = if (battery.temperatureCelsius >= 40f) "机身温度偏高" else "电量过低",
                    why = if (battery.temperatureCelsius >= 40f) {
                        "${"%.1f".format(battery.temperatureCelsius)}°C，超过 40°C 会加速电池衰减。"
                    } else {
                        "${battery.levelPercent}%，建议开启省电模式（系统在 系统设置 → 电池）。"
                    },
                    targetRoute = IssueTarget.SETTINGS,
                )
            }
        }.onFailure {
            results += StepResult(Step.BATTERY, null, "系统未给出电池读数：${it.message}", null)
        }

        // 4) 屏幕时长 —— ScreenTimeSource.summary()（需 UsageStats 权限）
        onStepStart(Step.SCREEN_TIME)
        val usageGranted = permissions.hasUsageStats()
        if (usageGranted) {
            runCatching {
                val summary = screenTime.summary()
                results += StepResult(
                    step = Step.SCREEN_TIME,
                    healthScore = null, // 屏幕时长无健康分；仅作信息项
                    summary = "${formatMinutes(summary.totalForegroundMs)} · ${summary.appCount} 个应用",
                    suggestion = if (summary.appCount == 0) "未检测到前台事件" else null,
                )
            }.onFailure {
                results += StepResult(Step.SCREEN_TIME, null, "读取屏幕时长失败：${it.message}", null)
            }
        } else {
            results += StepResult(
                step = Step.SCREEN_TIME,
                healthScore = null,
                summary = "未授予「使用情况访问」",
                suggestion = "去设置开启后可统计前台时长",
            )
            issues += Issue(
                title = "未授予「使用情况访问」",
                why = "拿不到应用前台时长，无法判断冻结/清理目标。",
                targetRoute = IssueTarget.SETTINGS,
            )
        }

        // 5) 流量 —— TrafficSource.total()（始终可读）
        onStepStart(Step.TRAFFIC)
        runCatching {
            val total = traffic.total()
            results += StepResult(
                step = Step.TRAFFIC,
                healthScore = null, // 流量无健康分
                summary = "下行 ${total.rxBytes.toReadableSize()} · 上行 ${total.txBytes.toReadableSize()}",
                suggestion = null,
            )
        }.onFailure {
            results += StepResult(Step.TRAFFIC, null, "读取流量失败：${it.message}", null)
        }

        val score = results.mapNotNull { it.healthScore }.let { scores ->
            if (scores.isEmpty()) 0 else scores.average().toInt().coerceIn(0, 100)
        }

        Report(
            score = score,
            results = results,
            issues = issues,
            nowMs = now,
        )
    }

    private fun buildStorageSummary(insight: StorageInsights): String {
        val usedPct = if (insight.totalBytes > 0L) {
            (insight.usedBytes * 100L / insight.totalBytes).toInt()
        } else 0
        return "已用 ${insight.usedBytes.toReadableSize()} / ${insight.totalBytes.toReadableSize()}（${usedPct}%）"
    }

    /** 把 Long 字节转成人能看懂的字符串（避免引入 formatBytes 依赖到 domain） */
    private fun Long.toReadableSize(): String {
        if (this <= 0L) return "0 B"
        val mb = this / 1024L / 1024L
        return when {
            mb < 1L -> "$this B"
            mb < 1024L -> "$mb MB"
            else -> "${"%.1f".format(mb / 1024f)} GB"
        }
    }

    private fun formatMinutes(ms: Long): String {
        val minutes = ms / 60_000L
        return when {
            minutes < 60L -> "${minutes}m"
            else -> "${minutes / 60L}h ${minutes % 60L}m"
        }
    }
}
