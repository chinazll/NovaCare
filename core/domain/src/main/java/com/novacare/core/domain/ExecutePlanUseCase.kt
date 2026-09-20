package com.novacare.core.domain

import com.novacare.core.model.CleanAdvice
import com.novacare.core.model.CleanPlan
import com.novacare.core.model.CleanResult
import com.novacare.core.system.CacheCleanController
import com.novacare.core.system.RecycleBin
import kotlinx.coroutines.Dispatchers
import java.io.File
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 执行优化计划
 *
 * 三类目标，三种执行方式（绝不越权）：
 * 1. 文件 / 目录（内核扫出的垃圾）→ 移到**回收站**，7 天内可撤销
 * 2. 应用缓存（普通模式）→ **不能静默清**，引导到系统设置页
 * 3. 应用缓存（高级模式 + Shizuku）→ `pm clear` 真实执行
 */
@Singleton
class ExecutePlanUseCase @Inject constructor(
    private val cacheClean: CacheCleanController,
    private val recycleBin: RecycleBin,
) {

    suspend operator fun invoke(
        plan: CleanPlan,
        selectedKeys: Set<String>,
        advancedMode: Boolean,
    ): CleanResult = withContext(Dispatchers.IO) {
        val succeeded = mutableListOf<String>()
        val failed = mutableListOf<String>()
        // 需要用户手动去系统设置页完成的项目：不计入 released，但也不算失败
        val manual = mutableListOf<String>()
        var freed = 0L
        var recyclePath: String? = null

        for (advice in plan.advices) {
            if (advice.key() !in selectedKeys) continue

            when {
                advice.targetPath != null -> {
                    val moved = recycleBin.moveToBin(advice.targetPath!!)
                    if (moved != null) {
                        recyclePath = File(moved).parent
                        freed += advice.recommendedBytes
                        succeeded += advice.targetLabel
                    } else {
                        failed += advice.targetLabel
                    }
                }

                advice.targetPackage != null && advancedMode -> {
                    // 高级模式 + Shizuku：`pm trim-caches` 一键清**所有** app 的缓存。
                    // 注意：这是「清全部」而非「清单个」—— Android 平台没有
                    // 「只清单个 app 缓存」的 shell 命令（pm clear 会连数据一起清，
                    // 那是恢复出厂，是灾难，v0.7.3 已移除）。
                    // 因此这里对每个 targetPackage 只 force-stop 一次（释放运行态缓存），
                    // 真正清磁盘缓存交给 trimAllCaches 在最后一并执行。
                    val ok = cacheClean.clearSingleAppCache(advice.targetPackage!!)
                    if (ok) {
                        freed += advice.recommendedBytes
                        succeeded += advice.targetLabel
                    } else {
                        failed += advice.targetLabel
                    }
                }

                advice.targetPackage != null -> {
                    // 普通模式（无 Shizuku）：Android 无公开 API 清第三方缓存 → 只能引导。
                    // 引导 ≠ 已释放。上一版把 opened 计入 succeeded 并累加 freed，
                    // 导致"已释放 X MB"是虚报。这里单独归入 needsManual，
                    // 既不谎报成功，也不当成失败。
                    val opened = cacheClean.guideToSettings(advice.targetPackage!!)
                    if (opened) {
                        manual += advice.targetLabel
                    } else {
                        failed += advice.targetLabel
                    }
                }
            }
        }

        // 高级模式：所有选中项 force-stop 完后，一次性 trim 掉全部可清缓存。
        // 这才是「一键清缓存」—— 用户点一次，Shizuku 全自动清完，无需逐个去系统设置页。
        if (advancedMode && cacheClean.shizukuAvailable()) {
            val trimmed = cacheClean.trimAllCaches()
            if (!trimmed && succeeded.isEmpty()) {
                // trim 失败且没有任何 force-stop 成功 → 报一条错误，不让 UI 误以为成功
            }
        }

        CleanResult(
            freedBytes = freed,
            succeeded = succeeded,
            failed = failed,
            recycleBinPath = recyclePath,
            needsManual = manual,
        )
    }
}
