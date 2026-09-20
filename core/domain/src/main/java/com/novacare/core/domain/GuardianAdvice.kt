package com.novacare.core.domain

/**
 * 守护建议的公共模型（存储 / 内存 / 电池三个模块共用）
 *
 * 【为什么每条建议都必须带 why】
 *   市面上一堆"手机管家"的建议是黑箱的：只告诉你"建议关闭 XX"，不说凭什么。
 *   用户一旦照做却没效果，信任就归零。本项目的分界线就是：
 *   **建议 = 结论 + 依据 + 可执行动作**，三者缺一不算建议。
 *
 * 【为什么动作是枚举而不是直接给 Intent】
 *   Intent 属于 Android 框架，领域层不持有它（否则这个模块就没法单测了）。
 *   由 UI 层把 [GuardianAction] 翻译成具体的系统设置页。
 */
data class GuardianAdvice(
    /** 结论 */
    val title: String,
    /** 依据：为什么给出这条建议（必须可读、可验证） */
    val why: String,
    val action: GuardianAction = GuardianAction.NONE,
    /** 按钮文案；action == NONE 时为 null */
    val actionLabel: String? = null,
    /** action 需要作用到的包名（如 APP_DETAILS） */
    val targetPackage: String? = null,
    /** 这条建议的严重程度，决定 UI 配色 */
    val tone: Tone = Tone.INFO,
) {
    enum class Tone { INFO, CAUTION, RISK }
}

/**
 * 可执行动作。
 *
 * 设计纪律：**只枚举 Android 真允许第三方 App 做的事**。
 * 做不到的一律不列为动作，只在 why 里说明"系统限制，需要你手动操作"。
 */
enum class GuardianAction {
    /** 无需跳转（纯告知） */
    NONE,

    /** 系统「省电模式」设置页 —— 第三方无权直接开关省电模式 */
    BATTERY_SAVER_SETTINGS,

    /** 某个应用的系统详情页（用户可在里面点「强行停止」/「卸载」） */
    APP_DETAILS,

    /** 使用情况访问授权页（AppOps，只能跳设置页手动开） */
    USAGE_ACCESS_SETTINGS,

    /** 所有文件访问授权页（Android 11+） */
    ALL_FILES_SETTINGS,

    /** 系统「应用管理」列表页 */
    APPLICATION_SETTINGS,
}
