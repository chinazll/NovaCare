package com.novacare.core.model

/** 垃圾类型（由 Rust 引擎判定） */
enum class JunkKind {
    CACHE,       // 缓存
    THUMBNAIL,   // 缩略图
    LOG,         // 日志
    TMP,         // 临时文件
    RESIDUAL,    // 残留目录（已卸载应用遗留）
    DUPLICATE,   // 重复文件（BLAKE3 精确哈希）
    EMPTY_DIR,   // 空目录
    ;
    val isSafeByDefault: Boolean
        get() = this == CACHE || this == THUMBNAIL || this == TMP
}

/** 清理风险分级（借鉴 Canta 的 Safe/Caution/Risky） */
enum class CleanRisk {
    SAFE,     // 删了无感知（缓存/缩略图/临时文件）
    CAUTION,  // 需确认（日志/残留目录/重复文件——完全相同副本，删一个不影响另一个）
    RISKY,    // 可能造成损失（应用数据目录）
}

data class JunkItem(
    val path: String,
    val label: String,
    val bytes: Long,
    val kind: JunkKind,
    val risk: CleanRisk,
    /** 风险说明（为空表示无额外说明） */
    val riskNote: String = "",
)
