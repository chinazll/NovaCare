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

        advices += CleanAdvisor.advise(snapshot.apps, snapshot.usage, snapshot.nowMs)
            .filter { it.recommendation != CleanRecommendation.KEEP }

        // 内核扫出的垃圾文件（缓存 / 临时 / 空目录 / 重复文件）
        snapshot.junk?.items
            ?.filter { includeRisky || it.risk == CleanRisk.SAFE }
            ?.filter { it.bytes > 0 }
            ?.forEach { item ->
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
                    costNote = if (item.risk == CleanRisk.SAFE) {
                        "属于缓存 / 临时文件，删除无感知"
                    } else {
                        item.riskNote.ifBlank { "需要确认：可能影响正在使用的应用" }
                    },
                    confidence = 0.95f,
                    source = com.novacare.core.model.AiTier.DETERMINISTIC,
                )
            }

        val selected = advices
            .filter { it.risk == CleanRisk.SAFE && it.recommendation != CleanRecommendation.KEEP }
            .map { it.key() }
            .toSet()

        return CleanPlan(
            advices = advices.sortedByDescending { it.recommendedBytes },
            totalReclaimableBytes = advices.sumOf { it.recommendedBytes },
            defaultSelected = selected,
            scanDurationMs = scanDurationMs,
        )
    }
}

/** 计划项的唯一键（勾选状态用） */
fun CleanAdvice.key(): String = targetPath ?: ("pkg:" + targetPackage.orEmpty())
