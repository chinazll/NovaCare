package com.novacare.core.automation

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 自动化调度器：把规则落到 WorkManager 周期任务
 */
@Singleton
class AutomationScheduler @Inject constructor(
    private val workManager: WorkManager,
) {
    fun ensureScheduled() {
        val request = PeriodicWorkRequestBuilder<AutomationWorker>(6, TimeUnit.HOURS)
            .build()
        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancelAll() = workManager.cancelUniqueWork(WORK_NAME)

    private companion object {
        const val WORK_NAME = "novacare_automation"
    }
}
