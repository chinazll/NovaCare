package com.novacare.core.ai

import com.google.common.truth.Truth.assertThat
import com.novacare.core.model.AppInfo
import com.novacare.core.model.AppUsageStats
import com.novacare.core.model.FreezeRisk
import com.novacare.core.model.StandbyBucket
import org.junit.Test

class FreezeAdvisorTest {

    private val now = 1_700_000_000_000L
    private val day = 86_400_000L

    private fun app(pkg: String, lastUsed: Long?, isSystem: Boolean) = AppInfo(
        packageName = pkg,
        label = pkg,
        isSystem = isSystem,
        sizeBytes = 100L * 1024 * 1024,
        cacheBytes = 10L * 1024 * 1024,
        dataBytes = 90L * 1024 * 1024,
        versionName = "1",
        targetSdk = 34,
        installTimeEpochMs = now - 300 * day,
        updateTimeEpochMs = now - 5 * day,
        lastUsedEpochMs = lastUsed,
    )

    private fun usage(pkg: String, bucket: StandbyBucket) = pkg to AppUsageStats(
        packageName = pkg,
        totalTimeForegroundMs = 0L,
        lastUsedEpochMs = now,
        standbyBucket = bucket,
        isInactive = true,
    )

    @Test
    fun rare_bucket_app_is_candidate() {
        val candidates = FreezeAdvisor.advise(
            apps = listOf(app("com.rare", now - 40 * day, false)),
            usage = mapOf(usage("com.rare", StandbyBucket.RARE)),
            nowMs = now,
        )
        assertThat(candidates).hasSize(1)
        assertThat(candidates.first().reason).contains("40")
    }

    @Test
    fun active_app_is_not_candidate() {
        val candidates = FreezeAdvisor.advise(
            apps = listOf(app("com.active", now - 1 * day, false)),
            usage = mapOf(usage("com.active", StandbyBucket.ACTIVE)),
            nowMs = now,
        )
        assertThat(candidates).isEmpty()
    }

    @Test
    fun system_apps_hidden_by_default() {
        val apps = listOf(app("com.sys", now - 90 * day, true))
        assertThat(
            FreezeAdvisor.advise(apps, mapOf(usage("com.sys", StandbyBucket.RARE)), now),
        ).isEmpty()

        val withSystem = FreezeAdvisor.advise(
            apps, mapOf(usage("com.sys", StandbyBucket.RARE)), now, includeSystemApps = true,
        )
        assertThat(withSystem).hasSize(1)
        // 系统应用必须标记为有风险，并如实说明
        assertThat(withSystem.first().risk).isEqualTo(FreezeRisk.RISKY)
        assertThat(withSystem.first().reason).contains("系统应用")
    }

    @Test
    fun never_fakes_battery_saving() {
        val candidates = FreezeAdvisor.advise(
            apps = listOf(app("com.rare", now - 90 * day, false)),
            usage = mapOf(usage("com.rare", StandbyBucket.NEVER)),
            nowMs = now,
        )
        // Android 8+ 已移除按应用耗电 API —— 必须是 null，不能编造
        assertThat(candidates.first().estimatedBatterySaving).isNull()
    }
}
