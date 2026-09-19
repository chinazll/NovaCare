package com.novacare.core.ai

import com.google.common.truth.Truth.assertThat
import com.novacare.core.model.AppInfo
import com.novacare.core.model.AppUsageStats
import com.novacare.core.model.CleanRecommendation
import com.novacare.core.model.StandbyBucket
import org.junit.Test

class CleanAdvisorTest {

    private val now = 1_700_000_000_000L
    private val day = 86_400_000L

    private fun app(
        pkg: String,
        cacheBytes: Long,
        lastUsed: Long?,
        isSystem: Boolean = false,
    ) = AppInfo(
        packageName = pkg,
        label = pkg,
        isSystem = isSystem,
        sizeBytes = cacheBytes,
        cacheBytes = cacheBytes,
        dataBytes = 0L,
        versionName = "1",
        targetSdk = 34,
        installTimeEpochMs = now - 200 * day,
        updateTimeEpochMs = now - 10 * day,
        lastUsedEpochMs = lastUsed,
    )

    private fun usage(pkg: String, bucket: StandbyBucket) = pkg to AppUsageStats(
        packageName = pkg,
        totalTimeForegroundMs = 1_000,
        lastUsedEpochMs = now,
        standbyBucket = bucket,
        isInactive = false,
    )

    @Test
    fun tiny_cache_is_never_recommended() {
        val advices = CleanAdvisor.advise(
            apps = listOf(app("com.a", 1024L, now)),
            usage = mapOf(usage("com.a", StandbyBucket.ACTIVE)),
            nowMs = now,
        )
        assertThat(advices).isEmpty()
    }

    @Test
    fun long_unused_cache_is_clean_all() {
        val advices = CleanAdvisor.advise(
            apps = listOf(app("com.old", 200L * 1024 * 1024, now - 90 * day)),
            usage = mapOf(usage("com.old", StandbyBucket.RARE)),
            nowMs = now,
        )
        assertThat(advices).hasSize(1)
        val advice = advices.first()
        assertThat(advice.recommendation).isEqualTo(CleanRecommendation.CLEAN_ALL)
        assertThat(advice.recommendedBytes).isEqualTo(advice.totalBytes)
        // 每条结论都必须带可验证理由（信任是核心资产）
        assertThat(advice.reason).contains("90")
    }

    @Test
    fun recently_used_small_cache_is_kept() {
        val advices = CleanAdvisor.advise(
            apps = listOf(app("com.new", 200L * 1024 * 1024, now - 2 * day)),
            usage = mapOf(usage("com.new", StandbyBucket.ACTIVE)),
            nowMs = now,
        )
        assertThat(advices.first().recommendation).isEqualTo(CleanRecommendation.KEEP)
        assertThat(advices.first().recommendedBytes).isEqualTo(0L)
    }

    @Test
    fun missing_usage_data_falls_back_to_conservative_partial() {
        val advices = CleanAdvisor.advise(
            apps = listOf(app("com.unknown", 300L * 1024 * 1024, null)),
            usage = emptyMap(),
            nowMs = now,
        )
        val advice = advices.first()
        assertThat(advice.recommendation).isEqualTo(CleanRecommendation.CLEAN_PARTIAL)
        assertThat(advice.recommendedBytes).isEqualTo(advice.totalBytes / 2)
        // 必须如实说明「不知道使用时间」，不能假装知道
        assertThat(advice.reason).contains("未获取到使用记录")
    }

    @Test
    fun system_apps_are_marked_caution() {
        val advices = CleanAdvisor.advise(
            apps = listOf(app("com.sys", 300L * 1024 * 1024, now - 90 * day, isSystem = true)),
            usage = mapOf(usage("com.sys", StandbyBucket.RARE)),
            nowMs = now,
        )
        assertThat(advices.first().risk).isEqualTo(com.novacare.core.model.CleanRisk.CAUTION)
    }

    @Test
    fun advices_sorted_by_reclaimable_size() {
        val advices = CleanAdvisor.advise(
            apps = listOf(
                app("com.small", 100L * 1024 * 1024, now - 90 * day),
                app("com.big", 900L * 1024 * 1024, now - 90 * day),
            ),
            usage = mapOf(
                usage("com.small", StandbyBucket.RARE),
                usage("com.big", StandbyBucket.RARE),
            ),
            nowMs = now,
        )
        assertThat(advices.first().targetPackage).isEqualTo("com.big")
    }

    @Test
    fun zero_cache_apps_are_skipped() {
        val advices = CleanAdvisor.advise(
            apps = listOf(app("com.nocache", 0L, now - 90 * day)),
            usage = mapOf(usage("com.nocache", StandbyBucket.RARE)),
            nowMs = now,
        )
        assertThat(advices).isEmpty()
    }
}
