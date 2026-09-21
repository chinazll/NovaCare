package com.novacare.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HealthScoreTest {

    @Test
    fun verdict_thresholds() {
        assertThat(HealthScore.verdictOf(90)).isEqualTo("状态良好")
        assertThat(HealthScore.verdictOf(70)).isEqualTo("需要关注")
        assertThat(HealthScore.verdictOf(30)).isEqualTo("建议立即优化")
    }

    @Test
    fun weights_sum_to_one() {
        val total = HealthDimension.entries.sumOf { it.weight.toDouble() }
        assertThat(total).isWithin(0.001).of(1.0)
    }

    @Test
    fun standby_bucket_inactivity_ordering() {
        assertThat(StandbyBucket.NEVER.inactivityRank)
            .isGreaterThan(StandbyBucket.RARE.inactivityRank)
        assertThat(StandbyBucket.ACTIVE.inactivityRank)
            .isLessThan(StandbyBucket.WORKING_SET.inactivityRank)
    }

    @Test
    fun junk_kind_safety_defaults() {
        assertThat(JunkKind.CACHE.isSafeByDefault).isTrue()
        assertThat(JunkKind.DUPLICATE.isSafeByDefault).isFalse()
    }

    @Test
    fun days_since_last_use_handles_unknown() {
        val app = AppInfo(
            packageName = "p", label = "P", isSystem = false, sizeBytes = 0,
            cacheBytes = 0, dataBytes = 0, versionName = "", targetSdk = 34,
            installTimeEpochMs = 0, updateTimeEpochMs = 0, lastUsedEpochMs = null,
        )
        assertThat(app.daysSinceLastUse(1_000_000_000L)).isNull()
    }
}
