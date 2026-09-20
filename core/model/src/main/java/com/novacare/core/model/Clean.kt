package com.novacare.core.model

/**
 * F1 —— 智能缓存清理的领域模型
 *
 * 这是「AI 决策层」的核心：不是给一份缓存列表让用户自己挑，
 * 而是给出「删哪个、删多少、代价多大」的**结论 + 理由**。
 */

/** AI 给出的处置建议 */
enum class CleanRecommendation {
    /** 可全清（如：已看过的视频缓存） */
    CLEAN_ALL,
    /** 只清一部分（如：只清 30 天前的聊天缓存） */
    CLEAN_PARTIAL,
    /** 不建议清（如：游戏资源包，删了要重下 10GB） */
    KEEP,
}

/**
 * 单条清理建议。
 *
 * 硬约束：**必须有可验证的理由**（reason），
 * 因为「信任」是核心资产 —— 用户要敢点「执行」。
 */
data class CleanAdvice(
    val targetPackage: String?,
    val targetLabel: String,
    val targetPath: String?,
    val totalBytes: Long,
    /** 建议实际清理的字节数（CLEAN_PARTIAL 时 < totalBytes） */
    val recommendedBytes: Long,
    val recommendation: CleanRecommendation,
    val risk: CleanRisk,
    /** 给用户的可执行结论（一句话） */
    val summary: String,
    /** 可验证的推理依据 —— 绝不允许为空的黑箱结论 */
    val reason: String,
    /** 不清的代价 / 清了的代价（诚实披露） */
    val costNote: String = "",
    val confidence: Float = 1.0f,
    /** 该结论由哪一层产出（L1 确定性 / L2 端侧 / L3 云端） */
    val source: AiTier,
)

/** 完整优化计划：可预览、可勾选、可撤销 */
data class CleanPlan(
    val advices: List<CleanAdvice>,
    val totalReclaimableBytes: Long,
    /** 默认勾选的（仅 SAFE 且推荐清理的） */
    val defaultSelected: Set<String>,
    val scanDurationMs: Long,
)

data class CleanResult(
    val freedBytes: Long,
    val succeeded: List<String>,
    val failed: List<String>,
    /** 回收站路径 —— 支持 7 天内撤销 */
    val recycleBinPath: String?,
    /**
     * 已引导用户去系统设置页、但本应用无法代为完成的项。
     *
     * 与 [succeeded] 严格区分：这些项**没有**释放任何空间，
     * 不能计入 [freedBytes]。上一版把它们算作成功，导致「已释放 X MB」虚报。
     */
    val needsManual: List<String> = emptyList(),
)
