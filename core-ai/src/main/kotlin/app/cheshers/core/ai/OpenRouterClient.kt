package app.cheshers.core.ai

import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface TextAiClient {
    suspend fun complete(
        apiKey: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        temperature: Double,
        maxTokens: Int,
    ): String
}

interface OpenRouterService {
    @POST("chat/completions")
    suspend fun chatCompletions(
        @Header("Authorization") authorization: String,
        @Header("HTTP-Referer") referer: String,
        @Header("X-Title") title: String,
        @Body request: ChatCompletionRequest,
    ): ChatCompletionResponse

    @GET("models")
    suspend fun models(@Header("Authorization") authorization: String): ModelsResponse
}

data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double,
    @SerializedName("max_tokens") val maxTokens: Int,
    @SerializedName("response_format") val responseFormat: ResponseFormat = ResponseFormat(),
)

data class ResponseFormat(val type: String = "json_object")
data class ChatMessage(val role: String, val content: String)
data class ChatCompletionResponse(
    val choices: List<ChatChoice> = emptyList(),
    val error: OpenRouterError? = null,
)
data class ChatChoice(val message: ChatMessage? = null)
data class OpenRouterError(val message: String? = null)
data class ModelsResponse(val data: List<RemoteModel> = emptyList())
data class RemoteModel(val id: String = "", val name: String = "")

/** No logging interceptor is installed anywhere: bearer tokens must never reach logs. */
class RetrofitOpenRouterClient(
    private val service: OpenRouterService,
    private val minimumIntervalMs: Long = 350,
) : TextAiClient {
    private val requestMutex = Mutex()
    private var lastRequestAt = 0L

    override suspend fun complete(
        apiKey: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        temperature: Double,
        maxTokens: Int,
    ): String {
        require(apiKey.isNotBlank()) { "An OpenRouter API key is required" }
        return requestMutex.withLock {
            val wait = minimumIntervalMs - (System.currentTimeMillis() - lastRequestAt)
            if (wait > 0) delay(wait)
            val response = service.chatCompletions(
                authorization = "Bearer $apiKey",
                referer = APP_REFERER,
                title = "Cheshers",
                request = ChatCompletionRequest(
                    model = model,
                    messages = listOf(
                        ChatMessage("system", systemPrompt),
                        ChatMessage("user", userPrompt),
                    ),
                    temperature = temperature,
                    maxTokens = maxTokens,
                ),
            )
            lastRequestAt = System.currentTimeMillis()
            response.error?.let { throw OpenRouterRequestException("OpenRouter rejected the request") }
            response.choices.firstOrNull()?.message?.content?.takeIf { it.isNotBlank() }
                ?: throw OpenRouterRequestException("OpenRouter returned an empty response")
        }
    }

    suspend fun availableModels(apiKey: String): List<OpenRouterModel> =
        service.models("Bearer $apiKey").data.mapNotNull { model ->
            model.id.takeIf { it.isNotBlank() }?.let { OpenRouterModel(it, model.name.ifBlank { it }) }
        }.sortedBy { it.label.lowercase() }

    companion object { const val APP_REFERER = "https://github.com/cheshers" }
}

class OpenRouterRequestException(message: String) : Exception(message)
class AiUnavailableException(message: String = "OpenRouter is unavailable") : Exception(message)
