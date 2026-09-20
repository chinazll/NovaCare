package com.novacare.core.automation

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * 定时自动化 Worker
 *
 * WorkManager 保证「应用没打开也能按时执行」，且遵守系统省电策略。
 */
@HiltWorker
class AutomationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val runner: AutomationRunner,
    private val status: AutomationStatusProvider,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val context = status.currentContext()
            val summaries = runner.runDueRules(context, System.currentTimeMillis())
            // 规则执行本身成功（无论有没有规则被命中）才返回 success；
            // 如果 summaries.isEmpty 说明没有该执行的规则，这是正常情况，不重试
            Result.success()
        } catch (e: Exception) {
            // 仅在执行异常时重试，避免同类错误反复触发
            Result.failure()
        }
    }
}

/** 由 :app 提供真实设备状态（automation 模块不直接依赖系统 API，便于测试） */
interface AutomationStatusProvider {
    fun currentContext(): RuleEngine.RuleContext
}
