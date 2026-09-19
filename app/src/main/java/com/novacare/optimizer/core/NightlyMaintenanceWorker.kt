package com.novacare.optimizer.core

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit

/**
 * 夜间自动维护（借鉴 SD Maid Scheduler + AAO 夜间维护思路）
 *
 * 每天凌晨 3 点（设备空闲且电量充足时）：
 *   1. 扫描并清理「安全」垃圾
 *   2. 尝试 fstrim（需 Shizuku，不可用时跳过并记录）
 *
 * 修复 P2-14：此前 `schedule()` 从未被任何地方调用，
 * 自定义 WorkManager 初始化也白做了 —— 这个定时任务实际上从未存在过。
 * 现在由 [com.novacare.optimizer.NovaCareApp.onCreate] 注册。
 */
@HiltWorker
class NightlyMaintenanceWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repo: DeviceRepository,
    private val maintenance: StorageMaintenance,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            // 1. 清理安全垃圾
            val junk = repo.scanJunk()
            val freed = repo.cleanJunk(junk.filter { it.isSafe })
            Log.i(TAG, "Nightly cleanup freed ${freed / 1024 / 1024} MB")

            // 2. 闪存整理（需 Shizuku；不可用时诚实跳过）
            val trimmed = maintenance.trim()
            Log.i(TAG, "Nightly fstrim executed=$trimmed")

            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Nightly maintenance failed, will retry", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "NightlyMaintenance"
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
            return (cal.timeInMillis - now).coerceAtLeast(0L)
        }
    }
}
