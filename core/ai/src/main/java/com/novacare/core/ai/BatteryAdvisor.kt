package com.novacare.core.ai

import com.novacare.core.model.AiTier
import com.novacare.core.model.AppInfo
import com.novacare.core.model.AppUsageStats
import com.novacare.core.model.StandbyBucket

/**
 * F6 —— 智能电池优化（P2）
 *
 * 诚实约束：Android 8.0 起系统**不再向第三方提供按应用的耗电数据**
 * （BatteryManager 的耗电统计已移除，只有系统设置页能看到）。
 * 因此这里只能基于「使用时长 + 系统待机分级」给出**限制后台**的建议，
 * 并明确告知用户依据是什么 —— 不编造「某 App 偷耗电 30%」这种无法验证的结论。
 */
object BatteryAdvisor {

    data class BatteryAdvice(
        val packageName: String,
        val label: String,
        val reason: String,
        val suggestRestrictBackground: Boolean,
        val source: AiTier = AiTier.DETERMINISTIC,
    )

    fun advise(
        apps: List<AppInfo>,
        usage: Map<String, AppUsageStats>,
        nowMs: Long,
    ): List<BatteryAdvice> = apps.mapNotNull { app ->
        val stats = usage[app.packageName] ?: return@mapNotNull null
        val bucket = stats.standbyBucket
        val daysUnused = app.lastUsedEpochMs?.let { ((nowMs - it) / 86_400_000L).toInt() }

        val suspicious = bucket == StandbyBucket.RARE ||
            bucket == StandbyBucket.RESTRICTED ||
            (daysUnused ?: 0) >= 21

        if (!suspicious) return@mapNotNull null

        BatteryAdvice(
            packageName = app.packageName,
            label = app.label,
            reason = buildString {
                if (daysUnused != null) append("${daysUnused} 天未主动打开")
                else append("无主动使用记录")
                append("，系统待机分级 ${bucket.name}")
                append("。系统未提供按应用耗电数据，此处仅依据活跃度建议限制后台")
            },
            suggestRestrictBackground = true,
        )
    }
}
