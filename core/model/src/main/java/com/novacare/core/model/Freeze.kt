package com.novacare.core.model

/**
 * F3 —— 不常用应用识别
 *
 * 对标 Hail，但更智能：不是给 200 个应用的全列表让用户挑，
 * 而是结合「使用时长 + 最后使用 + 待机分级 + 是否预装」给出**排序 + 理由**。
 */

enum class FreezeRisk {
    /** 冻结后完全无感（预装但不用的应用） */
    SAFE,
    /** 冻结后可能影响通知/后台同步 */
    CAUTION,
    /** 系统关键组件，冻结可能导致功能异常 */
    RISKY,
}

data class FreezeCandidate(
    val app: AppInfo,
    val daysUnused: Int?,
    val standbyBucket: StandbyBucket,
    val risk: FreezeRisk,
    /** 给用户的结论，如"3 个月未打开，且后台偷跑电量" */
    val reason: String,
    /** 预估可节省的电量占比（0.0~1.0），无依据时为 null */
    val estimatedBatterySaving: Float? = null,
    val confidence: Float = 1.0f,
    val source: AiTier = AiTier.DETERMINISTIC,
)

/** 冻结执行方式（三层系统控制策略） */
enum class FreezeMethod {
    /** 官方引导：跳设置页让用户手动操作（零门槛、零 hack） */
    OFFICIAL_GUIDE,
    /** Shizuku：pm suspend（可逆，高级模式） */
    SHIZUKU_SUSPEND,
    /** 系统级休眠（Android 16+ 仅对自身有效，第三方不可用） */
    SYSTEM_HIBERNATION,
}

data class FreezeResult(
    val packageName: String,
    val success: Boolean,
    val method: FreezeMethod,
    val message: String,
)
