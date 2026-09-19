package com.novacare.optimizer.core

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit

/**
 * 定时维护（借鉴 SD Maid Scheduler + AAO 夜间维护思路）：
 * 每天凌晨 3 点自动 fstrim + 清理安全垃圾，用户零感知
 */
@HiltWorker
class NightlyMaintenanceWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repo: DeviceRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val junk = repo.scanJunk()
            repo.cleanJunk(junk.filter { it.isSafe })
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val NAME = "novacare_nightly_maintenance"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<NightlyMaintenanceWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delayTo3Am(), TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .setRequiresDeviceIdle(true)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME, ExistingPeriodicWorkPolicy.KEEP, request,
            )
        }

        private fun delayTo3Am(): Long {
            val now = System.currentTimeMillis()
            val cal = java.util.Calendar.getInstance().apply {
                add(java.util.Calendar.DAY_OF_YEAR, 1)
                set(java.util.Calendar.HOUR_OF_DAY, 3)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
            }
            return cal.timeInMillis - now
        }
    }
}
