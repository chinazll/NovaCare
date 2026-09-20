package com.novacare.app.di

import android.content.Context
import androidx.work.WorkManager
import com.novacare.app.WorkManagerHolder
import com.novacare.core.automation.AutomationScheduler
import com.novacare.core.automation.AutomationStatusProvider
import com.novacare.core.automation.RuleEngine
import com.novacare.core.data.SettingsRepository
import com.novacare.core.domain.BuildOptimizePlanUseCase
import com.novacare.core.domain.DeviceSnapshotCache
import com.novacare.core.domain.ExecutePlanUseCase
import com.novacare.core.domain.ScanDeviceUseCase
import com.novacare.core.system.DeviceStatusSource
import com.novacare.core.system.ShizukuShell
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** 自动化动作的真实实现（automation 模块只依赖抽象，便于单测） */
@Singleton
class AutomationActionsImpl @Inject constructor(
    private val scan: ScanDeviceUseCase,
    private val buildPlan: BuildOptimizePlanUseCase,
    private val execute: ExecutePlanUseCase,
    private val settings: SettingsRepository,
    private val shell: ShizukuShell,
    private val cache: DeviceSnapshotCache,
) : com.novacare.core.automation.AutomationActions {

    override suspend fun perform(
        action: com.novacare.core.model.ActionType,
        onlySafe: Boolean,
    ): com.novacare.core.automation.AutomationActions.Result {
        return when (action) {
            com.novacare.core.model.ActionType.CLEAN_JUNK,
            com.novacare.core.model.ActionType.CLEAN_CACHE -> {
                val root = android.os.Environment.getExternalStorageDirectory()?.absolutePath
                    ?: return com.novacare.core.automation.AutomationActions.Result(
                        0L, "无法访问存储", false,
                    )
                val snapshot = cache.last ?: scan(root).also { cache.put(it) }
                val plan = buildPlan(snapshot, 0L, !onlySafe)
                val advanced = settings.settings.first().advancedMode
                val result = execute(plan, plan.defaultSelected, advanced)
                com.novacare.core.automation.AutomationActions.Result(
                    freedBytes = result.freedBytes,
                    message = "清理完成：${result.succeeded.size} 项成功，${result.failed.size} 项失败",
                    success = result.failed.isEmpty(),
                )
            }

            com.novacare.core.model.ActionType.TRIM_STORAGE -> {
                // fstrim 需要 ADB 级权限；没有 Shizuku 就如实返回失败，不假装成功
                val ok = shell.run("sm fstrim")
                com.novacare.core.automation.AutomationActions.Result(
                    freedBytes = 0L,
                    message = if (ok) "已执行闪存整理" else "未执行闪存整理（需要 Shizuku 授权）",
                    success = ok,
                )
            }

            com.novacare.core.model.ActionType.NOTIFY_SUMMARY ->
                com.novacare.core.automation.AutomationActions.Result(
                    0L, "已生成摘要", true,
                )
        }
    }
}

/** 规则执行时的设备状态来源 */
@Singleton
class DeviceStatusBridge @Inject constructor(
    private val device: DeviceStatusSource,
) : AutomationStatusProvider {

    override fun currentContext(): RuleEngine.RuleContext {
        val storage = device.storage()
        val battery = device.battery()
        val usedPercent = if (storage.totalBytes > 0L) {
            ((storage.totalBytes - storage.availableBytes) * 100 / storage.totalBytes).toInt()
        } else {
            null
        }
        return RuleEngine.RuleContext(
            storageUsedPercent = usedPercent,
            batteryPercent = battery.levelPercent,
            isIdle = device.isIdle(),
            reclaimableBytes = 0L,
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindingsModule {

    @Binds
    @Singleton
    abstract fun bindAutomationActions(
        impl: AutomationActionsImpl,
    ): com.novacare.core.automation.AutomationActions

    @Binds
    @Singleton
    abstract fun bindAutomationStatus(impl: DeviceStatusBridge): AutomationStatusProvider
}

@Module
@InstallIn(SingletonComponent::class)
object AppProvidesModule {

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        // 此处通过 Application 提供的 Configuration.Provider 拿单例。
        // 由 NovaCareApp.workManagerConfiguration 在第一次调用时安全注册
        // HiltWorkerFactory（走 EntryPoint，规避 lateinit 时序问题）。
        WorkManager.getInstance(context)

    @Provides
    @Singleton
    fun provideAutomationScheduler(workManager: WorkManager): AutomationScheduler =
        AutomationScheduler(workManager)
}
