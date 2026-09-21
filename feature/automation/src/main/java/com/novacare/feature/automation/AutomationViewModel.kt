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

/**
 * 自动化 —— 规则列表 + 表单状态机
 *
 * 【本节最重要的一条诚实说明】
 * 调度器 [AutomationScheduler] 目前把规则交给 WorkManager 的**周期性任务**
 * （最短 15 分钟一次，实际由系统在维护窗口里择机执行）。
 * 因此：
 *   - 「每周日 03:00」这类精确时刻**无法被保证**，系统只保证它最终会被跑到；
 *   - 「电量低于 20%」「存储超过 85%」这类阈值条件依赖 worker 被唤醒的那一刻
 *     去读当时的真实数值，不是实时监听。
 * 这些限制由 [ScheduleNote] 在界面上原样告诉用户，
 * 而不是画一个"每天 09:00 准点执行"的假象。
 */
@HiltViewModel
class AutomationViewModel @Inject constructor(
    private val repository: AutomationRepository,
    private val scheduler: AutomationScheduler,
) : ViewModel() {

    /** 触发器类型 —— 决定表单显示哪些字段。
 *  v0.7.2 移除 BOOT：当前 6 小时周期任务无法监听到 BOOT_COMPLETED，
 *  显示给用户是合同谎。 */
    enum class TriggerKind(val label: String, val explanation: String) {
        SCHEDULED("按时间", "由系统在 6 小时维护窗口内执行，不保证精确到分钟"),
        BATTERY_BELOW("电量低于", "读取到电量低于阈值时执行"),
        STORAGE_ABOVE("存储超过", "读取到存储占用高于阈值时执行"),
        DEVICE_IDLE("设备空闲充电时", "屏幕关闭且正在充电时执行"),
    }

    /** 表单可编辑的规则草稿（id 为 null 表示新建） */
    data class RuleDraft(
        val id: String? = null,
        val name: String = "",
        val kind: TriggerKind = TriggerKind.SCHEDULED,
        val hourOfDay: Int = 3,
        val dayOfWeek: Int? = null,
        val thresholdPercent: Int = 20,
        val actions: Set<ActionType> = setOf(ActionType.CLEAN_JUNK),
        val onlySafeItems: Boolean = true,
        val enabled: Boolean = true,
    ) {
        val validName: String
            get() = name.trim().ifEmpty { defaultName() }

        fun defaultName(): String = when (kind) {
            TriggerKind.SCHEDULED -> if (dayOfWeek == null) {
                "每天 %02d:00 自动执行".format(hourOfDay)
            } else {
                // dayOfWeek 可能来自损坏的库数据（0 / 8+），直接取下标会越界崩溃
                "每${DAY_LABELS.getOrNull(dayOfWeek - 1) ?: "周"} %02d:00 自动执行".format(hourOfDay)
            }

            TriggerKind.BATTERY_BELOW -> "电量低于 $thresholdPercent% 时执行"
            TriggerKind.STORAGE_ABOVE -> "存储超过 $thresholdPercent% 时执行"
            TriggerKind.DEVICE_IDLE -> "设备空闲充电时执行"
        }

        val canSave: Boolean
            get() = actions.isNotEmpty()
    }

    private val _rules = MutableStateFlow<List<AutomationRule>>(emptyList())
    val rules: StateFlow<List<AutomationRule>> = _rules.asStateFlow()

    /** 通知条（创建成功 / 解析失败 / 调度限制） */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    /** 非空时打开编辑表单；null 时关闭 */
    private val _editing = MutableStateFlow<RuleDraft?>(null)
    val editing: StateFlow<RuleDraft?> = _editing.asStateFlow()

    /** 待删除确认的规则 */
    private val _pendingDelete = MutableStateFlow<AutomationRule?>(null)
    val pendingDelete: StateFlow<AutomationRule?> = _pendingDelete.asStateFlow()

    private val _naturalInput = MutableStateFlow("")
    val naturalInput: StateFlow<String> = _naturalInput.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureDefaultRule()
            repository.observeRules().collect { _rules.value = it }
        }
    }

    // ---------------- 开关 / 删除 ----------------

    fun toggle(rule: AutomationRule, enabled: Boolean) {
        viewModelScope.launch {
            repository.save(rule.copy(enabled = enabled))
            if (enabled) {
                scheduler.ensureScheduled()
                _notice.value = "已启用「${rule.name}」。系统会在下一个合适的时机执行，" +
                    "不保证精确到分钟。"
            } else {
                _notice.value = "已停用「${rule.name}」"
            }
        }
    }

    fun askDelete(rule: AutomationRule) {
        _pendingDelete.value = rule
    }

    fun cancelDelete() {
        _pendingDelete.value = null
    }

    fun confirmDelete() {
        val rule = _pendingDelete.value ?: return
        viewModelScope.launch {
            repository.delete(rule.id)
            _pendingDelete.value = null
            _notice.value = "已删除「${rule.name}」"
        }
    }

    fun dismissNotice() {
        _notice.value = null
    }

    // ---------------- 表单 ----------------

    /** 新建：默认给一条「每天 03:00 清理垃圾（仅安全项）」—— 最常见的起点 */
    fun startCreate() {
        _editing.value = RuleDraft()
    }

    /** 编辑：把已有规则反向映射回草稿 */
    fun startEdit(rule: AutomationRule) {
        val kind = when (rule.trigger.type) {
            TriggerType.SCHEDULED -> TriggerKind.SCHEDULED
            TriggerType.BATTERY_BELOW -> TriggerKind.BATTERY_BELOW
            TriggerType.STORAGE_ABOVE -> TriggerKind.STORAGE_ABOVE
            TriggerType.DEVICE_IDLE -> TriggerKind.DEVICE_IDLE
        }
        _editing.value = RuleDraft(
            id = rule.id,
            name = rule.name,
            kind = kind,
            hourOfDay = rule.trigger.hourOfDay ?: 3,
            dayOfWeek = rule.trigger.dayOfWeek,
            thresholdPercent = rule.trigger.thresholdPercent ?: 20,
            actions = rule.actions.map { it.type }.toSet().ifEmpty { setOf(ActionType.CLEAN_JUNK) },
            onlySafeItems = rule.conditions.any { it.type == ConditionType.ONLY_SAFE_ITEMS } ||
                rule.conditions.isEmpty(),
            enabled = rule.enabled,
        )
    }

    fun cancelEdit() {
        _editing.value = null
    }

    fun updateDraft(transform: (RuleDraft) -> RuleDraft) {
        _editing.value = _editing.value?.let(transform)
    }

    fun saveDraft() {
        val draft = _editing.value ?: return
        if (!draft.canSave) {
            _notice.value = "至少选择一项要执行的动作"
            return
        }

        val rule = AutomationRule(
            id = draft.id ?: "rule-" + System.currentTimeMillis(),
            name = draft.validName,
            enabled = draft.enabled,
            trigger = Trigger(
                type = when (draft.kind) {
                    TriggerKind.SCHEDULED -> TriggerType.SCHEDULED
                    TriggerKind.BATTERY_BELOW -> TriggerType.BATTERY_BELOW
                    TriggerKind.STORAGE_ABOVE -> TriggerType.STORAGE_ABOVE
                    TriggerKind.DEVICE_IDLE -> TriggerType.DEVICE_IDLE
                },
                hourOfDay = if (draft.kind == TriggerKind.SCHEDULED) draft.hourOfDay else null,
                dayOfWeek = if (draft.kind == TriggerKind.SCHEDULED) draft.dayOfWeek else null,
                thresholdPercent = when (draft.kind) {
                    TriggerKind.BATTERY_BELOW, TriggerKind.STORAGE_ABOVE -> draft.thresholdPercent
                    else -> null
                },
            ),
            conditions = buildList {
                if (draft.onlySafeItems) add(Condition(type = ConditionType.ONLY_SAFE_ITEMS))
            },
            actions = draft.actions.map { Action(type = it) },
            // 编辑时保留上次执行时间，否则"上次执行"会凭空消失
            lastRunEpochMs = _rules.value.firstOrNull { it.id == draft.id }?.lastRunEpochMs,
            createdByAi = false,
        )

        viewModelScope.launch {
            repository.save(rule)
            if (rule.enabled) scheduler.ensureScheduled()
            _editing.value = null
            _notice.value = if (draft.id == null) {
                "已创建「${rule.name}」"
            } else {
                "已保存「${rule.name}」"
            }
        }
    }

    // ---------------- 自然语言建规则 ----------------

    fun setNaturalInput(text: String) {
        _naturalInput.value = text
    }

    /**
     * 用自然语言建规则。
     *
     * 本地确定性解析，只覆盖「每天/每周 X + N 点 + 清理垃圾/缓存/整理」这类说法。
     * 无法理解时**如实拒绝并给出可用的说法**，绝不猜一条规则去乱执行 ——
     * 自动化规则是会无人值守跑起来的，猜错的代价比助手答错大得多。
     */
    fun createFromText() {
        val text = _naturalInput.value.trim()
        if (text.isEmpty()) {
            _notice.value = "先输入一句话，例如「每周日 3 点清理垃圾」"
            return
        }

        val hour = Regex("(\\d{1,2})\\s*[点时:]").find(text)
            ?.groupValues?.get(1)?.toIntOrNull()
            ?.takeIf { it in 0..23 }

        // 解析分钟：「22:30」/「9 点 30 分」/「22 点 30」等。
        // 仅做"我们识别出来了"的反馈，调度粒度仍由 WorkManager 的 6h 周期决定，
        // 不能精确到分钟——所以 minute 当前只用于提示用户，不写库。
        val minute = Regex("(\\d{1,2})\\s*[:点]\\s*(\\d{1,2})").find(text)
            ?.let { m ->
                val h = m.groupValues[1].toIntOrNull()
                val mi = m.groupValues[2].toIntOrNull()
                if (h != null && mi != null && mi in 0..59 && h in 0..23) mi else null
            } ?: Regex("[点时:]\\s*(\\d{1,2})\\s*分").find(text)
                ?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 0..59 }

        val dayOfWeek = DAY_KEYWORDS.entries.firstOrNull { text.contains(it.key) }?.value

        val actionType = when {
            text.contains("缓存") -> ActionType.CLEAN_CACHE
            text.contains("垃圾") || text.contains("残留") || text.contains("清理") ->
                ActionType.CLEAN_JUNK
            text.contains("整理") || text.contains("trim") -> ActionType.TRIM_STORAGE
            else -> null
        }

        val isScheduled = hour != null || text.contains("每天") ||
            text.contains("每周") || dayOfWeek != null

        if (actionType == null) {
            _notice.value = "没理解要做什么动作。目前只能从「清理垃圾 / 清理缓存 / 闪存整理」" +
                "三类里选。试试：「每周日 3 点清理垃圾」"
            return
        }
        if (!isScheduled) {
            _notice.value = "没识别出时间。试试加上「每天 22 点」或「每周日 3 点」"
            return
        }

        val timeHint = if (hour != null && minute != null) {
            "%02d:%02d".format(hour, minute)
        } else if (hour != null) {
            "%02d:00（分钟未识别，按整点）".format(hour)
        } else "未识别"

        val draft = RuleDraft(
            kind = TriggerKind.SCHEDULED,
            hourOfDay = hour ?: 3,
            dayOfWeek = dayOfWeek,
            actions = setOf(actionType),
            onlySafeItems = true,
            enabled = true,
        )

        viewModelScope.launch {
            val rule = AutomationRule(
                id = "rule-" + System.currentTimeMillis(),
                name = draft.defaultName(),
                enabled = true,
                trigger = Trigger(
                    type = TriggerType.SCHEDULED,
                    hourOfDay = draft.hourOfDay,
                    dayOfWeek = draft.dayOfWeek,
                ),
                conditions = listOf(Condition(type = ConditionType.ONLY_SAFE_ITEMS)),
                actions = listOf(Action(type = actionType)),
                createdByAi = true,
            )
            repository.save(rule)
            scheduler.ensureScheduled()
            _naturalInput.value = ""
            // 明确告诉用户分钟被识别但不会精确执行 —— 上一版只是默默丢分钟，
            // 用户以为"22:30"能准点跑，实际漂移 6 小时。这是合同谎。
            _notice.value = "已创建：${rule.describe()}（识别时间 $timeHint）。" +
                "系统执行粒度为 6 小时，可能在你设置时间的 ±3 小时内。"
        }
    }

    companion object {
        val DAY_LABELS = listOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")

        /** 关键词 → Calendar 语义的星期（1 = 周日） */
        private val DAY_KEYWORDS: Map<String, Int> = mapOf(
            "周日" to 1, "星期日" to 1, "周天" to 1,
            "周一" to 2, "星期一" to 2,
            "周二" to 3, "星期二" to 3,
            "周三" to 4, "星期三" to 4,
            "周四" to 5, "星期四" to 5,
            "周五" to 6, "星期五" to 6,
            "周六" to 7, "星期六" to 7,
        )

        /** 动作的人类可读名 + 说明（表单里用，避免用户对着 CLEAN_JUNK 发呆） */
        fun actionLabel(type: ActionType): String = when (type) {
            ActionType.CLEAN_JUNK -> "清理垃圾文件"
            ActionType.CLEAN_CACHE -> "清理应用缓存"
            ActionType.TRIM_STORAGE -> "闪存整理（fstrim）"
            ActionType.NOTIFY_SUMMARY -> "发送执行摘要通知"
        }

        fun actionDetail(type: ActionType): String = when (type) {
            ActionType.CLEAN_JUNK -> "移入回收站，7 天内可撤销"
            ActionType.CLEAN_CACHE -> "普通模式下只能引导你到系统设置页手动清理"
            ActionType.TRIM_STORAGE -> "需要高级模式 + Shizuku 授权，否则跳过"
            ActionType.NOTIFY_SUMMARY -> "需要通知权限"
        }
    }
}
