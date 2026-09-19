package com.novacare.core.domain

import com.novacare.core.ai.CleanAdvisor
import com.novacare.core.common.formatBytes
import com.novacare.core.ai.FreezeAdvisor
import com.novacare.core.ai.IntentParser
import com.novacare.core.ai.LlmIntentParser
import com.novacare.core.model.CleanAdvice
import com.novacare.core.model.FreezeCandidate
import com.novacare.core.model.IntentType
import com.novacare.core.model.ParsedIntent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI 助手（F5 —— 自然语言 → 结构化操作）
 *
 * 默认走本地规则解析（零网络）；用户在设置里开启云端 AI 且填了 key 时，
 * 才走 [LlmIntentParser]。解析结果**只用于生成待确认的计划**，不直接执行。
 */
@Singleton
class AssistantUseCase @Inject constructor(
    private val ruleParser: IntentParser,
    private val llmParser: LlmIntentParser,
) {

    suspend fun parse(text: String, snapshot: DeviceSnapshot?): ParsedIntent {
        val knownApps = snapshot?.apps?.associate { it.label to it.packageName } ?: emptyMap()
        return if (llmParser.llmConfigured()) llmParser.parse(text, knownApps)
        else ruleParser.parse(text, knownApps)
    }

    fun apply(
        intent: ParsedIntent,
        snapshot: DeviceSnapshot,
        advancedMode: Boolean,
    ): AssistantOutcome {
        val excluded = intent.excludePackages
        val olderThanDays = intent.olderThanDays

        return when (intent.intent) {
            IntentType.CLEAN_CACHE -> {
                val advices = CleanAdvisor
                    .advise(snapshot.apps, snapshot.usage, snapshot.nowMs)
                    .filter { it.targetPackage !in excluded }
                    .filter { advice ->
                        olderThanDays?.let { days ->
                            val pkg = advice.targetPackage ?: return@filter true
                            val app = snapshot.apps.firstOrNull { it.packageName == pkg }
                            val used = app?.daysSinceLastUse(snapshot.nowMs)
                            used == null || used >= days
                        } ?: true
                    }
                    .filter { it.recommendation != com.novacare.core.model.CleanRecommendation.KEEP }
                AssistantOutcome.Plan(advices, "已按你的要求筛选缓存清理项")
            }

            IntentType.CLEAN_JUNK -> {
                val items = snapshot.junk?.items
                    ?.filter { it.risk == com.novacare.core.model.CleanRisk.SAFE }
                    ?.map { item ->
                        CleanAdvice(
                            targetPackage = null,
                            targetLabel = item.label,
                            targetPath = item.path,
                            totalBytes = item.bytes,
                            recommendedBytes = item.bytes,
                            recommendation = com.novacare.core.model.CleanRecommendation.CLEAN_ALL,
                            risk = item.risk,
                            summary = "${item.label}",
                            reason = "内核确定性扫描命中",
                            source = com.novacare.core.model.AiTier.DETERMINISTIC,
                        )
                    } ?: emptyList()
                AssistantOutcome.Plan(items, "已筛选安全垃圾项")
            }

            IntentType.FREEZE_UNUSED_APPS -> {
                val candidates = FreezeAdvisor
                    .advise(snapshot.apps, snapshot.usage, snapshot.nowMs, false)
                    .filter { it.app.packageName !in excluded }
                AssistantOutcome.Freeze(candidates, "已筛出不常用应用")
            }

            IntentType.ANALYZE_STORAGE -> {
                val used = snapshot.storage.totalBytes - snapshot.storage.availableBytes
                val percent = if (snapshot.storage.totalBytes > 0L) {
                    (used * 100 / snapshot.storage.totalBytes).toInt()
                } else {
                    0
                }
                AssistantOutcome.Info(
                    "已用 $percent%（${used.formatBytes()} / ${snapshot.storage.totalBytes.formatBytes()}），" +
                        "共 ${snapshot.apps.size} 个应用，内核可用：${snapshot.engineAvailable}",
                )
            }

            IntentType.UNKNOWN -> AssistantOutcome.Info(
                "没听懂这条指令。可以试试：清理半年没用的应用缓存 / 冻结不常用的应用 / 分析存储",
            )
        }
    }
}

sealed interface AssistantOutcome {
    data class Plan(val advices: List<CleanAdvice>, val explanation: String) : AssistantOutcome
    data class Freeze(val candidates: List<FreezeCandidate>, val explanation: String) : AssistantOutcome
    data class Info(val explanation: String) : AssistantOutcome
}
