package com.novacare.app

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import com.novacare.core.ai.AiProviderModule
import com.novacare.core.ai.CloudLlmConfig
import com.novacare.core.automation.AutomationScheduler
import com.novacare.core.data.SettingsRepository
import dagger.hilt.android.EntryPointAccessors
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

    /**
     * WorkManager 必须经由 HiltWorkerFactory 创建 Worker，否则 @HiltWorker 标注的
     * AutomationWorker 会因构造函数需要注入参数而无法实例化 —— 所有定时自动化
     * 任务会静默失败（用户看到界面正常，但规则永不触发）。
     *
     * 注意：为了让本配置生效，AndroidManifest 里必须用 tools:node="remove" 关闭
     * androidx.startup 对 WorkManager 的默认初始化，否则 WorkManager 会在
     * Application.onCreate 之前用默认工厂初始化完毕，本 Provider 永远不会被调用。
     *
     * ============================================================
     * 【死锁修复 / 2026-09-20】
     * 旧实现：`workManagerConfiguration` getter 里直接读 `this.workerFactory`
     * (lateinit @Inject)，看似没问题，事实上一旦有 Hilt 注入链触发
     * `WorkManager.getInstance(context)`，就会形成如下死锁：
     *
     *   1. Hilt 在实例化 `NovaCareApp` 时需要注入 `scheduler`（@Inject lateinit）
     *   2. `scheduler` 的构造依赖 `WorkManager` → 触发 `provideWorkManager()`
     *   3. `provideWorkManager()` 调 `WorkManager.getInstance(context)`
     *   4. `WorkManagerImpl` 检测到 Application 实现了 `Configuration.Provider`，
     *      调用 `app.workManagerConfiguration` getter
     *   5. getter 内部访问 `workerFactory`（还没注入） → UninitializedPropertyAccessException
     *   6. 整个应用 onCreate 失败，进程被 kill。**这是 v0.7.0 启动崩溃的根因**。
     *
     * 【正确做法】用 Hilt EntryPoint 在 getter 里**绕开 lateinit 直接去容器取**，
     * 不触发 `NovaCareApp` 自己的字段初始化（此时它正被构造）。
     * 这是 Hilt 官方文档对 Configuration.Provider 的标准建议。
     * ============================================================
     */
    override val workManagerConfiguration: Configuration
        get() {
            val factory = EntryPointAccessors.fromApplication(
                applicationContext,
                HiltWorkerFactoryEntryPoint::class.java,
            ).hiltWorkerFactory()
            return Configuration.Builder()
                .setWorkerFactory(factory)
                .build()
        }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        // WorkManager 实例只有在 Application 已经实现 Configuration.Provider 之后才能
        // 安全获取（否则 WorkManagerImpl 会用默认工厂初始化）。此时 super.onCreate
        // 已经返回，Hilt 已注入完毕，本 Provider 也已注册 —— getInstance() 安全。
        WorkManagerHolder.workManager = WorkManager.getInstance(this)

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

        // scheduler 是 @Inject lateinit，本字段在 super.onCreate() 完成后已经注入完毕，
        // 此时调用安全。注意：必须在 super.onCreate() 之后访问 @Inject 字段，
        // 否则会复现与 workManagerConfiguration 同样的死锁。
        appScope.launch {
            runCatching {
                scheduler.ensureScheduled()
            }
        }
    }
}

/**
 * WorkManager 实例持有 —— 用于在 [com.novacare.app.HiltWorkerFactoryEntryPoint] 之前
 * 应用已初始化时，把 WorkManager 实例传给其他组件（无业务直接调用方）。
 */
object WorkManagerHolder {
    @Volatile
    var workManager: WorkManager? = null
}