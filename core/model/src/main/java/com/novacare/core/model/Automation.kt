package com.novacare.core.model

/**
 * 可编程自动化引擎（对标 Tasker，但图形化、门槛低 10 倍）
 * 结构：触发器(Trigger) → 条件(Condition) → 动作(Action)
 */

enum class TriggerType {
    SCHEDULED,      // 定时（如每周日 3 点）
    DEVICE_IDLE,    // 设备空闲且充电
    STORAGE_ABOVE,  // 存储占用超过阈值
    BATTERY_BELOW,  // 电量低于阈值
    BOOT,           // 开机
}

data class Trigger(
    val type: TriggerType,
    /** 定时触发的小时（0-23），仅 SCHEDULED 用 */
    val hourOfDay: Int? = null,
    /** 定时触发的星期（1-7，Calendar 语义），null = 每天 */
    val dayOfWeek: Int? = null,
    /** 阈值百分比（0-100），用于 STORAGE_ABOVE / BATTERY_BELOW */
    val thresholdPercent: Int? = null,
)

enum class ConditionType {
    ONLY_SAFE_ITEMS,      // 仅清理安全项
    MIN_RECLAIMABLE_MB,   // 可回收空间大于阈值才执行
    BATTERY_NOT_LOW,
    DEVICE_IDLE,
}

data class Condition(
    val type: ConditionType,
    val intValue: Int? = null,
)

enum class ActionType {
    CLEAN_JUNK,
    CLEAN_CACHE,
    TRIM_STORAGE,
    NOTIFY_SUMMARY,
}

data class Action(
    val type: ActionType,
    val stringParam: String? = null,
)

data class AutomationRule(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val trigger: Trigger,
    val conditions: List<Condition>,
    val actions: List<Action>,
    /** 上次执行时间戳 */
    val lastRunEpochMs: Long? = null,
    /** 规则来源：用户手动创建 或 AI 自然语言生成（F5 独家） */
    val createdByAi: Boolean = false,
) {
    /** 人类可读的规则描述，如"每周日 03:00 清理垃圾" */
    fun describe(): String {
        val whenText = when (trigger.type) {
            TriggerType.SCHEDULED -> {
                val d = trigger.dayOfWeek?.let { "周" + "日一二三四五六"[it - 1] } ?: "每天"
                "%s %02d:00".format(d, trigger.hourOfDay ?: 3)
            }
            TriggerType.DEVICE_IDLE -> "设备空闲充电时"
            TriggerType.STORAGE_ABOVE -> "存储占用 > ${trigger.thresholdPercent ?: 85}%"
            TriggerType.BATTERY_BELOW -> "电量 < ${trigger.thresholdPercent ?: 20}%"
            TriggerType.BOOT -> "开机时"
        }
        val doText = actions.joinToString("、") {
            when (it.type) {
                ActionType.CLEAN_JUNK -> "清理垃圾"
                ActionType.CLEAN_CACHE -> "清理缓存"
                ActionType.TRIM_STORAGE -> "闪存整理"
                ActionType.NOTIFY_SUMMARY -> "发送摘要"
            }
        }
        return "$whenText —— $doText"
    }
}
