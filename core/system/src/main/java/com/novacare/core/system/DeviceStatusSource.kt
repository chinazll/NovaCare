package com.novacare.core.system

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 设备状态数据源：存储 / 内存 / 电池
 *
 * 全部读系统真实值：
 * - 容量：StatFs
 * - 内存：ActivityManager.MemoryInfo
 * - 电池：ACTION_BATTERY_CHANGED sticky intent
 */
@Singleton
class DeviceStatusSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    data class StorageStatus(val totalBytes: Long, val availableBytes: Long)

    fun storage(): StorageStatus {
        val path = runCatching { Environment.getDataDirectory() }.getOrNull()
        if (path == null) return StorageStatus(0L, 0L)
        return runCatching {
            val stat = StatFs(path.path)
            val total = stat.blockCountLong * stat.blockSizeLong
            val available = stat.availableBlocksLong * stat.blockSizeLong
            StorageStatus(totalBytes = total, availableBytes = available)
        }.getOrDefault(StorageStatus(0L, 0L))
    }

    data class MemoryStatus(val totalBytes: Long, val availableBytes: Long)

    fun memory(): MemoryStatus {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return MemoryStatus(0L, 0L)
        val info = ActivityManager.MemoryInfo()
        runCatching { am.getMemoryInfo(info) }.getOrNull()
        return MemoryStatus(
            totalBytes = info.totalMem,
            availableBytes = info.availMem,
        )
    }

    data class BatteryStatus(
        val levelPercent: Int,
        val temperatureTenths: Int,
        val voltageMv: Int,
        val currentNow: Int,
        val status: String,
        val health: String,
        val plugged: Int,
    )

    fun battery(): BatteryStatus {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (intent == null) {
            return BatteryStatus(0, 0, 0, 0, "unknown", "unknown", 0)
        }
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else 0
        val plugged = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        } else {
            intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        }
        val statusCode = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val healthCode = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
        val current = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
        } else {
            0
        }

        return BatteryStatus(
            levelPercent = percent,
            temperatureTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0),
            voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0),
            currentNow = current / 1000,
            status = statusName(statusCode),
            health = healthName(healthCode),
            plugged = plugged,
        )
    }

    private fun statusName(code: Int): String = when (code) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
        BatteryManager.BATTERY_STATUS_FULL -> "full"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not_charging"
        else -> "unknown"
    }

    /**
     * 设备是否"空闲"：屏幕关闭（或系统已进入 Doze）且插着电。
     *
     * 为什么要求充电：DEVICE_IDLE 触发的自动化往往是清理 / 整理这类有磁盘与
     * CPU 开销的动作，在电池供电时后台跑会明显耗电，用户会把账算在应用头上。
     * 亮屏时绝不执行 —— 用户正在用手机，此时后台重活会卡顿。
     *
     * 这里如实读系统状态，不猜测：拿不到 PowerManager 时保守返回 false
     * （宁可不执行，也不在错误时机执行）。
     */
    fun isIdle(): Boolean {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return false
        val interactive = runCatching { power.isInteractive }.getOrDefault(true)
        val deviceIdle = runCatching { power.isDeviceIdleMode }.getOrDefault(false)
        val screenOff = !interactive || deviceIdle
        val plugged = battery().plugged != 0
        return screenOff && plugged
    }

    private fun healthName(code: Int): String = when (code) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat"
        BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over_voltage"
        BatteryManager.BATTERY_HEALTH_COLD -> "cold"
        else -> "unknown"
    }
}
