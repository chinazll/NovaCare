package com.novacare.core.model

/**
 * 存储领域模型
 *
 * 全部字段来自真实数据源：
 * - 容量/已用：StatFs
 * - 分类/大文件：Rust 引擎扫描
 * - 应用占用：StorageStatsManager（API 26+）
 * **不存在任何估算或硬编码比例**（上一版曾编造 35%/25%/12%/8%/20%）。
 */

/** 单个文件/目录节点 */
data class FileNode(
    val path: String,
    val bytes: Long,
    val isDir: Boolean,
    val modifiedEpochMs: Long? = null,
    val children: List<FileNode> = emptyList(),
)

/** 存储分类（真实扫描结果，可能为空 —— 引擎不可用时如实为空） */
data class StorageCategory(
    val name: String,
    val bytes: Long,
    /** 占总扫描量的比例 0.0~1.0；由引擎按实际字节数计算 */
    val ratio: Double,
)

/**
 * F2 语义标签：端侧 AI 对文件/目录的理解结果。
 * L1 确定性层无法得出，只有 L2（端侧语义）能产出。
 */
enum class SemanticFileKind {
    SCREENSHOT,   // 截图
    MEME,         // 表情包/贴图
    DOCUMENT,     // 证件 / 发票 / 合同
    OLD_DOCUMENT, // 旧文档
    INSTALLER,    // 安装包（.apk，装完即无用）
    PHOTO_DUPLICATE, // 重复照片
    PHOTO_BLURRY,    // 模糊照片
    VIDEO_CACHE,
    UNKNOWN,
}

/** 带语义标注的文件组（F2 输出） */
data class SemanticFileGroup(
    val kind: SemanticFileKind,
    val displayName: String,
    val files: List<FileNode>,
    val bytes: Long,
    /** AI 给出的分组理由（可验证，非黑箱） */
    val reason: String,
    /** 置信度 0.0~1.0 */
    val confidence: Float,
)

data class StorageSnapshot(
    val totalBytes: Long,
    val usedBytes: Long,
    val categories: List<StorageCategory>,
    val largestFiles: List<FileNode>,
    val semanticGroups: List<SemanticFileGroup> = emptyList(),
    /** 扫描耗时（ms），用于验证「≤3 秒」的体验指标 */
    val scanDurationMs: Long = 0,
    /** 数据是否来自 Rust 引擎（false = 降级，UI 需如实说明） */
    val fromEngine: Boolean = true,
)
