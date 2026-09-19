package com.novacare.app

import android.app.Application
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
class NovaCareApp : Application() {

    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var scheduler: AutomationScheduler

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
