package com.novacare.core.ai

import com.novacare.core.model.AiTier
import com.novacare.core.model.FileClassification
import com.novacare.core.model.FileClassificationRequest
import com.novacare.core.model.SemanticCapability
import com.novacare.core.model.SemanticTask

/**
 * L2 —— 端侧语义分析器（F2 / F4）
 *
 * 硬约束（蓝图 §4.5.4）：
 * - 只做**分类标注**，不做删除决策
 * - 文件**不离开设备**（只接收路径 / 扩展名 / 体积等元信息）
 *
 * 现实约束：厂商端侧模型（蓝心 / MiMo / 盘古 / Gauss）均不向第三方开放，
 * 因此 L2 的落地路径只有两条：Gemini Nano（全球）与自集成 MiniCPM（中国）。
 * 在本仓库尚未集成这两个运行时之前，提供 [RuleBasedSemanticAnalyzer]：
 * 它基于路径 / 扩展名的确定性规则，**明确标注自己是 DETERMINISTIC 层**，
 * 绝不冒充端侧大模型 —— 这是「诚实」红线。
 */
interface SemanticAnalyzer {

    fun capability(): SemanticCapability

    suspend fun classifyFiles(requests: List<FileClassificationRequest>): List<FileClassification>
}

/** 尚未接入端侧模型时的实现：确定性规则，如实标注来源 */
class RuleBasedSemanticAnalyzer : SemanticAnalyzer {

    override fun capability(): SemanticCapability = SemanticCapability(
        tier = AiTier.DETERMINISTIC,
        engineName = "规则引擎（非端侧大模型）",
        isAvailable = true,
        unavailableReason = "尚未集成 Gemini Nano / MiniCPM 运行时",
        supportedTasks = setOf(SemanticTask.FILE_CLASSIFICATION),
    )

    override suspend fun classifyFiles(
        requests: List<FileClassificationRequest>,
    ): List<FileClassification> = requests.map { req ->
        val lower = req.path.lowercase()
        val ext = req.extension.lowercase()
        val (kind, reason) = when {
            lower.contains("screenshot") || lower.contains("截图") ->
                com.novacare.core.model.SemanticFileKind.SCREENSHOT to "路径含截图标识"

            lower.contains("sticker") || lower.contains("emoji") || lower.contains("表情") ->
                com.novacare.core.model.SemanticFileKind.MEME to "路径含表情包标识"

            ext == "apk" ->
                com.novacare.core.model.SemanticFileKind.INSTALLER to "安装包（.apk）"

            ext in setOf("jpg", "jpeg", "png", "webp", "heic") && req.bytes > 3 * 1024 * 1024 ->
                com.novacare.core.model.SemanticFileKind.PHOTO_DUPLICATE to "大体积图片，待进一步查重"

            ext in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx") ->
                com.novacare.core.model.SemanticFileKind.DOCUMENT to "文档类扩展名"

            ext in setOf("mp4", "mkv", "mov", "avi") ->
                com.novacare.core.model.SemanticFileKind.VIDEO_CACHE to "视频文件"

            else -> com.novacare.core.model.SemanticFileKind.UNKNOWN to "无确定性特征"
        }
        FileClassification(
            path = req.path,
            kind = kind,
            confidence = if (kind == com.novacare.core.model.SemanticFileKind.UNKNOWN) 0.3f else 0.8f,
            reason = reason,
        )
    }
}

/** 完全不可用时的空实现（保证调用方无需判空） */
class NoopSemanticAnalyzer(private val reason: String) : SemanticAnalyzer {
    override fun capability(): SemanticCapability = SemanticCapability(
        tier = AiTier.DETERMINISTIC,
        engineName = "未启用",
        isAvailable = false,
        unavailableReason = reason,
    )

    override suspend fun classifyFiles(
        requests: List<FileClassificationRequest>,
    ): List<FileClassification> = emptyList()
}
