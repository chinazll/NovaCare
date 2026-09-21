package com.novacare.core.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * L3 —— 云端 LLM Provider（默认关闭）
 *
 * ============================================================
 * 【v0.7.4 重写 —— 从「意图解析器」升级为「真对话」】
 *
 * 上一版只有 [complete]（一次性返回），且调用方 LlmIntentParser 强制
 * LLM 只吐 JSON 意图 —— 结果是「AI 助手」其实是个正则解析器的外壳，
 * 用户说的"AI 没做"完全成立：没有多轮、没有流式、不能自由回答。
 *
 * 这一版补齐：
 *   1. [completeStream] —— OpenAI 兼容 SSE 流式（stream=true），
 *      token 逐个下发，用户看到的是"AI 在打字"而非"转圈 10 秒后出全文"。
 *   2. [ChatMessage] 公开 —— 调用方可携带完整多轮历史，实现真正的对话。
 *
 * 设计约束不变：
 *   - 未配置（无 key）时 isConfigured=false，一个字节都不发
 *   - OpenAI 兼容协议：MiniMax / 智谱 / 通义 / DeepSeek / Gemini 全通
 */
interface CloudLlmProvider {
    val isConfigured: Boolean
    suspend fun complete(systemPrompt: String, userPrompt: String): String?

    /**
     * 流式对话。
     *
     * @param messages 完整历史（含 system + 用户多轮 + 助手多轮），按时间序
     * @param onDelta 每收到一个增量 token 回调一次（在 IO 线程）
     * @return 完整回复文本；失败返回 null
     */
    suspend fun completeStream(
        messages: List<ChatMessage>,
        onDelta: (String) -> Unit,
    ): String?
}

data class CloudLlmConfig(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
)

@Serializable
private data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.3,
    val stream: Boolean = false,
)

@Serializable
private data class ChatChoice(val message: ChatMessage? = null, val delta: ChatDelta? = null)

@Serializable
private data class ChatDelta(val content: String? = null)

@Serializable
private data class ChatResponse(val choices: List<ChatChoice> = emptyList())

@Serializable
private data class StreamChunk(val choices: List<ChatChoice> = emptyList())

class OpenAiCompatibleLlmProvider(
    private val configProvider: () -> CloudLlmConfig,
) : CloudLlmProvider {

    private val client = OkHttpClient.Builder()
        .callTimeout(60, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    override val isConfigured: Boolean
        get() {
            val c = configProvider()
            return c.apiKey.isNotBlank() && c.baseUrl.isNotBlank() && c.model.isNotBlank()
        }

    override suspend fun complete(systemPrompt: String, userPrompt: String): String? {
        if (!isConfigured) return null
        val cfg = configProvider()
        return runCatching {
            withContext(Dispatchers.IO) {
                val body = json.encodeToString(
                    ChatRequest.serializer(),
                    ChatRequest(
                        model = cfg.model,
                        messages = listOf(
                            ChatMessage("system", systemPrompt),
                            ChatMessage("user", userPrompt),
                        ),
                    ),
                )
                val request = Request.Builder()
                    .url(chatCompletionsUrl(cfg.baseUrl))
                    .addHeader("Authorization", "Bearer " + cfg.apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    val raw = resp.body?.string() ?: return@withContext null
                    val parsed = json.decodeFromString(ChatResponse.serializer(), raw)
                    parsed.choices.firstOrNull()?.message?.content
                }
            }
        }.getOrNull()
    }

    override suspend fun completeStream(
        messages: List<ChatMessage>,
        onDelta: (String) -> Unit,
    ): String? {
        if (!isConfigured) return null
        val cfg = configProvider()
        return runCatching {
            withContext(Dispatchers.IO) {
                val body = json.encodeToString(
                    ChatRequest.serializer(),
                    ChatRequest(
                        model = cfg.model,
                        messages = messages,
                        stream = true,
                    ),
                )
                val request = Request.Builder()
                    .url(chatCompletionsUrl(cfg.baseUrl))
                    .addHeader("Authorization", "Bearer " + cfg.apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    val source = resp.body?.source() ?: return@withContext null
                    val sb = StringBuilder()
                    // SSE 格式：每个事件形如 `data: {...}\n\n`，结束标志 `data: [DONE]`
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        if (!line.startsWith("data:")) continue
                        val payload = line.removePrefix("data:").trim()
                        if (payload == "[DONE]") break
                        val chunk = runCatching {
                            json.decodeFromString(StreamChunk.serializer(), payload)
                        }.getOrNull() ?: continue
                        val delta = chunk.choices.firstOrNull()?.delta?.content
                        if (!delta.isNullOrEmpty()) {
                            sb.append(delta)
                            onDelta(delta)
                        }
                    }
                    sb.toString().ifBlank { null }
                }
            }
        }.getOrNull()
    }

    /** baseUrl 已含版本路径（/v1、/api/paas/v4 等），这里只补端点 */
    private fun chatCompletionsUrl(baseUrl: String): String =
        baseUrl.trimEnd('/') + "/chat/completions"
}

/** 未配置 / 用户未开启时的实现：任何调用都返回 null，不发一个字节的网络请求 */
class DisabledCloudLlmProvider : CloudLlmProvider {
    override val isConfigured: Boolean get() = false
    override suspend fun complete(systemPrompt: String, userPrompt: String): String? = null
    override suspend fun completeStream(
        messages: List<ChatMessage>,
        onDelta: (String) -> Unit,
    ): String? = null
}
