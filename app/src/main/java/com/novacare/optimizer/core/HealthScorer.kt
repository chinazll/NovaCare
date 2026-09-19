package com.novacare.optimizer.core

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 设备健康评分模型（One UI 9 设备管家风格）
 *
 * 评分算法：
 * - 存储 35%：使用率越低越好
 * - 内存 30%：使用率 < 70% 为佳
 * - 电池 20%：电量 + 温度双因子
 * - 应用防护 15%：缓存大户 + 臃肿应用数量
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
        heavyCacheApps: Int,
        frozenCount: Int,
    ): HealthResult {
        val storageScore = when {
            usedStorageRatio.isNaN() || usedStorageRatio < 0f -> 100
            usedStorageRatio < 0.6f -> 100
            usedStorageRatio < 0.75f -> 85
            usedStorageRatio < 0.85f -> 65
            else -> 40
        }
        val ramScore = when {
            usedRamRatio.isNaN() || usedRamRatio < 0f -> 100
            usedRamRatio < 0.55f -> 100
            usedRamRatio < 0.7f -> 85
            usedRamRatio < 0.9f -> 60
            else -> 35
        }
        val baseBattery = when {
            batteryLevel >= 60 && batteryTemp < 38f -> 100
            batteryLevel >= 30 -> 80
            batteryLevel >= 15 -> 55
            else -> 30
        }
        val batteryScore = if (batteryTemp >= 42f) (baseBattery - 20).coerceAtLeast(10) else baseBattery

        val appScore = (100 - heavyCacheApps * 5).coerceIn(20, 100)

        val total = (
            storageScore * 0.35f +
            ramScore * 0.30f +
            batteryScore * 0.20f +
            appScore * 0.15f
        ).toInt().coerceIn(0, 100)

        val verdict = when {
            total >= 85 -> "状态良好"
            total >= 60 -> "需要关注"
            else -> "建议立即优化"
        }
        return HealthResult(total, storageScore, ramScore, batteryScore, appScore, verdict)
    }
}