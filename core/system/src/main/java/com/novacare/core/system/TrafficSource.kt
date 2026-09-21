package com.novacare.core.system

import android.content.Context
import android.content.pm.PackageManager
import android.net.TrafficStats
import android.os.Build
import android.os.Process
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 流量数据源（Traffic）
 *
 * 双源读取，**取真实值不估算**：
 *   1. [TrafficStats] —— 自开机以来的累计字节数（rx/tx 总和、按 UID 拆分）。
 *      设备重启后归零，无法跨重启做"每日用量"；本类据此诚实标注"自设备启动"。
 *   2. [/proc/net/dev] —— 各网络接口（wlan0 / rmnet_data0 等）的 rx/tx。
 *      用于顶部"按接口"汇总视图，反映实际网络活动。
 *
 * 单位约定：所有字节数 = bytes；调用方按需换算。
 *
 * 权限说明：
 *   - `TrafficStats.getTotalRxBytes()` 等聚合接口**不需要**任何权限。
 *   - `TrafficStats.getUidRxBytes(uid)` 返回 -1 表示"系统未给本进程读这个 UID"。
 *     在 root / Shizuku 环境下系统会吐真实值；普通应用通常只能看到自己的 UID。
 *   - 因此本类**不**展示"别人的流量排行"那种假数据 —— 只有拿到非 -1 值的 UID 才进入列表。
 */
@Singleton
open class TrafficSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** 单个应用（按 UID）的流量 */
    data class UidTraffic(
        val uid: Int,
        val packageName: String,
        val label: String,
        val rxBytes: Long,
        val txBytes: Long,
    ) {
        val totalBytes: Long get() = rxBytes + txBytes
    }

    /** 单个网络接口的流量（来自 /proc/net/dev） */
    data class InterfaceTraffic(
        val name: String,
        val rxBytes: Long,
        val txBytes: Long,
    ) {
        val totalBytes: Long get() = rxBytes + txBytes
    }

    /** 总览（设备启动后累计） */
    data class Summary(
        val totalRxBytes: Long,
        val totalTxBytes: Long,
        val interfaces: List<InterfaceTraffic>,
        val topApps: List<UidTraffic>,
    ) {
        val totalBytes: Long get() = totalRxBytes + totalTxBytes
    }

    /**
     * 读取一份完整的流量汇总。
     *
     * @param topN UID 排行返回前 N 名（按 totalBytes 降序）
     */
    fun summary(topN: Int = DEFAULT_TOP_N): Summary {
        // 1) 设备级聚合（不需要权限）
        val totalRx = safe(TrafficStats::getTotalRxBytes)
        val totalTx = safe(TrafficStats::getTotalTxBytes)

        // 2) 接口级（/proc/net/dev）
        val interfaces = readProcNetDev()

        // 3) UID 维度（依赖 PACKAGE_USAGE_STATS 与 PackageManager 解析）
        val apps = collectPerUidTraffic(topN)

        return Summary(
            totalRxBytes = totalRx.coerceAtLeast(0L),
            totalTxBytes = totalTx.coerceAtLeast(0L),
            interfaces = interfaces,
            topApps = apps,
        )
    }

    /**
     * 列出当前已安装包 + 它们的 UID 流量。
     *
     * 不假装"全设备流量排行"——只统计本机可解析的 UID，且 TrafficStats
     * 对返回 -1 的 UID 一律丢弃（系统拒绝提供给本进程）。
     */
    private fun collectPerUidTraffic(topN: Int): List<UidTraffic> {
        val pm = context.packageManager
        val out = ArrayList<UidTraffic>()
        val packages = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(0)
            }
        }.getOrDefault(emptyList())

        for (info in packages) {
            val pkg = info.packageName ?: continue
            val appInfo = info.applicationInfo ?: continue
            val uid = appInfo.uid
            val rx = safe { TrafficStats.getUidRxBytes(uid) }
            val tx = safe { TrafficStats.getUidTxBytes(uid) }
            // 系统对未授权的调用方会返回 -1（"未支持 / 被忽略"）。
            // 至少有一项 >0 才视为有效记录，避免把"完全无流量"的 App 排进列表造成误导。
            if (rx <= 0L && tx <= 0L) continue
            val label = runCatching {
                pm.getApplicationLabel(appInfo).toString()
            }.getOrDefault(pkg)
            out += UidTraffic(
                uid = uid,
                packageName = pkg,
                label = label,
                rxBytes = rx.coerceAtLeast(0L),
                txBytes = tx.coerceAtLeast(0L),
            )
        }

        return out
            .sortedByDescending { it.totalBytes }
            .take(topN)
    }

    /**
     * 解析 /proc/net/dev。
     *
     * 格式（注意 `:` 在接口名尾部）：
     *   Inter-|   Receive                                                |  Transmit
     *    face |bytes    packets errs drop fifo frame compressed multicast|bytes    ...
     *        lo: 1234     10    0    0    0     0          0           0  1234     ...
     *     wlan0: 5678     ...
     *
     * 字段按"rx bytes / tx bytes"两列，单位 bytes。
     */
    private fun readProcNetDev(): List<InterfaceTraffic> {
        return runCatching {
            val lines = File("/proc/net/dev").bufferedReader().readLines()
            val out = ArrayList<InterfaceTraffic>(lines.size)
            for (raw in lines) {
                val line = raw.trim()
                if (!line.contains(':')) continue
                val (namePart, restPart) = line.split(':', limit = 2)
                val name = namePart.trim()
                if (name.isEmpty()) continue
                // 跳过回环与全 0 接口；只关心真实网络接口（wlan / rmnet / p2p 等）
                if (name == "lo") continue
                val cols = restPart.trim().split(Regex("\\s+"))
                if (cols.size < 9) continue
                val rxBytes = cols.getOrNull(0)?.toLongOrNull() ?: continue
                val txBytes = cols.getOrNull(8)?.toLongOrNull() ?: 0L
                if (rxBytes == 0L && txBytes == 0L) continue
                out += InterfaceTraffic(
                    name = name,
                    rxBytes = rxBytes,
                    txBytes = txBytes,
                )
            }
            out.sortedByDescending { it.totalBytes }
        }.getOrDefault(emptyList())
    }

    /** 包一层 runCatching：[TrafficStats] 在初始化失败的设备上会抛 RuntimeException。 */
    private inline fun safe(block: () -> Long): Long = runCatching(block).getOrDefault(-1L)

    /** 自家进程的实时上下行速率（不需要任何权限，便于对比展示） */
    open fun selfTraffic(): UidTraffic {
        val uid = Process.myUid()
        val rx = safe { TrafficStats.getUidRxBytes(uid) }.coerceAtLeast(0L)
        val tx = safe { TrafficStats.getUidTxBytes(uid) }.coerceAtLeast(0L)
        return UidTraffic(
            uid = uid,
            packageName = context.packageName,
            label = context.applicationInfo?.loadLabel(context.packageManager)?.toString()
                ?: context.packageName,
            rxBytes = rx,
            txBytes = tx,
        )
    }

    private companion object {
        const val DEFAULT_TOP_N: Int = 10
    }
}
