package com.novacare.core.automation

import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AutomationModule {

    @Provides
    @Singleton
    fun provideWorkManagerProvider(): WorkManagerProvider = WorkManagerProvider()
}

/** WorkManager 需要 Context，这里用 lazy 持有，由 :app 在启动时注入实例 */
class WorkManagerProvider {
    @Volatile
    var workManager: WorkManager? = null
        private set

    fun set(manager: WorkManager) {
        workManager = manager
    }

    fun get(): WorkManager = requireNotNull(workManager) { "WorkManager not initialized" }
}
