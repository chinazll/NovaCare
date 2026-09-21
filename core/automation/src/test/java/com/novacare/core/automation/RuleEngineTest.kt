package com.novacare.core.automation

import com.google.common.truth.Truth.assertThat
import com.novacare.core.model.Action
import com.novacare.core.model.ActionType
import com.novacare.core.model.AutomationRule
import com.novacare.core.model.Condition
import com.novacare.core.model.ConditionType
import com.novacare.core.model.Trigger
import com.novacare.core.model.TriggerType
import org.junit.Test

class RuleEngineTest {

    private fun rule(
        enabled: Boolean = true,
        trigger: Trigger = Trigger(TriggerType.SCHEDULED, hourOfDay = 3),
        conditions: List<Condition> = emptyList(),
        actions: List<Action> = listOf(Action(ActionType.CLEAN_JUNK)),
    ) = AutomationRule(
        id = "r1",
        name = "test",
        enabled = enabled,
        trigger = trigger,
        conditions = conditions,
        actions = actions,
    )

    private val idleContext = RuleEngine.RuleContext(
        storageUsedPercent = 92,
        batteryPercent = 80,
        isIdle = true,
        reclaimableBytes = 900L * 1024 * 1024,
    )

    /**
     * 确定落在定时窗口内的时刻。默认 trigger 的 hourOfDay 为 null（按 3 点算），
     * 周期 6h，窗口即 [03:00, 09:00)，这里取 05:00。
     * 不用 0L 之类的裸时间戳 —— 那会依赖默认时区，导致不同机器结果不一致。
     */
    private val nowInWindow: Long = java.util.Calendar.getInstance().apply {
        set(2026, java.util.Calendar.JANUARY, 15, 5, 0, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** 明确落在窗口外的时刻（12:00），用于验证定时规则不会每个周期都命中。 */
    private val nowOutOfWindow: Long = java.util.Calendar.getInstance().apply {
        set(2026, java.util.Calendar.JANUARY, 15, 12, 0, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun disabled_rule_never_runs() {
        assertThat(RuleEngine.shouldRun(rule(enabled = false), idleContext, nowInWindow)).isFalse()
    }

    @Test
    fun scheduled_rule_runs_when_workmanager_fires() {
        assertThat(RuleEngine.shouldRun(rule(), idleContext, nowInWindow)).isTrue()
    }

    @Test
    fun storage_threshold_trigger() {
        val r = rule(trigger = Trigger(TriggerType.STORAGE_ABOVE, thresholdPercent = 85))
        assertThat(RuleEngine.shouldRun(r, idleContext, nowInWindow)).isTrue()
        assertThat(
            RuleEngine.shouldRun(r, idleContext.copy(storageUsedPercent = 40), nowInWindow),
        ).isFalse()
    }

    @Test
    fun battery_threshold_trigger() {
        val r = rule(trigger = Trigger(TriggerType.BATTERY_BELOW, thresholdPercent = 20))
        assertThat(RuleEngine.shouldRun(r, idleContext.copy(batteryPercent = 12), nowInWindow)).isTrue()
        assertThat(RuleEngine.shouldRun(r, idleContext.copy(batteryPercent = 90), nowInWindow)).isFalse()
    }

    @Test
    fun min_reclaimable_condition_blocks_small_gains() {
        val r = rule(
            conditions = listOf(Condition(ConditionType.MIN_RECLAIMABLE_MB, 500)),
        )
        assertThat(RuleEngine.shouldRun(r, idleContext, nowInWindow)).isTrue()
        assertThat(
            RuleEngine.shouldRun(
                r, idleContext.copy(reclaimableBytes = 10L * 1024 * 1024), nowInWindow,
            ),
        ).isFalse()
    }

    @Test
    fun idle_condition_respected() {
        val r = rule(
            trigger = Trigger(TriggerType.DEVICE_IDLE),
            conditions = listOf(Condition(ConditionType.DEVICE_IDLE)),
        )
        assertThat(RuleEngine.shouldRun(r, idleContext, nowInWindow)).isTrue()
        assertThat(RuleEngine.shouldRun(r, idleContext.copy(isIdle = false), nowInWindow)).isFalse()
    }

    // 回归保护：调度器是 6h 一次，定时规则曾无条件命中（一天跑 4 次），
    // 配置的时间/星期形同虚设。窗口外必须不执行。
    @Test
    fun scheduled_rule_does_not_run_outside_its_window() {
        assertThat(RuleEngine.shouldRun(rule(), idleContext, nowOutOfWindow)).isFalse()
    }

    @Test
    fun scheduled_rule_respects_configured_hour() {
        val r = rule(trigger = Trigger(TriggerType.SCHEDULED, hourOfDay = 20))
        // 05:00 不在 [20:00, 02:00) 窗口内
        assertThat(RuleEngine.shouldRun(r, idleContext, nowInWindow)).isFalse()
    }

    @Test
    fun only_safe_items_is_default() {
        assertThat(RuleEngine.onlySafeItems(rule())).isTrue()
        assertThat(
            RuleEngine.onlySafeItems(
                rule(conditions = listOf(Condition(ConditionType.BATTERY_NOT_LOW))),
            ),
        ).isFalse()
    }

    @Test
    fun rule_description_is_human_readable() {
        val r = AutomationRule(
            id = "r",
            name = "每周清理",
            enabled = true,
            trigger = Trigger(TriggerType.SCHEDULED, hourOfDay = 3, dayOfWeek = 1),
            conditions = emptyList(),
            actions = listOf(Action(ActionType.CLEAN_JUNK)),
        )
        assertThat(r.describe()).contains("周日")
        assertThat(r.describe()).contains("03:00")
    }
}
