package com.novacare.core.domain

import com.novacare.core.engine.NovaEngine
import com.novacare.core.system.AppRepository
import com.novacare.core.system.DeviceStatusSource
import com.novacare.core.system.SystemPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 扫描设备（L1 确定性层）
 *
 * 硬指标：≤ 3 秒返回（蓝图 §4.5.2）。
 * 为此：L1 与 L2 并行启动，且垃圾扫描默认关闭重复文件哈希（最耗时的一项）。
 */
@Singleton
class ScanDeviceUseCase @Inject constructor(
    private val engine: NovaEngine,
    private val device: DeviceStatusSource,
    private val apps: AppRepository,
    private val permissions: SystemPermissions,
) {

    suspend operator fun invoke(
        rootPath: String,
        detectDuplicates: Boolean = false,
    ): DeviceSnapshot = withContext(Dispatchers.Default) {
        val now = System.currentTimeMillis()
        val storageStatus = device.storage()
        val memoryStatus = device.memory()
        val batteryStatus = device.battery()
        val usageGranted = permissions.hasUsageStats()

        coroutineScope {
            val appListDeferred = async { apps.loadInstalledApps(now) }
            val usageDeferred = async { apps.loadUsage(now) }

            val appList = appListDeferred.await()
            val usage = usageDeferred.await()

            // 空包列表 = 跳过残留检测（防误删在用应用数据）
            val packages = appList.joinToString(",") { it.packageName }

            val detail = engine.analyzeStorage(rootPath, storageStatus.totalBytes)
            val junk = engine.scanJunk(rootPath, packages, detectDuplicates)

            DeviceSnapshot(
                nowMs = now,
                storage = storageStatus,
                memory = memoryStatus,
                battery = batteryStatus,
                storageDetail = detail,
                junk = junk,
                apps = appList,
                usage = usage,
                installedPackages = packages,
                engineAvailable = engine.isAvailable,
                usagePermissionGranted = usageGranted,
            )
        }
    }
}
