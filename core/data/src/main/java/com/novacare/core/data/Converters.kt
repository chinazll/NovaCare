package com.novacare.core.data

import androidx.room.TypeConverter
import com.novacare.core.model.Action
import com.novacare.core.model.ActionType
import com.novacare.core.model.Condition
import com.novacare.core.model.ConditionType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class ConditionDto(val type: String, val intValue: Int? = null)

@Serializable
private data class ActionDto(val type: String, val stringParam: String? = null)

/**
 * Room 类型转换器
 *
 * 规则的条件 / 动作是可变列表，用 kotlinx.serialization 序列化为 JSON 存储，
 * 避免为每种条件建一张表（规则引擎需要的是灵活可扩展的结构）。
 */
class Converters {

    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun conditionsToJson(value: List<Condition>): String =
        json.encodeToString(value.map { ConditionDto(it.type.name, it.intValue) })

    @TypeConverter
    fun conditionsFromJson(raw: String): List<Condition> = runCatching {
        json.decodeFromString<List<ConditionDto>>(raw).map {
            Condition(
                type = runCatching { ConditionType.valueOf(it.type) }
                    .getOrDefault(ConditionType.ONLY_SAFE_ITEMS),
                intValue = it.intValue,
            )
        }
    }.getOrDefault(emptyList())

    @TypeConverter
    fun actionsToJson(value: List<Action>): String =
        json.encodeToString(value.map { ActionDto(it.type.name, it.stringParam) })

    @TypeConverter
    fun actionsFromJson(raw: String): List<Action> = runCatching {
        json.decodeFromString<List<ActionDto>>(raw).mapNotNull {
            // 同理：未知动作类型丢弃，绝不兜底成 CLEAN_JUNK —— 否则自动化会
            // 执行一个用户从没配置过的、会真实删文件的动作。
            val type = runCatching { ActionType.valueOf(it.type) }.getOrNull()
                ?: return@mapNotNull null
            Action(type = type, stringParam = it.stringParam)
        }
    }.getOrDefault(emptyList())

    companion object {
        fun conditionsToJson(value: List<Condition>): String = Converters().conditionsToJson(value)
        fun conditionsFromJson(raw: String): List<Condition> = Converters().conditionsFromJson(raw)
        fun actionsToJson(value: List<Action>): String = Converters().actionsToJson(value)
        fun actionsFromJson(raw: String): List<Action> = Converters().actionsFromJson(raw)
    }
}
