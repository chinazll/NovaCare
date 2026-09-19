package com.novacare.core.domain

import com.novacare.core.model.AppInfo
import com.novacare.core.model.AppUsageStats
import com.novacare.core.model.JunkReport
import com.novacare.core.model.StorageSnapshot
import com.novacare.core.system.DeviceStatusSource

/**
 * 一次扫描的完整结果（不可变，跨模块传递）
 *
 * 遵循 UDF：扫描结果一次性产生，UI 只渲染，不再回写。
 */
data class DeviceSnapshot(
    val nowMs: Long,
    val storage: DeviceStatusSource.StorageStatus,
    val memory: DeviceStatusSource.MemoryStatus,
    val battery: DeviceStatusSource.BatteryStatus,
    /** Rust 引擎给出的存储分类；null = 引擎不可用（UI 需如实说明） */
    val storageDetail: StorageSnapshot?,
    val junk: JunkReport?,
    val apps: List<AppInfo>,
    val usage: Map<String, AppUsageStats>,
    val installedPackages: String,
    val engineAvailable: Boolean,
    val usagePermissionGranted: Boolean,
)
