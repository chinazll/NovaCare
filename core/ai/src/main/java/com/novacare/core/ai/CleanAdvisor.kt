package com.novacare.core.ai

import com.novacare.core.common.formatBytes
import com.novacare.core.model.AiTier
import com.novacare.core.model.AppInfo
import com.novacare.core.model.AppUsageStats
import com.novacare.core.model.CleanAdvice
import com.novacare.core.model.CleanRecommendation
import com.novacare.core.model.CleanRisk
import com.novacare.core.model.StandbyBucket

/**
 * F1 —— 智能缓存清理（P0，核心杀手功能）
 *
 * 没有这一层时，清理器只是「列出微信 2.3GB、抖音 1.8GB」让用户自己挑，
 * 用户不知道删了会怎样 → **不敢删** → 清理率极低。
 *
 * 这一层做的是：结合「缓存大小 + 使用频率 + 是否系统应用」给出
 * **删哪个 / 删多少 / 代价多大** 的结论，并且**每条结论都带可验证理由**。
 *
 * 本实现是 L1 确定性决策（不调用任何模型）：
 * 规则透明、可单测、可在最差设备上运行 —— 符合「确定性优先，AI 增强」原则。
 */
object CleanAdvisor {

    /** 小于这个体积不做建议：清理收益低于重建代价 */
    private const val MIN_CACHE_BYTES = 32L * 1024 * 1024
    private const val UNUSED_DAYS_FULL = 60
    private const val UNUSED_DAYS_PARTIAL = 14

    fun advise(
        apps: List<AppInfo>,
        usage: Map<String, AppUsageStats>,
        nowMs: Long,
    ): List<CleanAdvice> = apps.mapNotNull { app ->
        val cache = app.cacheBytes
        if (cache <= 0L) return@mapNotNull null
        if (cache < MIN_CACHE_BYTES) return@mapNotNull null

        val stats = usage[app.packageName]
        val daysUnused = app.lastUsedEpochMs?.let { ((nowMs - it) / 86_400_000L).toInt() }
        val bucket = stats?.standbyBucket ?: StandbyBucket.UNKNOWN

        val (recommendation, recommendedBytes, reason) = decide(cache, daysUnused, bucket)

        val costNote = when (recommendation) {
            CleanRecommendation.CLEAN_ALL ->
                "清理后会重新生成缓存，首次打开该应用可能略慢"

            CleanRecommendation.CLEAN_PARTIAL ->
                "只清理一部分，保留较新的缓存，避免重新加载"

            CleanRecommendation.KEEP ->
                "缓存较小或使用频繁，清理收益低于重建代价"
        }

        val summary = when (recommendation) {
            CleanRecommendation.CLEAN_ALL ->
                "${app.label} 缓存 ${cache.formatBytes()}，建议全部清理"

            CleanRecommendation.CLEAN_PARTIAL ->
                "${app.label} 缓存 ${cache.formatBytes()}，建议清理 ${recommendedBytes.formatBytes()}"

            CleanRecommendation.KEEP ->
                "${app.label} 缓存 ${cache.formatBytes()}，建议保留"
        }

        CleanAdvice(
            targetPackage = app.packageName,
            targetLabel = app.label,
            targetPath = null,
            totalBytes = cache,
            recommendedBytes = recommendedBytes,
            recommendation = recommendation,
            risk = if (app.isSystem) CleanRisk.CAUTION else CleanRisk.SAFE,
            summary = summary,
            reason = reason,
            costNote = costNote,
            confidence = if (daysUnused == null) 0.5f else 0.9f,
            source = AiTier.DETERMINISTIC,
        )
    }.sortedByDescending { it.recommendedBytes }

    private fun decide(
        cacheBytes: Long,
        daysUnused: Int?,
        bucket: StandbyBucket,
    ): Triple<CleanRecommendation, Long, String> = when {
        // 使用数据缺失：保守策略，只清一半，并如实说明「不知道上次使用时间」
        daysUnused == null -> Triple(
            CleanRecommendation.CLEAN_PARTIAL,
            cacheBytes / 2,
            "未获取到使用记录（可能未授权使用情况访问权限），保守建议只清理一半",
        )

        daysUnused >= UNUSED_DAYS_FULL -> Triple(
            CleanRecommendation.CLEAN_ALL,
            cacheBytes,
            "${daysUnused} 天未使用，缓存已无保留价值（系统待机分级：${bucket.name}）",
        )

        daysUnused >= UNUSED_DAYS_PARTIAL -> Triple(
            CleanRecommendation.CLEAN_PARTIAL,
            cacheBytes * 60 / 100,
            "最近 $daysUnused 天仍有使用，只清理较早的 60%（系统待机分级：${bucket.name}）",
        )

        cacheBytes >= 512L * 1024 * 1024 -> Triple(
            CleanRecommendation.CLEAN_PARTIAL,
            cacheBytes * 30 / 100,
            "虽然日常在用，但缓存已达 ${cacheBytes.formatBytes()}，只清理 30% 降低重建代价",
        )

        else -> Triple(
            CleanRecommendation.KEEP,
            0L,
            "最近 $daysUnused 天内有使用，且缓存规模不大，建议保留",
        )
    }
}
