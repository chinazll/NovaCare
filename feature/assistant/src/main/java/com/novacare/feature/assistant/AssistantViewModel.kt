package com.novacare.feature.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacare.core.data.SettingsRepository
import com.novacare.core.domain.AssistantOutcome
import com.novacare.core.domain.AssistantUseCase
import com.novacare.core.domain.DeviceSnapshot
import com.novacare.core.domain.DeviceSnapshotCache
import com.novacare.core.domain.ExecutePlanUseCase
import com.novacare.core.domain.ScanDeviceUseCase
import com.novacare.core.domain.key
import com.novacare.core.engine.NovaEngine
import com.novacare.core.model.AiTier
import com.novacare.core.model.CleanAdvice
import com.novacare.core.model.CleanPlan
import com.novacare.core.model.CleanRisk
import com.novacare.core.model.FreezeCandidate
import com.novacare.core.model.IntentType
import com.novacare.core.model.ParsedIntent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * AI 助手 —— 对话状态机
 *
 * 【能力边界，必须对用户诚实】
 * 本应用**没有**通用聊天模型。助手能做的是把一句中文口令解析成
 * 结构化意图（CLEAN_CACHE / CLEAN_JUNK / FREEZE_UNUSED_APPS / ANALYZE_STORAGE），
 * 再由 [AssistantUseCase] 生成一份**待确认**的计划。
 *
 * 解析器有两层：
 *   - L1 本地规则（[com.novacare.core.ai.RuleBasedIntentParser]）—— 离线可用，
 *     覆盖常见说法
 *   - L3 云端模型 —— 仅在用户在设置里主动开启并填了 API Key 时才启用
 *
 * 因此 [AssistantViewModel.Capability] 会如实告诉 UI 当前是哪一层在解析。
 * 解析不出意图时返回 UNKNOWN，UI 必须显示"没听懂 + 能听懂什么"，
 * **绝不假装理解了**，也绝不猜一个动作去执行。
 */
@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val scan: ScanDeviceUseCase,
    private val cache: DeviceSnapshotCache,
    private val assistant: AssistantUseCase,
    private val executePlanUseCase: ExecutePlanUseCase,
    private val engine: NovaEngine,
    private val settings: SettingsRepository,
) : ViewModel() {

    /** 助手当前的解析能力（用于界面顶部如实标注，而不是假装成通用大模型） */
    data class Capability(
        /** 云端模型是否已配置（true = L3 在解析） */
        val cloudEnabled: Boolean,
        /** 本地内核是否可用（false = 只能给出通用建议，读不到设备真实数据） */
        val engineAvailable: Boolean,
        val engineVersion: String,
    ) {
        val tierLabel: String
            get() = if (cloudEnabled) "云端模型 · L3" else "本地规则 · L1（离线可用）"
    }

    sealed interface Bubble {
        val id: Long

        /** 用户发言 */
        data class User(override val id: Long, val text: String) : Bubble

        /** 助手回复：始终带一句文字，必要时附带可操作卡片 */
        data class Assistant(
            override val id: Long,
            val text: String,
            /** 解析出的意图（UNKNOWN 时 UI 显示"没听懂"） */
            val understood: Boolean,
            val tier: AiTier,
            val confidence: Float,
            /** 被识别的排除项（"别动微信"） */
            val excludedLabels: List<String>,
            val olderThanDays: Int?,
            val card: ReplyCard?,
        ) : Bubble
    }

    /** 助手回复里可执行的部分 —— 一律先给清单，用户确认后才执行 */
    sealed interface ReplyCard {
        /** 可清理清单 */
        data class Plan(
            val advices: List<CleanAdvice>,
            val totalBytes: Long,
            val safeBytes: Long,
        ) : ReplyCard

        /** 可冻结清单 */
        data class Freeze(val candidates: List<FreezeCandidate>) : ReplyCard
    }

    /** 执行结果（清理由助手卡片触发的一步） */
    data class ExecutionReport(
        val freedBytes: Long,
        val succeeded: Int,
        val failed: Int,
        /** 需要用户去系统设置页手动完成、本应用无法代为执行的项 */
        val needsManual: Int,
        val recycleBinPath: String?,
    )

    private val _bubbles = MutableStateFlow<List<Bubble>>(emptyList())
    val bubbles: StateFlow<List<Bubble>> = _bubbles.asStateFlow()

    private val _thinking = MutableStateFlow(false)
    val thinking: StateFlow<Boolean> = _thinking.asStateFlow()

    private val _capability = MutableStateFlow(
        Capability(cloudEnabled = false, engineAvailable = true, engineVersion = ""),
    )
    val capability: StateFlow<Capability> = _capability.asStateFlow()

    /** 非空时表示上一轮解析/扫描出错，UI 用 InlineNotice 展示并提供重试 */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _report = MutableStateFlow<ExecutionReport?>(null)
    val report: StateFlow<ExecutionReport?> = _report.asStateFlow()

    private var nextId = 1L
    private var lastText: String = ""

    init {
        refreshCapability()
    }

    /**
     * 刷新能力状态。
     * `cloudApiKey` 通过 DataStore 异步读取，因此这里放协程里；UI 在 ON_RESUME 时调用。
     */
    fun refreshCapability() {
        viewModelScope.launch {
            val s = settings.settings.first()
            _capability.value = Capability(
                cloudEnabled = s.cloudAiEnabled && s.cloudApiKey.isNotBlank(),
                engineAvailable = engine.isAvailable,
                engineVersion = engine.version(),
            )
        }
    }

    /** 发送一条消息并解析 */
    fun submit(text: String, rootPath: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _thinking.value) return

        lastText = trimmed
        _error.value = null
        _report.value = null
        _bubbles.value = _bubbles.value + Bubble.User(nextId++, trimmed)
        _thinking.value = true

        viewModelScope.launch {
            runCatching {
                val snapshot = cache.last ?: scan(rootPath).also { cache.put(it) }
                val intent = assistant.parse(trimmed, snapshot)
                val advanced = settings.settings.first().advancedMode
                val outcome = assistant.apply(intent, snapshot, advanced)
                Triple(intent, outcome, snapshot)
            }.onSuccess { (intent, outcome, snapshot) ->
                _bubbles.value = _bubbles.value + buildReply(trimmed, intent, outcome, snapshot)
                // 能力状态可能因为这一轮扫描才发现内核不可用 —— 立即同步到顶部标注
                _capability.value = _capability.value.copy(
                    engineAvailable = snapshot.engineAvailable,
                    engineVersion = engine.version(),
                )
                _thinking.value = false
            }.onFailure { e ->
                _error.value = e.message ?: "解析失败，请重试"
                _thinking.value = false
            }
        }
    }

    /** 出错后重发上一条 */
    fun retry(rootPath: String) {
        if (lastText.isBlank()) return
        val text = lastText
        _bubbles.value = _bubbles.value.dropLastWhile { it is Bubble.User && it.text == text }
        submit(text, rootPath)
    }

    fun clearError() {
        _error.value = null
    }

    fun dismissReport() {
        _report.value = null
    }

    /**
     * 执行助手给出的清理计划。
     *
     * 只执行用户已经在卡片上**看过并点击确认**的那一份清单：
     * 把清单重新装回 [CleanPlan] 并按 safety 过滤后交给 [ExecutePlanUseCase]，
     * 不会因为点了一次执行就扩大到其它项。
     */
    fun executePlan(advices: List<CleanAdvice>, rootPath: String) {
        if (advices.isEmpty()) return
        viewModelScope.launch {
            _thinking.value = true
            _error.value = null
            runCatching {
                val advanced = settings.settings.first().advancedMode
                val plan = CleanPlan(
                    advices = advices,
                    totalReclaimableBytes = advices.sumOf { it.recommendedBytes },
                    defaultSelected = advices.map { it.key() }.toSet(),
                    scanDurationMs = 0L,
                )
                executePlanUseCase(plan, plan.defaultSelected, advanced)
            }.onSuccess { result ->
                _report.value = ExecutionReport(
                    freedBytes = result.freedBytes,
                    succeeded = result.succeeded.size,
                    failed = result.failed.size,
                    needsManual = result.needsManual.size,
                    recycleBinPath = result.recycleBinPath,
                )
                // 执行后重扫，让后续回答基于真实状态而不是缓存
                runCatching { scan(rootPath) }.getOrNull()?.let { cache.put(it) }
                _thinking.value = false
            }.onFailure { e ->
                _error.value = e.message ?: "执行失败"
                _thinking.value = false
            }
        }
    }

    fun clearReport() {
        _report.value = null
    }

    private suspend fun buildReply(
        text: String,
        intent: ParsedIntent,
        outcome: AssistantOutcome,
        snapshot: DeviceSnapshot,
    ): Bubble.Assistant {
        val labels = intent.excludePackages.mapNotNull { pkg ->
            snapshot.apps.firstOrNull { it.packageName == pkg }?.label
        }

        val card = when (outcome) {
            is AssistantOutcome.Plan -> if (outcome.advices.isEmpty()) {
                null
            } else {
                ReplyCard.Plan(
                    advices = outcome.advices,
                    totalBytes = outcome.advices.sumOf { it.totalBytes },
                    safeBytes = outcome.advices
                        .filter { it.risk == CleanRisk.SAFE }
                        .sumOf { it.recommendedBytes },
                )
            }

            is AssistantOutcome.Freeze -> if (outcome.candidates.isEmpty()) {
                null
            } else {
                ReplyCard.Freeze(outcome.candidates)
            }

            is AssistantOutcome.Info -> null
        }

        return Bubble.Assistant(
            id = nextId++,
            text = outcome.explanation(),
            understood = intent.intent != IntentType.UNKNOWN,
            tier = intent.source,
            confidence = intent.confidence,
            excludedLabels = labels,
            olderThanDays = intent.olderThanDays,
            card = card,
        )
    }

    private fun AssistantOutcome.explanation(): String = when (this) {
        is AssistantOutcome.Plan ->
            if (advices.isEmpty()) {
                "已经按你的条件筛过一遍，没有匹配到可清理的项目。" +
                    "可以放宽条件（比如去掉「半年前」这个时间限制）再试一次。"
            } else {
                explanation
            }

        is AssistantOutcome.Freeze ->
            if (candidates.isEmpty()) {
                "没有找到符合条件的不常用应用。这可能是因为设备刚使用不久，" +
                    "或者「使用情况访问」权限尚未授予 —— 那会导致所有应用都无法判定为不常用。"
            } else {
                explanation
            }

        is AssistantOutcome.Info -> explanation
    }
}
