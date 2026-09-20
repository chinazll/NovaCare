package com.novacare.core.domain

import com.novacare.core.ai.CleanAdvisor
import com.novacare.core.common.formatBytes
import com.novacare.core.model.CleanAdvice
import com.novacare.core.model.CleanPlan
import com.novacare.core.model.CleanRecommendation
import com.novacare.core.model.CleanRisk
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 生成优化计划（F1 的核心产出）
 *
 * 与「列出缓存让你自己选」的区别：
 * 这里给出**结论 + 理由 + 代价**，并且默认只勾选安全项。
 */
@Singleton
class BuildOptimizePlanUseCase @Inject constructor() {

    operator fun invoke(
        snapshot: DeviceSnapshot,
        scanDurationMs: Long,
        includeRisky: Boolean = false,
    ): CleanPlan {
        val advices = mutableListOf<CleanAdvice>()

        val advisorAdvices = CleanAdvisor.advise(snapshot.apps, snapshot.usage, snapshot.nowMs)
        // 被判 KEEP 的条目不进清单，但数量必须外泄给 UI —— 否则用户看到空清单
        // 却不知道原因通常是「缺使用情况访问权限」（CleanAdvisor 零伪造决策）。
        val keptCount = advisorAdvices.count { it.recommendation == CleanRecommendation.KEEP }

        advices += advisorAdvices
            .filter { it.recommendation != CleanRecommendation.KEEP }

        // 内核扫出的垃圾文件（缓存 / 临时 / 空目录 / 重复文件）
        // 过滤规则：
        //   - SAFE    → 始终显示，默认勾选
        //   - CAUTION → 始终显示（如 DUPLICATE：同内容的另一个副本还在，删一个不影响）
        //   - RISKY   → 仅 includeRisky=true 时显示（如应用数据目录）
        snapshot.junk?.items
            ?.filter { item ->
                item.risk == CleanRisk.SAFE ||
                    item.risk == CleanRisk.CAUTION ||
                    (includeRisky && item.risk == CleanRisk.RISKY)
            }
            ?.filter { it.bytes > 0 }
            ?.forEach { item ->
                val isSafe = item.risk == CleanRisk.SAFE
                advices += CleanAdvice(
                    targetPackage = null,
                    targetLabel = item.label,
                    targetPath = item.path,
                    totalBytes = item.bytes,
                    recommendedBytes = item.bytes,
                    recommendation = CleanRecommendation.CLEAN_ALL,
                    risk = item.risk,
                    summary = "${item.label} ${item.bytes.formatBytes()}",
                    reason = "内核确定性扫描命中（类型：${item.kind.name}）",
                    costNote = if (isSafe) {
                        "属于缓存 / 临时文件，删除无感知"
                    } else if (item.risk == CleanRisk.CAUTION) {
                        item.riskNote.ifBlank {
                            when (item.kind) {
                                com.novacare.core.model.JunkKind.DUPLICATE ->
                                    "重复文件有另一份完全相同的副本，删一个不影响数据"
                                com.novacare.core.model.JunkKind.RESIDUAL ->
                                    "已卸载应用遗留，删除无影响"
                                else -> "需要确认后再清理"
                            }
                        }
                    } else {
                        item.riskNote.ifBlank { "风险较高，请确认后再清理" }
                    },
                    confidence = 0.95f,
                    source = com.novacare.core.model.AiTier.DETERMINISTIC,
                )
            }

        //
        // 默认勾选策略：**只自动勾 SAFE**。
        // CAUTION / RISKY 项照样出现在清单里（用户可逐条勾选执行），但不替用户预先勾上 ——
        // 「需确认」三个字如果本身就是默认值，它就失去意义了。
        // 代价是 defaultSelected 可能为空（典型场景：内核扫出的垃圾全是 CAUTION）。
        // 这种情况**不靠放宽策略救**，而是交给 UI 如实说明原因 + 给一键勾选入口
        // （见 CleanViewModel.computeAvailability 的 NeedSelection 分支）。
        val selected = advices
            .filter { it.risk == CleanRisk.SAFE && it.recommendation != CleanRecommendation.KEEP }
            .map { it.key() }
            .toSet()

        return CleanPlan(
            advices = advices.sortedByDescending { it.recommendedBytes },
            totalReclaimableBytes = advices.sumOf { it.recommendedBytes },
            defaultSelected = selected,
            scanDurationMs = scanDurationMs,
            keptCount = keptCount,
        )
    }
}

/** 计划项的唯一键（勾选状态用） */
fun CleanAdvice.key(): String = targetPath ?: ("pkg:" + targetPackage.orEmpty())
