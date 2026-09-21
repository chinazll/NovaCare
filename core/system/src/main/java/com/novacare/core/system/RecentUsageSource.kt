package com.novacare.core.system

import android.app.usage.UsageStatsManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 指定时间窗内的应用使用时长（按天聚合，真实读数）
 *
 * 为什么单独建这个类，而不是复用 [UsageStatsSource]：
 *   [UsageStatsSource.query] 用 `INTERVAL_BEST` 且回溯一整年，得到的是
 *   **全年累计**前台时长。电池守护要讲的是"最近 24 小时谁在耗电"，
 *   拿全年累计去排行会把"过去一年用得多"误说成"今天耗电多" —— 这是实打实的误导。
 *   所以这里用 `INTERVAL_DAILY` + 明确的起止时间窗，只取窗口内的数据。
 *
 * 未授权时返回空 Map（UsageStatsManager 的行为），调用方据此如实降级。
 */
@Singleton
class RecentUsageSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** 窗口内的使用数据 */
    data class RecentUsage(
        /** 窗口内累计前台时长 */
        val foregroundMs: Long,
        /** 窗口内最后一次被记录到活动的时间（0 = 窗口内无记录） */
        val lastUsedEpochMs: Long,
    )

    /**
     * @param sinceMs 窗口起点（不含）
     * @param nowMs 窗口终点
     * @return 包名 → 窗口内使用数据；未授权或系统拒绝时为空 Map
     */
    fun query(sinceMs: Long, nowMs: Long): Map<String, RecentUsage> {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyMap()
        if (nowMs <= sinceMs) return emptyMap()

        val stats = runCatching {
            manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, sinceMs, nowMs)
        }.getOrNull().orEmpty()
        if (stats.isEmpty()) return emptyMap()

        val out = LinkedHashMap<String, RecentUsage>(stats.size)
        for (s in stats) {
            val pkg = s.packageName ?: continue
            val prev = out[pkg]
            out[pkg] = RecentUsage(
                foregroundMs = (prev?.foregroundMs ?: 0L) + s.totalTimeInForeground,
                lastUsedEpochMs = maxOf(prev?.lastUsedEpochMs ?: 0L, s.lastTimeUsed),
            )
        }
        return out
    }
}
