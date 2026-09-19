package com.novacare.feature.automation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacare.core.automation.AutomationScheduler
import com.novacare.core.data.AutomationRepository
import com.novacare.core.model.Action
import com.novacare.core.model.ActionType
import com.novacare.core.model.AutomationRule
import com.novacare.core.model.Condition
import com.novacare.core.model.ConditionType
import com.novacare.core.model.Trigger
import com.novacare.core.model.TriggerType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AutomationViewModel @Inject constructor(
    private val repository: AutomationRepository,
    private val scheduler: AutomationScheduler,
) : ViewModel() {

    private val _rules = MutableStateFlow<List<AutomationRule>>(emptyList())
    val rules: StateFlow<List<AutomationRule>> = _rules.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureDefaultRule()
            repository.observeRules().collect { _rules.value = it }
        }
    }

    fun toggle(rule: AutomationRule, enabled: Boolean) {
        viewModelScope.launch {
            repository.save(rule.copy(enabled = enabled))
            if (enabled) scheduler.ensureScheduled()
        }
    }

    fun delete(rule: AutomationRule) {
        viewModelScope.launch { repository.delete(rule.id) }
    }

    /**
     * 用自然语言建规则（F5 的落地：说人话 → 规则）
     *
     * 当前实现是本地确定性解析（覆盖「每周 / 每天 / 清理垃圾 / 清缓存」等常见说法）；
     * 完全无法理解时**如实提示**，绝不生成一条猜出来的规则去乱执行。
     */
    fun createFromText(text: String) {
        val hour = Regex("(\\d+)\\s*点").find(text)?.groupValues?.get(1)?.toIntOrNull()
        val dayOfWeek = listOf("周日", "星期一", "周一", "周二", "星期三", "周三", "周四",
            "星期五", "周五", "周六", "星期六").firstOrNull { text.contains(it) }?.let { label ->
            when (label) {
                "周日", "星期日" -> 1
                "周一", "星期一" -> 2
                "周二", "星期二" -> 3
                "周三", "星期三" -> 4
                "周四", "星期四" -> 5
                "周五", "星期五" -> 6
                else -> 7
            }
        }

        val actionType = when {
            text.contains("缓存") -> ActionType.CLEAN_CACHE
            text.contains("垃圾") || text.contains("清理") -> ActionType.CLEAN_JUNK
            text.contains("整理") || text.contains("fstrim") -> ActionType.TRIM_STORAGE
            else -> null
        }

        if (actionType == null) {
            _notice.value = "没理解要做什么。试试：「每周日 3 点清理垃圾」或「每天 22 点清缓存」"
            return
        }

        val rule = AutomationRule(
            id = "rule-" + System.currentTimeMillis(),
            name = if (dayOfWeek != null) "自定义周期任务" else "自定义每日任务",
            enabled = true,
            trigger = Trigger(
                type = TriggerType.SCHEDULED,
                hourOfDay = hour ?: 3,
                dayOfWeek = dayOfWeek,
            ),
            conditions = listOf(Condition(type = ConditionType.ONLY_SAFE_ITEMS)),
            actions = listOf(Action(type = actionType)),
            createdByAi = true,
        )
        viewModelScope.launch {
            repository.save(rule)
            scheduler.ensureScheduled()
            _notice.value = "已创建规则：${rule.describe()}"
        }
    }
}
