package com.github.guchengod.projectassistant.services

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class ModelResponse(
    val `object`: String,
    val data: List<Model>
)

data class Model(
    val id: String,
    val `object`: String,
    @SerializedName("owned_by")
    val ownedBy: String
)

data class ChatMessage(
    val role: String,
    val content: String
)

data class ChatRequest(
    val messages: List<ChatMessage>,
    val model: String,
    @SerializedName("frequency_penalty")
    val frequencyPenalty: Double = 0.0,
    @SerializedName("max_tokens")
    val maxTokens: Int = 2048,
    @SerializedName("presence_penalty")
    val presencePenalty: Double = 0.0,
    @SerializedName("response_format")
    val responseFormat: ResponseFormat = ResponseFormat("text"),
    val stop: Any? = null,
    val stream: Boolean = true,
    @SerializedName("stream_options")
    val streamOptions: Any? = null,
    val temperature: Double = 1.0,
    @SerializedName("top_p")
    val topP: Double = 1.0,
    val tools: Any? = null,
    @SerializedName("tool_choice")
    val toolChoice: String = "none",
    val logprobs: Boolean = false,
    @SerializedName("top_logprobs")
    val topLogprobs: Any? = null
)

data class ResponseFormat(
    val type: String
)

data class ChatResponse(
    val id: String,
    val choices: List<Choice>,
    val created: Long,
    val model: String,
    val `object`: String,
    val usage: Usage
)

data class Choice(
    @SerializedName("finish_reason")
    val finishReason: String,
    val index: Int,
    val message: ChatMessage
)

data class Usage(
    @SerializedName("completion_tokens")
    val completionTokens: Int,
    @SerializedName("prompt_tokens")
    val promptTokens: Int,
    @SerializedName("total_tokens")
    val totalTokens: Int
)

data class StreamChoice(
    @SerializedName("finish_reason")
    val finishReason: String?,
    val index: Int,
    val delta: Delta,
    val logprobs: Any?
)

data class Delta(
    val content: String?,
    val role: String?
)

data class StreamResponse(
    val id: String,
    val choices: List<StreamChoice>,
    val created: Long,
    val model: String,
    val `object`: String,
    @SerializedName("system_fingerprint")
    val systemFingerprint: String,
    val usage: Usage?
)

data class BalanceInfo(
    val currency: String,
    @SerializedName("total_balance")
    val totalBalance: String,
    @SerializedName("granted_balance")
    val grantedBalance: String,
    @SerializedName("topped_up_balance")
    val toppedUpBalance: String
)

data class BalanceResponse(
    @SerializedName("is_available")
    val isAvailable: Boolean,
    @SerializedName("balance_infos")
    val balanceInfos: List<BalanceInfo>
)

class AIModelService {
    private val client = OkHttpClient()
    private val gson = Gson()
    private val jsonMediaType = "application/json".toMediaType()

    fun fetchModels(baseUrl: String, apiKey: String): List<Model> {
        val request = Request.Builder()
            .url("$baseUrl/models")
            .get()
            .addHeader("Accept", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Failed to fetch models: ${response.code}")
            }
            return gson.fromJson(response.body?.string(), ModelResponse::class.java).data
        }
    }

    fun sendMessage(
        baseUrl: String,
        apiKey: String,
        model: String,
        message: String,
        onChunk: (String) -> Unit
    ) {
        val chatRequest = ChatRequest(
            messages = listOf(
                ChatMessage("system", "You are a helpful assistant"),
                ChatMessage("user", message)
            ),
            model = model
        )

        val requestBody = gson.toJson(chatRequest).toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Failed to send message: ${response.code}")
            }

            response.body?.let { body ->
                body.source().use { source ->
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: continue
                        if (line.isEmpty()) continue

                        if (!line.startsWith("data: ")) continue
                        val data = line.substring(6)

                        if (data == "[DONE]") break

                        try {
                            val streamResponse = gson.fromJson(data, StreamResponse::class.java)
                            val content = streamResponse.choices.firstOrNull()?.delta?.content
                            if (content != null) {
                                onChunk(content)
                            }
                        } catch (e: Exception) {
                            // Skip malformed JSON
                            continue
                        }
                    }
                }
            }
        }
    }

    fun fetchBalance(baseUrl: String, apiKey: String): BalanceResponse {
        val request = Request.Builder()
            .url("$baseUrl/user/balance")
            .get()
            .addHeader("Accept", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Failed to fetch balance: ${response.code}")
            }
            return gson.fromJson(response.body?.string(), BalanceResponse::class.java)
        }
    }

    companion object {
        const val DEEPSEEK_BASE_URL = "https://api.deepseek.com"
        const val OPENAI_BASE_URL = "https://api.openai.com/v1"
    }
}
