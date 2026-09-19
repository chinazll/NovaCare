package com.novacare.optimizer

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.novacare.optimizer.core.NightlyMaintenanceWorker
import com.novacare.optimizer.core.RustCore
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application 入口
 *
 * 职责：
 *   1. Hilt 依赖注入根节点
 *   2. 提供 WorkManager 的 Hilt WorkerFactory（@HiltWorker 注入的前提）
 *   3. 注册夜间维护定时任务的唯一调用点
 *   4. 初始化 Rust 引擎
 *
 * 修复 P2-14：此前自定义 WorkManager 初始化写了、`AndroidManifest` 里也移除了
 * 默认的 WorkManagerInitializer，但 `NightlyMaintenanceWorker.schedule()` 从未被
 * 任何地方调用 —— 定时任务实际上从未存在。现在在这里注册。
 *
 * 注意：onCreate 中不做任何阻塞 IO；WorkManager 的 enqueue 本身是异步的。
 */
@HiltAndroidApp
class NovaCareApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Rust 引擎加载（失败时 RustCore.isAvailable 为 false，UI 优雅降级）
        Log.i(TAG, "Rust engine: ${RustCore.versionSafe()}")

        // 注册夜间维护（唯一调用点）
        runCatching {
            NightlyMaintenanceWorker.schedule(this)
        }.onFailure {
            Log.w(TAG, "Failed to schedule nightly maintenance", it)
        }
    }

    private companion object {
        const val TAG = "NovaCareApp"
    }
}
