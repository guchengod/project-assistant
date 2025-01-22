package com.github.guchengod.projectassistant.services

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.io.IOException
import okhttp3.Interceptor
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicReference

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

class AIModelService private constructor() {
    private val gson = Gson()
    private val jsonMediaType = "application/json".toMediaType()
    
    // 使用 AtomicReference 确保线程安全的客户端引用
    private val clientRef = AtomicReference<OkHttpClient>()

    // 懒加载初始化 OkHttpClient
    private fun getClient(): OkHttpClient {
        return clientRef.get() ?: synchronized(this) {
            clientRef.get() ?: createNewClient().also { clientRef.set(it) }
        }
    }

    private fun createNewClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(RetryInterceptor(maxRetries = 3))
            .addInterceptor(ErrorInterceptor())
            .build()
    }

    fun createService(config: AIServiceConfig): AIService {
        return when (config.provider) {
            Provider.DEEPSEEK -> DeepSeekService(config, this)
            Provider.OPENAI -> OpenAIService(config, this)
        }
    }

    // 将原来的公共方法改为内部方法
    internal fun executeRequest(request: Request): okhttp3.Response {
        return executeWithRetry {
            getClient().newCall(request).execute()
        }
    }

    // 重试拦截器
    private class RetryInterceptor(private val maxRetries: Int) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
            var retryCount = 0
            var response: okhttp3.Response? = null
            var exception: IOException? = null

            while (retryCount < maxRetries) {
                try {
                    response = chain.proceed(chain.request())
                    if (response.isSuccessful) {
                        return response
                    } else if (response.code in 500..599) {
                        response.close()
                        retryCount++
                        Thread.sleep(1000L * retryCount) // 指数退避
                        continue
                    }
                    return response
                } catch (e: IOException) {
                    exception = e
                    retryCount++
                    if (retryCount == maxRetries) break
                    Thread.sleep(1000L * retryCount)
                }
            }

            throw exception ?: IOException("Request failed after $maxRetries retries")
        }
    }

    // 错误处理拦截器
    private class ErrorInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
            try {
                val response = chain.proceed(chain.request())
                when (response.code) {
                    in 400..499 -> throw APIException("Client error", response.code)
                    in 500..599 -> throw APIException("Server error", response.code)
                }
                return response
            } catch (e: SocketTimeoutException) {
                throw APIException("Connection timeout", -1, e)
            } catch (e: IOException) {
                throw APIException("Network error", -1, e)
            }
        }
    }

    // 自定义异常类
    class APIException(
        message: String,
        val statusCode: Int,
        cause: Throwable? = null
    ) : Exception("$message (Status Code: $statusCode)", cause)

    // 通用重试执行方法
    private fun <T> executeWithRetry(block: () -> T): T {
        var lastException: Exception? = null
        repeat(3) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                if (attempt < 2) { // 最后一次尝试不需要等待
                    Thread.sleep(1000L * (attempt + 1))
                }
            }
        }
        throw lastException ?: RuntimeException("Unknown error occurred")
    }

    companion object {
        const val DEEPSEEK_BASE_URL = "https://api.deepseek.com"
        const val OPENAI_BASE_URL = "https://api.openai.com/v1"

        @Volatile
        private var instance: AIModelService? = null

        fun getInstance(): AIModelService {
            return instance ?: synchronized(this) {
                instance ?: AIModelService().also { instance = it }
            }
        }
    }
}

// 配置数据类
data class AIServiceConfig(
    val provider: Provider,
    val apiKey: String,
    val model: String,
    val baseUrl: String,
    val proxyType: String? = null,
    val proxyHost: String? = null,
    val proxyPort: Int? = null
)

enum class Provider {
    DEEPSEEK, OPENAI
}

// DeepSeek 实现
private class DeepSeekService(
    private val config: AIServiceConfig,
    private val modelService: AIModelService
) : AIService {
    
    override fun fetchModels(): List<Model> {
        val request = Request.Builder()
            .url("${config.baseUrl}/models")
            .get()
            .addHeader("Accept", "application/json")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .build()

        modelService.executeRequest(request).use { response ->
            if (!response.isSuccessful) {
                throw AIModelService.APIException("Failed to fetch models", response.code)
            }
            return Gson().fromJson(response.body?.string(), ModelResponse::class.java).data
        }
    }

    override fun sendMessage(message: String, onChunk: (String) -> Unit) {
        val chatRequest = ChatRequest(
            messages = listOf(
                ChatMessage("system", "You are a helpful assistant"),
                ChatMessage("user", message)
            ),
            model = config.model
        )

        val requestBody = Gson().toJson(chatRequest)
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("${config.baseUrl}/chat/completions")
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .build()

        modelService.executeRequest(request).use { response ->
            if (!response.isSuccessful) {
                throw AIModelService.APIException("Failed to send message", response.code)
            }

            response.body?.source()?.use { source ->
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: continue
                    if (line.isEmpty() || !line.startsWith("data: ")) continue
                    
                    val data = line.substring(6)
                    if (data == "[DONE]") break

                    try {
                        val streamResponse = Gson().fromJson(data, StreamResponse::class.java)
                        streamResponse.choices.firstOrNull()?.delta?.content?.let(onChunk)
                    } catch (e: Exception) {
                        continue
                    }
                }
            }
        }
    }

    override fun fetchBalance(): BalanceResponse {
        val request = Request.Builder()
            .url("${config.baseUrl}/user/balance")
            .get()
            .addHeader("Accept", "application/json")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .build()

        modelService.executeRequest(request).use { response ->
            if (!response.isSuccessful) {
                throw AIModelService.APIException("Failed to fetch balance", response.code)
            }
            return Gson().fromJson(response.body?.string(), BalanceResponse::class.java)
        }
    }
}

// OpenAI 实现（类似 DeepSeekService）
private class OpenAIService(
    private val config: AIServiceConfig,
    private val modelService: AIModelService
) : AIService {
    override fun fetchModels(): List<Model> {
        TODO("Not yet implemented")
    }

    override fun sendMessage(message: String, onChunk: (String) -> Unit) {
        TODO("Not yet implemented")
    }

    override fun fetchBalance(): BalanceResponse {
        TODO("Not yet implemented")
    }

}
