package com.novacare.core.model

/** 垃圾扫描结果（内核返回项的聚合） */
data class JunkReport(
    val items: List<JunkItem>,
    val totalBytes: Long,
    val safeBytes: Long,
    val durationMs: Long,
)

/** 与 Rust 侧 kind 字符串对齐的转换 */
fun junkKindFromWire(value: String): JunkKind = when (value) {
    "cache" -> JunkKind.CACHE
    "thumbnail" -> JunkKind.THUMBNAIL
    "log" -> JunkKind.LOG
    "tmp" -> JunkKind.TMP
    "crash" -> JunkKind.LOG
    "residual" -> JunkKind.RESIDUAL
    "duplicate" -> JunkKind.DUPLICATE
    else -> JunkKind.TMP
}

/** 与 Rust 侧 risk 字符串对齐的转换 */
fun cleanRiskFromWire(value: String): CleanRisk = when (value) {
    "safe" -> CleanRisk.SAFE
    "risky" -> CleanRisk.RISKY
    else -> CleanRisk.CAUTION
}
