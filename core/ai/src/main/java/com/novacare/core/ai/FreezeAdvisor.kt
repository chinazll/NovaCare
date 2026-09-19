package com.novacare.core.ai

import com.novacare.core.common.formatBytes
import com.novacare.core.model.AiTier
import com.novacare.core.model.AppInfo
import com.novacare.core.model.AppUsageStats
import com.novacare.core.model.FreezeCandidate
import com.novacare.core.model.FreezeRisk
import com.novacare.core.model.StandbyBucket

/**
 * F3 —— 不常用应用识别
 *
 * Hail 给的是 200 个应用的全列表让用户自己挑；NovaCare 给的是
 * **排序 + 可验证理由**，让用户敢点「冻结」。
 *
 * 判定信号（全部来自系统权威数据）：
 * - UsageStatsManager 的最后使用时间
 * - getAppStandbyBucket 的系统分级（RARE / RESTRICTED / NEVER 最有说服力）
 * - 是否系统 / 预装应用（决定风险等级）
 *
 * 诚实声明：Android 8+ 已移除按应用的耗电统计 API（BatteryManager 不再提供），
 * 因此 [FreezeCandidate.estimatedBatterySaving] 保持 null —— **不编造省电比例**。
 */
object FreezeAdvisor {

    private const val UNUSED_DAYS = 30

    fun advise(
        apps: List<AppInfo>,
        usage: Map<String, AppUsageStats>,
        nowMs: Long,
        includeSystemApps: Boolean = false,
    ): List<FreezeCandidate> {
        return apps.mapNotNull { app ->
            if (app.isSystem && !includeSystemApps) return@mapNotNull null
            val stats = usage[app.packageName]
            val daysUnused = app.lastUsedEpochMs?.let { ((nowMs - it) / 86_400_000L).toInt() }
            val bucket = stats?.standbyBucket ?: StandbyBucket.UNKNOWN

            val rare = bucket == StandbyBucket.RARE ||
                bucket == StandbyBucket.RESTRICTED ||
                bucket == StandbyBucket.NEVER
            val stale = (daysUnused ?: 0) >= UNUSED_DAYS
            if (!rare && !stale) return@mapNotNull null

            val risk = when {
                app.isSystem -> FreezeRisk.RISKY
                bucket == StandbyBucket.WORKING_SET || bucket == StandbyBucket.ACTIVE -> FreezeRisk.CAUTION
                else -> FreezeRisk.SAFE
            }

            val reason = buildString {
                if (daysUnused != null) append("${daysUnused} 天未打开") else append("无使用记录")
                append("，占用 ${app.sizeBytes.formatBytes()}")
                if (bucket != StandbyBucket.UNKNOWN) append("，系统待机分级：${bucket.name}")
                if (app.isSystem) append("（系统应用，冻结前请确认）")
            }

            FreezeCandidate(
                app = app,
                daysUnused = daysUnused,
                standbyBucket = bucket,
                risk = risk,
                reason = reason,
                estimatedBatterySaving = null,
                confidence = if (daysUnused == null) 0.5f else 0.85f,
                source = AiTier.DETERMINISTIC,
            )
        }.sortedWith(compareByDescending<FreezeCandidate> { it.risk == FreezeRisk.SAFE }
            .thenByDescending { it.daysUnused ?: 0 }
            .thenByDescending { it.app.sizeBytes })
    }
}
