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
                    val ok = cacheClean.clearWithShizuku(advice.targetPackage!!)
                    if (ok) {
                        freed += advice.recommendedBytes
                        succeeded += advice.targetLabel
                    } else {
                        failed += advice.targetLabel
                    }
                }

                advice.targetPackage != null -> {
                    // 普通模式：Android 无公开 API 清第三方缓存 → 只能引导
                    val opened = cacheClean.guideToSettings(advice.targetPackage!!)
                    if (opened) succeeded += advice.targetLabel else failed += advice.targetLabel
                }
            }
        }

        CleanResult(
            freedBytes = freed,
            succeeded = succeeded,
            failed = failed,
            recycleBinPath = recyclePath,
        )
    }
}
