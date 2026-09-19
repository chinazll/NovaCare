package com.novacare.core.model

/**
 * AI 三层能力栈的领域模型
 *
 * 原则：能确定性就不调 AI，能本地就不上云。
 * 用户无需知道 AI 在哪跑，只需点一下，系统自动选最快最省最私密的路。
 */

/** 结论由哪一层 AI 产出（决定 UI 如何标注可信度与来源） */
enum class AiTier {
    /** L1 确定性规则：Rust 引擎 + 系统 API，永远可用、免费、本地 */
    DETERMINISTIC,
    /** L2 端侧语义 AI：Gemini Nano（全球） / MiniCPM（中国自集成） */
    ON_DEVICE,
    /** L3 云端 AI：用户主动开启才联网；中国走国产已备案模型 */
    CLOUD,
}

/** 端侧语义分析器的能力状态 */
data class SemanticCapability(
    val tier: AiTier,
    val engineName: String,
    val isAvailable: Boolean,
    /** 不可用的原因（如实告知，不静默失败） */
    val unavailableReason: String? = null,
    /** 支持的语义任务 */
    val supportedTasks: Set<SemanticTask> = emptySet(),
)

enum class SemanticTask {
    FILE_CLASSIFICATION,   // F2 文件语义识别
    DUPLICATE_PHOTO,       // F4 重复/模糊照片
    INTENT_PARSING,        // F5 自然语言指令
}

/** F2 输入：待分类的文件元信息（不含文件内容本身，隐私红线） */
data class FileClassificationRequest(
    val path: String,
    val bytes: Long,
    val extension: String,
    val modifiedEpochMs: Long?,
)

/** F2 输出 */
data class FileClassification(
    val path: String,
    val kind: SemanticFileKind,
    val confidence: Float,
    val reason: String,
)

/**
 * F5 自然语言指令 → 结构化操作
 * 例："清理半年前的缓存但别动微信"
 *   intent=CLEAN_CACHE, olderThanDays=180, excludePackages=[com.tencent.mm]
 */
data class ParsedIntent(
    val intent: IntentType,
    val olderThanDays: Int? = null,
    val excludePackages: Set<String> = emptySet(),
    val includePackages: Set<String> = emptySet(),
    val minBytes: Long? = null,
    val rawText: String,
    /** 解析来源层 */
    val source: AiTier,
    val confidence: Float,
)

enum class IntentType {
    CLEAN_CACHE,
    CLEAN_JUNK,
    FREEZE_UNUSED_APPS,
    ANALYZE_STORAGE,
    UNKNOWN,
}

/** L3 云端模型（可插拔，用户可在设置中切换） */
enum class CloudModel(
    val displayName: String,
    /** OpenAI 兼容 base url */
    val baseUrl: String,
    /** 是否已完成国内备案（合规红线） */
    val isChinaCompliant: Boolean,
    val defaultModel: String,
) {
    MINIMAX(
        "MiniMax（国内首选）",
        "https://api.minimax.chat/v1",
        isChinaCompliant = true,
        defaultModel = "MiniMax-Text-01",
    ),
    ZHIPU(
        "智谱 GLM",
        "https://open.bigmodel.cn/api/paas/v4",
        isChinaCompliant = true,
        defaultModel = "glm-4-plus",
    ),
    QWEN(
        "通义千问",
        "https://dashscope.aliyuncs.com/compatible-mode/v1",
        isChinaCompliant = true,
        defaultModel = "qwen-plus",
    ),
    DEEPSEEK(
        "DeepSeek",
        "https://api.deepseek.com",
        isChinaCompliant = true,
        defaultModel = "deepseek-chat",
    ),
    GEMINI(
        "Google Gemini（全球）",
        "https://generativelanguage.googleapis.com/v1beta/openai",
        isChinaCompliant = false,
        defaultModel = "gemini-2.0-flash",
    ),
}
