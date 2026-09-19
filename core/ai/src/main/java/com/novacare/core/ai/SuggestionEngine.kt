package com.novacare.core.ai

import com.novacare.core.common.formatBytes

/**
 * F7 —— 智能建议引擎（P2）
 *
 * 输入全部是本地事实（上次清理时间、当前可回收空间），
 * 输出「该不该现在优化」的结论。不联网、不调用模型。
 */
object SuggestionEngine {

    private const val SUGGEST_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000
    private const val MIN_RECLAIMABLE_BYTES = 300L * 1024 * 1024

    data class Suggestion(val title: String, val detail: String, val worthDoing: Boolean)

    fun suggest(lastCleanEpochMs: Long?, reclaimableBytes: Long, nowMs: Long): Suggestion? {
        if (reclaimableBytes < MIN_RECLAIMABLE_BYTES) return null

        val sinceMs = lastCleanEpochMs?.let { nowMs - it }
        val days = sinceMs?.div(86_400_000L)

        return when {
            days == null -> Suggestion(
                title = "可以清理一次",
                detail = "当前可回收 ${reclaimableBytes.formatBytes()}，尚未记录过清理时间",
                worthDoing = true,
            )

            sinceMs >= SUGGEST_INTERVAL_MS -> Suggestion(
                title = "距上次清理已 $days 天",
                detail = "当前可回收 ${reclaimableBytes.formatBytes()}，建议现在优化",
                worthDoing = true,
            )

            else -> Suggestion(
                title = "刚刚清理过",
                detail = "$days 天前清理过，当前可回收 ${reclaimableBytes.formatBytes()}，可稍后再处理",
                worthDoing = false,
            )
        }
    }
}
