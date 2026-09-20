package com.novacare.app

import androidx.hilt.work.HiltWorkerFactory
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 通过 EntryPoint 访问 Hilt SingletonComponent 中的 `HiltWorkerFactory`。
 *
 * ## 解决 v0.7.0 启动崩溃的根因（2026-09-20）
 *
 * 旧实现：`workManagerConfiguration` getter 里直接读 `this.workerFactory`
 * (lateinit @Inject)，看似没问题 —— 事实上当 Hilt 在实例化 `NovaCareApp` 的
 * 过程中需要注入 `scheduler`（@Inject lateinit），而 `scheduler` 构造又依赖
 * `WorkManager` 时，就会触发下列死锁：
 *
 *   1. Hilt 实例化 `NovaCareApp`，需要注入 `scheduler`
 *   2. `scheduler` 依赖 `WorkManager` → 触发 `provideWorkManager()`
 *   3. `provideWorkManager()` 调 `WorkManager.getInstance(context)`
 *   4. `WorkManagerImpl` 检测到 Application 实现了 `Configuration.Provider`，
 *      调用 `app.workManagerConfiguration` getter
 *   5. getter 内部访问 `workerFactory`（还没注入） → UninitializedPropertyAccessException
 *   6. 整个 Application.onCreate 失败，进程被 kill —— **用户看到的"装上打不开"**
 *
 * EntryPoint 走的是**已构建好的 Hilt SingletonComponent**，
 * 与 `NovaCareApp` 实例字段的初始化状态完全无关，天然规避这个时序问题。
 *
 * 这是 Hilt 官方文档对 Configuration.Provider 的标准建议模式。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface HiltWorkerFactoryEntryPoint {
    fun hiltWorkerFactory(): HiltWorkerFactory
}