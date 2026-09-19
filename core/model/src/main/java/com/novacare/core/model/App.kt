package com.novacare.core.model

/**
 * 应用领域模型
 *
 * 关键修复：cacheBytes / dataBytes 来自 StorageStatsManager 的真实读数。
 * 上一版恒为 0（注释写着"无 root 无法读取"），导致「应用防护」评分永远满分。
 */

data class AppInfo(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    /** 应用总占用（cache + data） */
    val sizeBytes: Long,
    val cacheBytes: Long,
    val dataBytes: Long,
    val versionName: String,
    val targetSdk: Int,
    val installTimeEpochMs: Long,
    val updateTimeEpochMs: Long,
    /** 最后使用时间；null = 未知（未授予 PACKAGE_USAGE_STATS） */
    val lastUsedEpochMs: Long? = null,
) {
    /** 未使用天数；null = 未知 */
    fun daysSinceLastUse(nowMs: Long): Int? =
        lastUsedEpochMs?.let { ((nowMs - it) / 86_400_000L).toInt() }
}

/** 系统权威的活跃度分级（UsageStatsManager.getAppStandbyBucket） */
enum class StandbyBucket {
    ACTIVE, WORKING_SET, FREQUENT, RARE, RESTRICTED, NEVER, UNKNOWN;

    /** 越靠后越"不常用" */
    val inactivityRank: Int get() = when (this) {
        ACTIVE -> 0; WORKING_SET -> 1; FREQUENT -> 2
        RARE -> 3; RESTRICTED -> 4; NEVER -> 5; UNKNOWN -> -1
    }
}

data class AppUsageStats(
    val packageName: String,
    val totalTimeForegroundMs: Long,
    val lastUsedEpochMs: Long,
    val standbyBucket: StandbyBucket,
    /** 系统判定是否处于不活跃状态（isAppInactive） */
    val isInactive: Boolean,
)
