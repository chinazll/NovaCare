package com.novacare.core.system

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 屏幕使用时长数据源（Screen Time）
 *
 * 数据来源：[UsageStatsManager.queryEvents]（仅需 `PACKAGE_USAGE_STATS`，
 * 用户在系统设置中授权一次）。**没有"猜"的部分**：
 *   - 拿不到事件流（无权限 / ROM 屏蔽）→ 整个 ScreenTime 页直接降级
 *   - 单个应用无任何事件 → 不出现在列表（绝不显示"0 分钟"占位）
 *   - 单位来自事件时间戳相减，不做缩放
 *
 * 颗粒度选择：聚合到"前台停留总时长"是 OneUI / iOS 屏幕时长的做法，
 * 也是绝大多数用户唯一关心的数。"最近一次使用"是次要信息，作为副文展示。
 */
@Singleton
class ScreenTimeSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** 单个应用的前台使用时长聚合 */
    data class AppUsage(
        val packageName: String,
        /** 在 [windowMs] 内累计前台毫秒数 */
        val foregroundMs: Long,
        /** 最近一次切到前台的时间戳（epoch ms）；无则为 0 */
        val lastForegroundEpochMs: Long,
        /** 应用名（来自 PackageManager）；查不到时回退为 packageName */
        val label: String,
    )

    /** 全局汇总 */
    data class Summary(
        /** 聚合窗口内**所有**应用累计前台毫秒数（含本应用），用于顶部"今日前台时长" */
        val totalForegroundMs: Long,
        /** 应用排行（按前台时长降序） */
        val apps: List<AppUsage>,
        /** 聚合窗口起点（epoch ms） */
        val windowStartEpochMs: Long,
        /** 聚合窗口终点（epoch ms） */
        val windowEndEpochMs: Long,
    ) {
        val appCount: Int get() = apps.size
    }

    /**
     * 读取最近 [windowMs] 毫秒（默认 24h）内的应用前台使用时长。
     *
     * @param topN 只返回前 N 名（默认 10）
     * @return 始终非 null；无权限或系统屏蔽时返回空 Summary
     */
    fun summary(windowMs: Long = DEFAULT_WINDOW_MS, topN: Int = DEFAULT_TOP_N): Summary {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return Summary(0L, emptyList(), 0L, 0L)
        val end = System.currentTimeMillis()
        val start = end - windowMs

        val events = runCatching { usm.queryEvents(start, end) }.getOrNull()
            ?: return Summary(0L, emptyList(), start, end)

        // 状态机：每个包名维护一个 (lastResumeAt, accumulatedMs)
        val perPkgLastResume = HashMap<String, Long>()
        val perPkgAccumulated = HashMap<String, Long>()
        val perPkgLastForeground = HashMap<String, Long>()

        val e = UsageEvents.Event()
        while (events.getNextEvent(e)) {
            val pkg = e.packageName ?: continue
            when (e.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    perPkgLastResume[pkg] = e.timeStamp
                    perPkgLastForeground[pkg] = e.timeStamp
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val startAt = perPkgLastResume.remove(pkg) ?: continue
                    val delta = (e.timeStamp - startAt).coerceAtLeast(0L)
                    perPkgAccumulated[pkg] = (perPkgAccumulated[pkg] ?: 0L) + delta
                }
                // Android Q+ 在 split screen / 画中画里会有 ACTIVITY_PAUSED，
                // 但 queryEvents 仍以 MOVE_TO_BACKGROUND 为主要"切走"信号 —— 不处理 PAUSED。
            }
        }
        // 窗口结束时还在前台的，按当前时间收尾，避免被低估。
        val now = System.currentTimeMillis()
        for ((pkg, resumeAt) in perPkgLastResume) {
            val delta = (now - resumeAt).coerceAtLeast(0L)
            perPkgAccumulated[pkg] = (perPkgAccumulated[pkg] ?: 0L) + delta
        }

        // 过滤掉纯系统服务（无应用条目 / 累积 < 阈值），并按前台时长降序
        val minMs = MIN_VISIBLE_MS
        val apps = perPkgAccumulated
            .filter { (pkg, ms) -> ms >= minMs && pkg.isNotBlank() }
            .map { (pkg, ms) ->
                AppUsage(
                    packageName = pkg,
                    foregroundMs = ms,
                    lastForegroundEpochMs = perPkgLastForeground[pkg] ?: 0L,
                    label = appLabel(pkg),
                )
            }
            .sortedByDescending { it.foregroundMs }
            .take(topN)

        val total = apps.sumOf { it.foregroundMs }
        return Summary(
            totalForegroundMs = total,
            apps = apps,
            windowStartEpochMs = start,
            windowEndEpochMs = end,
        )
    }

    /** 包名 → 应用名。查不到时原样返回包名（与 [MemoryProcessSource.appLabel] 保持一致）。 */
    private fun appLabel(packageName: String): String = runCatching {
        context.packageManager.getApplicationLabel(
            context.packageManager.getApplicationInfo(packageName, 0),
        ).toString()
    }.getOrDefault(packageName)

    private companion object {
        /** 默认聚合窗口：24 小时 */
        const val DEFAULT_WINDOW_MS: Long = 24L * 60L * 60L * 1000L

        /** 默认返回前 10 个应用 */
        const val DEFAULT_TOP_N: Int = 10

        /** 不足 1 秒的前台停留直接忽略（系统服务/瞬时跳转噪声） */
        const val MIN_VISIBLE_MS: Long = 1_000L
    }
}
