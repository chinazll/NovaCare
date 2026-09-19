package com.novacare.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.novacare.core.model.Action
import com.novacare.core.model.ActionType
import com.novacare.core.model.AutomationRule
import com.novacare.core.model.Condition
import com.novacare.core.model.ConditionType
import com.novacare.core.model.Trigger
import com.novacare.core.model.TriggerType

@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val enabled: Boolean,
    val triggerType: String,
    val hourOfDay: Int?,
    val dayOfWeek: Int?,
    val thresholdPercent: Int?,
    /** 条件的 JSON（[Condition] 列表） */
    val conditionsJson: String,
    /** 动作的 JSON（[Action] 列表） */
    val actionsJson: String,
    val lastRunEpochMs: Long?,
    val createdByAi: Boolean,
)

fun RuleEntity.toDomain(): AutomationRule = AutomationRule(
    id = id,
    name = name,
    enabled = enabled,
    trigger = Trigger(
        type = runCatching { TriggerType.valueOf(triggerType) }.getOrDefault(TriggerType.SCHEDULED),
        hourOfDay = hourOfDay,
        dayOfWeek = dayOfWeek,
        thresholdPercent = thresholdPercent,
    ),
    conditions = Converters.conditionsFromJson(conditionsJson),
    actions = Converters.actionsFromJson(actionsJson),
    lastRunEpochMs = lastRunEpochMs,
    createdByAi = createdByAi,
)

fun AutomationRule.toEntity(): RuleEntity = RuleEntity(
    id = id,
    name = name,
    enabled = enabled,
    triggerType = trigger.type.name,
    hourOfDay = trigger.hourOfDay,
    dayOfWeek = trigger.dayOfWeek,
    thresholdPercent = trigger.thresholdPercent,
    conditionsJson = Converters.conditionsToJson(conditions),
    actionsJson = Converters.actionsToJson(actions),
    lastRunEpochMs = lastRunEpochMs,
    createdByAi = createdByAi,
)

/** 默认规则：每周日 03:00 清理垃圾（仅安全项） */
fun defaultWeeklyRule(): AutomationRule = AutomationRule(
    id = "builtin-weekly-junk",
    name = "每周自动清理",
    enabled = false,
    trigger = Trigger(type = TriggerType.SCHEDULED, hourOfDay = 3, dayOfWeek = 1),
    conditions = listOf(Condition(type = ConditionType.ONLY_SAFE_ITEMS)),
    actions = listOf(Action(type = ActionType.CLEAN_JUNK)),
    createdByAi = false,
)
