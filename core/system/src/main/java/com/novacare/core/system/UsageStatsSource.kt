package com.novacare.core.system

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import com.novacare.core.model.AppUsageStats
import com.novacare.core.model.StandbyBucket
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 应用使用情况数据源（F1 / F3 / F6 的判定依据）
 *
 * 数据全部来自系统权威接口：
 * - UsageStatsManager.queryUsageStats → 前台时长 / 最后使用时间
 * - getAppStandbyBucket → 系统给出的活跃度分级（RARE / RESTRICTED 等）
 * - isAppInactive → 系统判定的不活跃状态
 *
 * 未授权时返回空 Map，上层据此把结论标注为「低置信度」，而不是编造使用时长。
 */
@Singleton
class UsageStatsSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun query(nowMs: Long): Map<String, AppUsageStats> {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyMap()

        val begin = nowMs - ONE_YEAR_MS
        val stats = runCatching {
            manager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, begin, nowMs)
        }.getOrNull() ?: return emptyMap()

        val out = LinkedHashMap<String, AppUsageStats>(stats.size)
        for (s in stats) {
            out[s.packageName] = AppUsageStats(
                packageName = s.packageName,
                totalTimeForegroundMs = s.totalTimeInForeground,
                lastUsedEpochMs = s.lastTimeUsed,
                standbyBucket = standbyBucketOf(),
                isInactive = runCatching { manager.isAppInactive(s.packageName) }.getOrDefault(false),
            )
        }
        return out
    }

    /**
     * 系统待机分级
     *
     * 诚实说明：Android 16（API 36）起 `getAppStandbyBucket(String)` 已被移除，
     * 第三方 App **再也拿不到按应用的待机分级**（只剩无参版本，只能查自己）。
     * 因此这里如实返回 UNKNOWN —— 不伪造分级。
     * 可继续使用的不活跃信号是 [AppUsageStats.isInactive]（isAppInactive）。
     */
    private fun standbyBucketOf(): StandbyBucket = StandbyBucket.UNKNOWN

    private companion object {
        const val ONE_YEAR_MS = 365L * 24 * 60 * 60 * 1000
    }
}
