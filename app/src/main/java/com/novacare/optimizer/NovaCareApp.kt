package com.novacare.optimizer

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * 极简 Application：仅注册 Hilt
 *
 * 设计原则（参考 SD Maid SE / Canta）：
 * - 不在 Application.onCreate 跑任何阻塞逻辑
 * - 定时维护放到 Activity 启动后由 ViewModel 调度
 * - 避免 WorkManager 在主进程启动时强初始化（Android 14+ 严格）
 */
@HiltAndroidApp
class NovaCareApp : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}