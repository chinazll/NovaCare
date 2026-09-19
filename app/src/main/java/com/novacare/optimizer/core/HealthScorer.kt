package com.novacare.optimizer.core

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 设备健康评分模型
 * 四维加权：存储 35% / 内存 30% / 电池 20% / 应用防护 15%
 * 评分语言与 One UI 设备管家一致：良好 / 一般 / 需要优化
 */
@Singleton
class HealthScorer @Inject constructor() {

    data class HealthResult(
        val totalScore: Int,
        val storageScore: Int,
        val ramScore: Int,
        val batteryScore: Int,
        val appScore: Int,
        val verdict: String,
    )

    fun evaluate(
        usedStorageRatio: Float,
        usedRamRatio: Float,
        batteryLevel: Int,
        batteryTemp: Float,
        heavyCacheApps: Int,   // 缓存 > 200MB 的应用数
        frozenCount: Int,      // 已冻结的臃肿应用数
    ): HealthResult {
        // 存储：使用率越低越好，85% 以上亮红灯
        val storageScore = when {
            usedStorageRatio < 0.6f -> 100
            usedStorageRatio < 0.75f -> 85
            usedStorageRatio < 0.85f -> 65
            else -> 40
        }
        // 内存：70% 以下流畅，90% 以上会重载
        val ramScore = when {
            usedRamRatio < 0.55f -> 100
            usedRamRatio < 0.7f -> 85
            usedRamRatio < 0.9f -> 60
            else -> 35
        }
        // 电池：电量 + 温度双因子
        val batteryScore = when {
            batteryLevel >= 60 && batteryTemp < 38f -> 100
            batteryLevel >= 30 -> 80
            batteryLevel >= 15 -> 55
            else -> 30
        }.let { if (batteryTemp >= 42f) (it - 20).coerceAtLeast(10) else it }

        // 应用防护：缓存大户与未冻结臃肿应用扣分
        val appScore = (100 - heavyCacheApps * 5 - (frozenCount.coerceAtLeast(0) * 0))
            .coerceIn(20, 100)

        val total = (storageScore * 0.35f + ramScore * 0.30f +
                batteryScore * 0.20f + appScore * 0.15f).toInt()

        val verdict = when {
            total >= 85 -> "状态良好"
            total >= 60 -> "需要关注"
            else -> "建议立即优化"
        }
        return HealthResult(total, storageScore, ramScore, batteryScore, appScore, verdict)
    }
}
