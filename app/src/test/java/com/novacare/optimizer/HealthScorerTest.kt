package com.novacare.optimizer

import com.novacare.optimizer.core.HealthScorer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * HealthScorer 单元测试
 *
 * P0-8：此前仓库完全没有 Kotlin 测试（build.gradle 里声明了 junit 依赖却一个测试都没有）。
 * HealthScorer 是纯 Kotlin、无 Android 依赖，最适合作为第一批测试对象。
 */
class HealthScorerTest {

    private lateinit var scorer: HealthScorer

    @Before
    fun setUp() {
        scorer = HealthScorer()
    }

    @Test
    fun `理想状态应得满分`() {
        val r = scorer.evaluate(
            usedStorageRatio = 0.3f,
            usedRamRatio = 0.4f,
            batteryHealthScore = 100,
            heavyCacheApps = 0,
            frozenCount = 0,
        )
        assertEquals(100, r.totalScore)
        assertEquals("状态良好", r.verdict)
    }

    @Test
    fun `极端糟糕状态应得低分`() {
        val r = scorer.evaluate(
            usedStorageRatio = 0.99f,
            usedRamRatio = 0.99f,
            batteryHealthScore = 10,
            heavyCacheApps = 20,
            frozenCount = 0,
        )
        assertTrue("分数应显著偏低，实际=${r.totalScore}", r.totalScore < 50)
        assertEquals("建议立即优化", r.verdict)
    }

    @Test
    fun `电池分数应直接采用 Rust 引擎结果而非本地重算`() {
        // P1-8：电池维度必须是 Rust 给多少就是多少，Kotlin 不得二次加工
        val r = scorer.evaluate(
            usedStorageRatio = 0.5f,
            usedRamRatio = 0.5f,
            batteryHealthScore = 42,
            heavyCacheApps = 0,
            frozenCount = 0,
        )
        assertEquals(42, r.batteryScore)
    }

    @Test
    fun `电池引擎不可用时退到中性分而非零分`() {
        val r = scorer.evaluate(
            usedStorageRatio = 0.5f,
            usedRamRatio = 0.5f,
            batteryHealthScore = -1,
            heavyCacheApps = 0,
            frozenCount = 0,
        )
        assertTrue("不应给出恐慌性的 0 分", r.batteryScore > 0)
    }

    @Test
    fun `frozenCount 应真正影响应用防护分`() {
        // P2-5：此前该参数完全没被使用
        val withoutFreeze = scorer.evaluate(
            usedStorageRatio = 0.5f, usedRamRatio = 0.5f,
            batteryHealthScore = 100, heavyCacheApps = 5, frozenCount = 0,
        )
        val withFreeze = scorer.evaluate(
            usedStorageRatio = 0.5f, usedRamRatio = 0.5f,
            batteryHealthScore = 100, heavyCacheApps = 5, frozenCount = 5,
        )
        assertTrue(
            "冻结应用后应用防护分应更高（${withFreeze.appScore} vs ${withoutFreeze.appScore}）",
            withFreeze.appScore > withoutFreeze.appScore,
        )
    }

    @Test
    fun `非法比率应安全降级为满分而非崩溃`() {
        val nan = scorer.evaluate(
            usedStorageRatio = Float.NaN,
            usedRamRatio = -1f,
            batteryHealthScore = 100,
            heavyCacheApps = 0,
            frozenCount = 0,
        )
        assertEquals(100, nan.storageScore)
        assertEquals(100, nan.ramScore)
    }

    @Test
    fun `总分应始终落在 0 到 100 之间`() {
        for (storage in listOf(-1f, 0f, 0.5f, 1f, 2f)) {
            for (ram in listOf(-1f, 0f, 0.5f, 1f)) {
                val r = scorer.evaluate(storage, ram, 50, 30, 100)
                assertTrue(r.totalScore in 0..100)
            }
        }
    }
}
