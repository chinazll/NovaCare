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

    @Test
    fun disabled_rule_never_runs() {
        assertThat(RuleEngine.shouldRun(rule(enabled = false), idleContext, 0L)).isFalse()
    }

    @Test
    fun scheduled_rule_runs_when_workmanager_fires() {
        assertThat(RuleEngine.shouldRun(rule(), idleContext, 0L)).isTrue()
    }

    @Test
    fun storage_threshold_trigger() {
        val r = rule(trigger = Trigger(TriggerType.STORAGE_ABOVE, thresholdPercent = 85))
        assertThat(RuleEngine.shouldRun(r, idleContext, 0L)).isTrue()
        assertThat(
            RuleEngine.shouldRun(r, idleContext.copy(storageUsedPercent = 40), 0L),
        ).isFalse()
    }

    @Test
    fun battery_threshold_trigger() {
        val r = rule(trigger = Trigger(TriggerType.BATTERY_BELOW, thresholdPercent = 20))
        assertThat(RuleEngine.shouldRun(r, idleContext.copy(batteryPercent = 12), 0L)).isTrue()
        assertThat(RuleEngine.shouldRun(r, idleContext.copy(batteryPercent = 90), 0L)).isFalse()
    }

    @Test
    fun min_reclaimable_condition_blocks_small_gains() {
        val r = rule(
            conditions = listOf(Condition(ConditionType.MIN_RECLAIMABLE_MB, 500)),
        )
        assertThat(RuleEngine.shouldRun(r, idleContext, 0L)).isTrue()
        assertThat(
            RuleEngine.shouldRun(
                r, idleContext.copy(reclaimableBytes = 10L * 1024 * 1024), 0L,
            ),
        ).isFalse()
    }

    @Test
    fun idle_condition_respected() {
        val r = rule(
            trigger = Trigger(TriggerType.DEVICE_IDLE),
            conditions = listOf(Condition(ConditionType.DEVICE_IDLE)),
        )
        assertThat(RuleEngine.shouldRun(r, idleContext, 0L)).isTrue()
        assertThat(RuleEngine.shouldRun(r, idleContext.copy(isIdle = false), 0L)).isFalse()
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
