package com.novacare.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.novacare.core.ai.AiProviderModule
import com.novacare.core.ai.CloudLlmConfig
import com.novacare.core.automation.AutomationScheduler
import com.novacare.core.data.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class NovaCareApp : Application(), Configuration.Provider {

    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var scheduler: AutomationScheduler
    @Inject lateinit var workerFactory: HiltWorkerFactory

    /**
     * WorkManager 必须经由 HiltWorkerFactory 创建 Worker，否则 @HiltWorker 标注的
     * AutomationWorker 会因构造函数需要注入参数而无法实例化 —— 所有定时自动化
     * 任务会静默失败（用户看到界面正常，但规则永不触发）。
     *
     * 注意：为了让本配置生效，AndroidManifest 里必须用 tools:node="remove" 关闭
     * androidx.startup 对 WorkManager 的默认初始化，否则 WorkManager 会在
     * Application.onCreate 之前用默认工厂初始化完毕，本 Provider 永远不会被调用。
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        // 云端 AI 配置同步：用户在设置里改模型 / Key，这里才生效。
        // 未开启时 config 为空 → provider.isConfigured = false → 一个网络请求都不发。
        appScope.launch {
            settings.settings.collectLatest { s ->
                AiProviderModule.updateCloudConfig(
                    CloudLlmConfig(
                        baseUrl = s.cloudModel.baseUrl,
                        apiKey = if (s.cloudAiEnabled) s.cloudApiKey else "",
                        model = s.cloudModel.defaultModel,
                    ),
                )
            }
        }

        appScope.launch {
            runCatching {
                WorkManagerHolder.workManager = WorkManager.getInstance(this@NovaCareApp)
                scheduler.ensureScheduled()
            }
        }
    }
}

/** WorkManager 实例持有（Application 启动时注入） */
object WorkManagerHolder {
    @Volatile
    var workManager: WorkManager? = null
}
