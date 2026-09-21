package com.novacare.core.model

/** 设备健康的四个维度（权重与代码严格一致） */
enum class HealthDimension(val weight: Float, val displayName: String) {
    STORAGE(0.35f, "存储"),
    MEMORY(0.30f, "内存"),
    BATTERY(0.20f, "电池"),
    APP(0.15f, "应用防护"),
}

data class DimensionScore(
    val dimension: HealthDimension,
    val score: Int,
    /** 该维度结论的一句话说明 */
    val summary: String,
)

data class HealthScore(
    val total: Int,
    val dimensions: List<DimensionScore>,
    val verdict: String,
) {
    companion object {
        fun verdictOf(total: Int): String = when {
            total >= 85 -> "状态良好"
            total >= 60 -> "需要关注"
            else -> "建议立即优化"
        }
    }
}
