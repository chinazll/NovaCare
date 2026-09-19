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
        val context = status.currentContext()
        val summaries = runner.runDueRules(context, System.currentTimeMillis())
        return if (summaries.isEmpty()) Result.success() else Result.success()
    }
}

/** 由 :app 提供真实设备状态（automation 模块不直接依赖系统 API，便于测试） */
interface AutomationStatusProvider {
    fun currentContext(): RuleEngine.RuleContext
}
