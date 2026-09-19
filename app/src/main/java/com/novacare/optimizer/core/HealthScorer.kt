package com.novacare.optimizer.core

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 设备健康评分模型（One UI 9 设备管家风格）
 *
 * 加权：存储 35% · 内存 30% · 电池 20% · 应用防护 15%
 *
 * **单一算法原则（修复 P1-8）**：
 * 电池维度的分数**不再在 Kotlin 侧计算**，而是直接采用 Rust 引擎
 * `battery_monitor::analyze()` 的结果（[DeviceRepository.getDeviceStatus] 传入）。
 * 此前 Kotlin（BatteryScreen）与 Rust 各有一套阈值完全不同的算法，
 * 同一个「电池健康度」会给出互相矛盾的数字。现在 Rust 是唯一来源。
 *
 * **frozenCount 生效（修复 P2-5）**：
 * 此前该参数传入后从未被使用，是死参数。现在用于「应用防护」加分：
 * 冻结了臃肿应用 = 用户主动做了治理，给予正向反馈。
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

    /**
     * @param usedStorageRatio 存储使用率 0~1
     * @param usedRamRatio     内存使用率 0~1
     * @param batteryHealthScore 电池健康度，**由 Rust 引擎给出**（未知时传 -1 走后备）
     * @param heavyCacheApps   缓存超过阈值的应用数量
     * @param frozenCount      已冻结应用数量
     */
    fun evaluate(
        usedStorageRatio: Float,
        usedRamRatio: Float,
        batteryHealthScore: Int,
        heavyCacheApps: Int,
        frozenCount: Int = 0,
    ): HealthResult {
        val storageScore = when {
            usedStorageRatio.isNaN() || usedStorageRatio < 0f -> 100
            usedStorageRatio < 0.60f -> 100
            usedStorageRatio < 0.75f -> 85
            usedStorageRatio < 0.85f -> 65
            else -> 40
        }

        val ramScore = when {
            usedRamRatio.isNaN() || usedRamRatio < 0f -> 100
            usedRamRatio < 0.55f -> 100
            usedRamRatio < 0.70f -> 85
            usedRamRatio < 0.90f -> 60
            else -> 35
        }

        // 电池：Rust 引擎为唯一来源；引擎不可用（-1）时用一个保守后备值
        val batteryScore = if (batteryHealthScore in 0..100) {
            batteryHealthScore
        } else {
            80 // 后备：未知状态下给中性分，既不虚高也不恐慌
        }

        // 应用防护：缓存大户扣分，已治理（冻结）加分
        val appScore = (100 - heavyCacheApps * 5 + frozenCount * 3).coerceIn(20, 100)

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
