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
 * 设计要点：
 * - **OpenAI 兼容协议**：MiniMax / 智谱 / 通义 / DeepSeek / Gemini 都提供
 *   `/v1/chat/completions` 兼容端点，因此一个实现覆盖全部模型，用户可在设置中切换
 * - **默认不联网**：[CloudLlmConfig.apiKey] 为空时 [isConfigured] = false，
 *   所有调用直接返回 null，绝不静默发请求
 * - **合规**：中国区模型（MiniMax / 智谱 / 通义 / DeepSeek）已在国内备案，
 *   数据不出境；见 com.novacare.core.model.CloudModel.isChinaCompliant
 */
interface CloudLlmProvider {
    val isConfigured: Boolean
    suspend fun complete(systemPrompt: String, userPrompt: String): String?
}

data class CloudLlmConfig(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
)

@Serializable
private data class ChatMessage(val role: String, val content: String)

@Serializable
private data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.1,
)

@Serializable
private data class ChatChoice(val message: ChatMessage? = null)

@Serializable
private data class ChatResponse(val choices: List<ChatChoice> = emptyList())

class OpenAiCompatibleLlmProvider(
    private val configProvider: () -> CloudLlmConfig,
) : CloudLlmProvider {

    private val client = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
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
                    .url(cfg.baseUrl.trimEnd('/') + "/chat/completions")
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
}

/** 未配置 / 用户未开启时的实现：任何调用都返回 null，不发一个字节的网络请求 */
class DisabledCloudLlmProvider : CloudLlmProvider {
    override val isConfigured: Boolean get() = false
    override suspend fun complete(systemPrompt: String, userPrompt: String): String? = null
}
