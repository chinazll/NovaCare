package com.novacare.feature.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacare.core.ai.ChatMessage
import com.novacare.core.ai.CloudLlmProvider
import com.novacare.core.common.formatBytes
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
 * AI 助手 —— 真正的对话状态机
 *
 * ============================================================
 * 【v0.7.4 重写 —— 从「指令解析器」升级为「真对话」】
 *
 * 上一版的问题是本质性的：无论云端还是本地，它都只做一件事——
 * 把一句话解析成 5 种意图之一，然后吐一张清单。这不是对话，是查询接口。
 * 用户说"AI 没做"，完全成立。
 *
 * 这一版的分层：
 *
 *  1. **云端已配置**（用户在设置里填了 key）：
 *     - 走 [CloudLlmProvider.completeStream] **流式多轮对话**，
 *       把设备真实状态（存储/内存/应用数）注入 system prompt，
 *       AI 能自由回答、追问、给建议 —— token 逐个蹦出来，用户看到"AI 在打字"。
 *     - 同时本地规则仍解析一次意图：若 AI 回复里带出可执行的动作
 *       （清理/冻结），下面仍附操作卡片，保证"说得清 + 做得到"。
 *
 *  2. **云端未配置**：
 *     - 走本地规则解析（离线、确定性），明确标注"本地规则"，
 *       并提示用户可到设置开启云端获得更自然的对话。
 *
 * 【诚实底线不变】
 * 解析不出意图就明说"没听懂"，绝不猜一个动作去执行。
 * 操作卡片永远"先清单、后执行"，不静默动手。
 */
@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val scan: ScanDeviceUseCase,
    private val cache: DeviceSnapshotCache,
    private val assistant: AssistantUseCase,
    private val executePlanUseCase: ExecutePlanUseCase,
    private val engine: NovaEngine,
    private val settings: SettingsRepository,
    private val llm: CloudLlmProvider,
) : ViewModel() {

    data class Capability(
        val cloudEnabled: Boolean,
        val engineAvailable: Boolean,
        val engineVersion: String,
    ) {
        val tierLabel: String
            get() = if (cloudEnabled) "云端对话 · 流式" else "本地规则 · 离线"
    }

    sealed interface Bubble {
        val id: Long

        data class User(override val id: Long, val text: String) : Bubble

        data class Assistant(
            override val id: Long,
            val text: String,
            val understood: Boolean,
            val tier: AiTier,
            val confidence: Float,
            val excludedLabels: List<String>,
            val olderThanDays: Int?,
            val card: ReplyCard?,
            /** 是否由云端 LLM 流式生成（决定是否标注"云端对话"） */
            val streamed: Boolean = false,
        ) : Bubble
    }

    sealed interface ReplyCard {
        data class Plan(
            val advices: List<CleanAdvice>,
            val totalBytes: Long,
            val safeBytes: Long,
        ) : ReplyCard

        data class Freeze(val candidates: List<FreezeCandidate>) : ReplyCard
    }

    data class ExecutionReport(
        val freedBytes: Long,
        val succeeded: Int,
        val failed: Int,
        val needsManual: Int,
        val recycleBinPath: String?,
    )

    private val _bubbles = MutableStateFlow<List<Bubble>>(emptyList())
    val bubbles: StateFlow<List<Bubble>> = _bubbles.asStateFlow()

    private val _thinking = MutableStateFlow(false)
    val thinking: StateFlow<Boolean> = _thinking.asStateFlow()

    /** 流式对话的中间态：非空表示正在逐 token 输出这段文本 */
    private val _streamingText = MutableStateFlow<String?>(null)
    val streamingText: StateFlow<String?> = _streamingText.asStateFlow()

    private val _capability = MutableStateFlow(
        Capability(cloudEnabled = false, engineAvailable = true, engineVersion = ""),
    )
    val capability: StateFlow<Capability> = _capability.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _report = MutableStateFlow<ExecutionReport?>(null)
    val report: StateFlow<ExecutionReport?> = _report.asStateFlow()

    private var nextId = 1L
    private var lastText: String = ""

    /** 多轮对话历史（云端模式用），不含 system —— system 每次根据最新快照重建 */
    private val history = mutableListOf<ChatMessage>()

    init {
        refreshCapability()
    }

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

    fun submit(text: String, rootPath: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _thinking.value) return

        lastText = trimmed
        _error.value = null
        _report.value = null
        _bubbles.value = _bubbles.value + Bubble.User(nextId++, trimmed)
        _thinking.value = true

        viewModelScope.launch {
            val snapshot = runCatching { cache.last ?: scan(rootPath).also { cache.put(it) } }
                .getOrElse {
                    _thinking.value = false
                    _error.value = it.message ?: "读取设备失败"
                    return@launch
                }

            // ---- 云端已配置：流式多轮对话 ----
            if (llm.isConfigured) {
                runCatching {
                    val advanced = settings.settings.first().advancedMode
                    // 本地规则仍解析一次意图，作为"可执行动作"层
                    val intent = assistant.parse(trimmed, snapshot)
                    val messages = buildCloudMessages(trimmed, snapshot)
                    var full = ""
                    val text2 = llm.completeStream(messages) { delta ->
                        full += delta
                        _streamingText.value = full
                    }
                    val reply = text2 ?: full
                    history.add(ChatMessage("user", trimmed))
                    history.add(ChatMessage("assistant", reply))
                    _streamingText.value = null
                    val outcome = assistant.apply(intent, snapshot, advanced)
                    buildReply(trimmed, reply, intent, outcome, snapshot, streamed = true)
                }.onSuccess { bubble ->
                    _bubbles.value = _bubbles.value + bubble
                    _thinking.value = false
                    _streamingText.value = null
                }.onFailure { e ->
                    _streamingText.value = null
                    _thinking.value = false
                    _error.value = e.message ?: "云端对话失败，已回退。可在设置里检查 Key。"
                }
                return@launch
            }

            // ---- 本地规则模式（离线、确定性） ----
            runCatching {
                val advanced = settings.settings.first().advancedMode
                val intent = assistant.parse(trimmed, snapshot)
                val outcome = assistant.apply(intent, snapshot, advanced)
                buildReply(trimmed, outcome.text(), intent, outcome, snapshot, streamed = false)
            }.onSuccess { bubble ->
                _bubbles.value = _bubbles.value + bubble
                _thinking.value = false
            }.onFailure { e ->
                _thinking.value = false
                _error.value = e.message ?: "解析失败，请重试"
            }
        }
    }

    /**
     * 构造云端对话的消息列表。
     *
     * system prompt 注入设备真实状态 —— 让 AI 的回答"有据"，而不是凭空说车轱辘话。
     * 历史保留最近 6 条（3 轮），避免 prompt 无限膨胀。
     */
    private fun buildCloudMessages(userText: String, s: DeviceSnapshot): List<ChatMessage> {
        val used = s.storage.totalBytes - s.storage.availableBytes
        val percent = if (s.storage.totalBytes > 0L) {
            (used * 100 / s.storage.totalBytes).toInt()
        } else 0
        val system = buildString {
            append("你是 NovaCare，一个安卓系统清理与优化助手。")
            append("你能读取设备的真实状态并给出建议，也能生成清理/冻结操作清单。")
            append("用户可能用中文提问、闲聊或下指令。回答要简洁、说人话、不堆术语。")
            append("\n\n当前设备状态（真实数据，回答时可引用）：")
            append("\n- 存储：已用 $percent%（已用 ${used.formatBytes()} / " +
                "共 ${s.storage.totalBytes.formatBytes()}）")
            append("\n- 已安装应用：${s.apps.size} 个")
            append("\n- 可回收空间：${(s.junk?.totalBytes ?: 0L).formatBytes()}")
            append("\n- 内核可用：${s.engineAvailable}")
            append("\n\n规则：如果用户表达了清理/冻结/分析的意图，先用一句话确认你的理解，")
            append("再给出建议；不要替用户执行任何操作（执行由 App 的确认卡片完成）。")
        }
        val tail = history.takeLast(6)
        return listOf(ChatMessage("system", system)) + tail +
            ChatMessage("user", userText)
    }

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

    fun clearReport() {
        _report.value = null
    }

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
                runCatching { scan(rootPath) }.getOrNull()?.let { cache.put(it) }
                _thinking.value = false
            }.onFailure { e ->
                _error.value = e.message ?: "执行失败"
                _thinking.value = false
            }
        }
    }

    private fun buildReply(
        userText: String,
        replyText: String,
        intent: ParsedIntent,
        outcome: AssistantOutcome,
        snapshot: DeviceSnapshot,
        streamed: Boolean,
    ): Bubble.Assistant {
        val labels = intent.excludePackages.mapNotNull { pkg ->
            snapshot.apps.firstOrNull { it.packageName == pkg }?.label
        }

        val card = when (outcome) {
            is AssistantOutcome.Plan -> if (outcome.advices.isEmpty()) null else {
                ReplyCard.Plan(
                    advices = outcome.advices,
                    totalBytes = outcome.advices.sumOf { it.totalBytes },
                    safeBytes = outcome.advices
                        .filter { it.risk == CleanRisk.SAFE }
                        .sumOf { it.recommendedBytes },
                )
            }
            is AssistantOutcome.Freeze -> if (outcome.candidates.isEmpty()) null else {
                ReplyCard.Freeze(outcome.candidates)
            }
            is AssistantOutcome.Info -> null
        }

        return Bubble.Assistant(
            id = nextId++,
            text = replyText,
            understood = intent.intent != IntentType.UNKNOWN,
            tier = if (streamed) AiTier.CLOUD else intent.source,
            confidence = intent.confidence,
            excludedLabels = labels,
            olderThanDays = intent.olderThanDays,
            card = card,
            streamed = streamed,
        )
    }
}

/** AssistantOutcome 各子类都有 explanation 字段，但 sealed interface 未声明该属性，这里统一取值 */
private fun AssistantOutcome.text(): String = when (this) {
    is AssistantOutcome.Plan -> explanation
    is AssistantOutcome.Freeze -> explanation
    is AssistantOutcome.Info -> explanation
}
