package com.novacare.core.common

/**
 * 字节数格式化
 *
 * 纯函数、无 Android 依赖 —— 可直接单元测试。
 * 用 1024 进制（与 Android 系统设置一致），并保留 1 位小数。
 */
fun Long.formatBytes(): String {
    if (this < 0) return "-"
    if (this < 1024) return "${this} B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = this.toDouble() / 1024.0
    var idx = 0
    while (value >= 1024.0 && idx < units.lastIndex) {
        value /= 1024.0
        idx++
    }
    return if (value >= 100) {
        "%.0f %s".format(value, units[idx])
    } else {
        "%.1f %s".format(value, units[idx])
    }
}

fun Long.formatDays(): String = when {
    this <= 0 -> "今天"
    this == 1L -> "1 天前"
    this < 30 -> "$this 天前"
    this < 365 -> "${this / 30} 个月前"
    else -> "${this / 365} 年前"
}
