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

    /** 调度器的周期长度（小时）。定时规则按这个窗口判断是否该在本轮命中。 */
    private const val PERIOD_HOURS = 6

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

    /** 触发条件是否满足（定时 / 空闲 / 阈值）。
     *  v0.7.2 移除 BOOT：当前调度是 6h 周期任务，没有 BOOT_COMPLETED 监听，
     *  旧 BOOT 触发器在每个周期都会"命中"，等于"每周期执行"，与文档承诺不符。 */
    fun triggerMatches(rule: AutomationRule, context: RuleContext, nowMs: Long): Boolean {
        if (!rule.enabled) return false
        return when (rule.trigger.type) {
            // 不再无条件命中：调度器是 PERIOD_HOURS 一次的周期任务，无条件 true 会让
            // 「每周日 03:00」在每个周期都执行（一天 4 次），配置的时间/星期形同虚设。
            TriggerType.SCHEDULED -> scheduledMatches(rule, nowMs)
            TriggerType.DEVICE_IDLE -> context.isIdle
            TriggerType.STORAGE_ABOVE -> {
                val threshold = rule.trigger.thresholdPercent ?: 85
                (context.storageUsedPercent ?: 0) > threshold
            }
            TriggerType.BATTERY_BELOW -> {
                val threshold = rule.trigger.thresholdPercent ?: 20
                (context.batteryPercent ?: 100) < threshold
            }
        }
    }

    /**
     * 定时规则是否落在本次周期窗口内。
     *
     * 旧实现无条件 `return true`：调度器是 6 小时一次的周期任务，于是
     * 一条「每周日 03:00 清理」的规则在**每个周期**都命中 —— 一天执行 4 次，
     * 配置里的 hourOfDay / dayOfWeek 完全没被读过。这里按周期窗口自行判断。
     */
    private fun scheduledMatches(rule: AutomationRule, nowMs: Long): Boolean {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = nowMs }
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val targetHour = (rule.trigger.hourOfDay ?: 3).coerceIn(0, 23)
        val endExclusive = targetHour + PERIOD_HOURS
        val inWindow = if (endExclusive <= 24) {
            hour in targetHour until endExclusive
        } else {
            hour >= targetHour || hour < endExclusive % 24
        }
        if (!inWindow) return false

        val day = rule.trigger.dayOfWeek ?: return true // null = 每天
        return cal.get(java.util.Calendar.DAY_OF_WEEK) == day
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
