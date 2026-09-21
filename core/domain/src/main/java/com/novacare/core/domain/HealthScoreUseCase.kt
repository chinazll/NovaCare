package com.novacare.core.domain

import com.novacare.core.model.DimensionScore
import com.novacare.core.model.HealthDimension
import com.novacare.core.model.HealthScore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 健康评分（四个维度，权重与 [HealthDimension] 严格一致）
 *
 * 诚实规则：**拿不到数据的维度不参与评分**，权重按剩余维度重新归一化，
 * 并在结论里说明。绝不用「假设值」补齐 —— 上一版就是这样把
 * 「应用防护」永远算成满分的。
 */
@Singleton
class HealthScoreUseCase @Inject constructor() {

    operator fun invoke(snapshot: DeviceSnapshot, batteryScore: Int?): HealthScore {
        val dims = mutableListOf<DimensionScore>()

        val total = snapshot.storage.totalBytes
        if (total > 0L) {
            val usedPercent = ((snapshot.storage.totalBytes - snapshot.storage.availableBytes)
                .toDouble() / total.toDouble() * 100).toInt().coerceIn(0, 100)
            dims += DimensionScore(
                dimension = HealthDimension.STORAGE,
                score = (100 - usedPercent).coerceIn(0, 100),
                summary = "已用 $usedPercent%",
            )
        }

        if (snapshot.memory.totalBytes > 0L) {
            val availPercent =
                (snapshot.memory.availableBytes.toDouble() / snapshot.memory.totalBytes.toDouble() * 100)
                    .toInt().coerceIn(0, 100)
            dims += DimensionScore(
                dimension = HealthDimension.MEMORY,
                score = availPercent,
                summary = "可用内存 $availPercent%",
            )
        }

        if (batteryScore != null) {
            dims += DimensionScore(
                dimension = HealthDimension.BATTERY,
                score = batteryScore.coerceIn(0, 100),
                summary = "电量 ${snapshot.battery.levelPercent}%",
            )
        } else if (snapshot.battery.levelPercent > 0) {
            // 引擎不可用时的降级：只用真实电量，不伪造「健康度」
            dims += DimensionScore(
                dimension = HealthDimension.BATTERY,
                score = snapshot.battery.levelPercent.coerceIn(0, 100),
                summary = "电量 ${snapshot.battery.levelPercent}%（内核不可用，未评估健康度）",
            )
        }

        if (snapshot.apps.isNotEmpty()) {
            var stale = 0
            var unknown = 0
            for (app in snapshot.apps) {
                when (val days = app.daysSinceLastUse(snapshot.nowMs)) {
                    null -> unknown++
                    else -> if (days >= 30) stale++
                }
            }
            // 拿不到使用记录的 app（未授权 PACKAGE_USAGE_STATS）**不得**计入陈旧：
            // 旧写法是 `days == null || days >= 30`，一旦未授权就把全部 app 当成
            // 「超过 30 天未用」，APP 维度直接 0 分 —— 这是凭空编造的结论。
            val measurable = snapshot.apps.size - unknown
            if (measurable > 0) {
                val penalty = (stale * 100 / measurable).coerceIn(0, 100)
                dims += DimensionScore(
                    dimension = HealthDimension.APP,
                    score = 100 - penalty,
                    summary = if (unknown > 0) {
                        "$stale / $measurable 个应用超过 30 天未使用（另有 $unknown 个无使用记录，未参与评分）"
                    } else {
                        "$stale / ${snapshot.apps.size} 个应用超过 30 天未使用"
                    },
                )
            }
        }

        if (dims.isEmpty()) {
            return HealthScore(
                total = 0,
                dimensions = emptyList(),
                verdict = "数据不足，无法评分",
            )
        }

        // 权重按「有数据的维度」重新归一化
        val weightSum = dims.sumOf { it.dimension.weight.toDouble() }
        val scored = dims.sumOf { it.score.toDouble() * it.dimension.weight.toDouble() } / weightSum

        return HealthScore(
            total = scored.toInt().coerceIn(0, 100),
            dimensions = dims,
            verdict = HealthScore.verdictOf(scored.toInt().coerceIn(0, 100)),
        )
    }
}
