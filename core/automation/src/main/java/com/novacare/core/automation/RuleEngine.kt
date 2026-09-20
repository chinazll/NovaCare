package com.novacare.core.automation

import com.novacare.core.model.ActionType
import com.novacare.core.model.AutomationRule
import com.novacare.core.model.ConditionType
import com.novacare.core.model.TriggerType

/**
 * 规则引擎（纯逻辑，可单元测试）
 *
 * 结构：触发器(Trigger) → 条件(Condition) → 动作(Action)
 * 对标 Tasker，但用图形化配置把门槛降到「选一选就能用」。
 */
object RuleEngine {

    /**
     * 规则执行上下文
     * @param storageUsedPercent 当前存储占用百分比；null = 未知
     * @param batteryPercent 当前电量；null = 未知
     * @param isIdle 设备是否空闲（屏幕关闭且充电中）
     * @param reclaimableBytes 当前可回收空间
     */
    data class RuleContext(
        val storageUsedPercent: Int? = null,
        val batteryPercent: Int? = null,
        val isIdle: Boolean = false,
        val reclaimableBytes: Long = 0L,
    )

    /** 触发条件是否满足（定时 / 空闲 / 阈值 / 开机） */
    fun triggerMatches(rule: AutomationRule, context: RuleContext, nowMs: Long): Boolean {
        if (!rule.enabled) return false
        return when (rule.trigger.type) {
            TriggerType.SCHEDULED -> true // 由 WorkManager 保证时间点，命中即执行
            TriggerType.DEVICE_IDLE -> context.isIdle
            TriggerType.STORAGE_ABOVE -> {
                val threshold = rule.trigger.thresholdPercent ?: 85
                (context.storageUsedPercent ?: 0) > threshold
            }
            TriggerType.BATTERY_BELOW -> {
                val threshold = rule.trigger.thresholdPercent ?: 20
                (context.batteryPercent ?: 100) < threshold
            }
            TriggerType.BOOT -> true
        }
    }

    /** 附加条件是否全部满足（全部满足才执行，避免误动作） */
    fun conditionsMatch(rule: AutomationRule, context: RuleContext): Boolean {
        if (rule.conditions.isEmpty()) return true
        return rule.conditions.all { condition ->
            when (condition.type) {
                // No-op: filtering is done at execution side (AutomationRunner passes `onlySafe` to actions.perform)
                ConditionType.ONLY_SAFE_ITEMS -> true
                ConditionType.MIN_RECLAIMABLE_MB -> {
                    val minMb = condition.intValue ?: 200
                    context.reclaimableBytes >= minMb * 1024L * 1024L
                }
                ConditionType.BATTERY_NOT_LOW -> (context.batteryPercent ?: 100) >= 20
                ConditionType.DEVICE_IDLE -> context.isIdle
            }
        }
    }

    fun shouldRun(rule: AutomationRule, context: RuleContext, nowMs: Long): Boolean =
        triggerMatches(rule, context, nowMs) && conditionsMatch(rule, context)

    /** 是否只清安全项（默认只清安全项，这是「绝不误删」的底线） */
    fun onlySafeItems(rule: AutomationRule): Boolean =
        rule.conditions.any { it.type == ConditionType.ONLY_SAFE_ITEMS } ||
            rule.conditions.isEmpty()

    fun actionsOf(rule: AutomationRule): Set<ActionType> = rule.actions.map { it.type }.toSet()
}
