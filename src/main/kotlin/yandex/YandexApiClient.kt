package yandex

import ApiClientInterface
import dto.ApiResponse
import dto.ApiResult
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import yandex.dto.CompletionOptionsDto
import yandex.dto.MessageDto
import yandex.dto.RequestDto
import yandex.dto.ResponseDto
import java.math.BigDecimal
import java.math.RoundingMode

class YandexApiClient : ApiClientInterface {
    private companion object {
        const val URL = "https://llm.api.cloud.yandex.net/foundationModels/v1/completion"
        val apiKey: String = System.getProperty("yandexApiKey")
    }

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
        engine {
            endpoint {
                connectTimeout = 60000
                requestTimeout = 120000
            }
        }
    }

    private var temperature: Double = 0.7

    private var systemPrompt: String = """
        Ты - генеративная языковая модель
    """.trimIndent()

    private var maxTokens: Int = 100

    private val messageHistory = mutableListOf<MessageDto>()

    override fun getSystemPrompt(): String = systemPrompt

    override fun setSystemPrompt(prompt: String) {
        systemPrompt = prompt
    }

    override fun clearMessages() {
        messageHistory.clear()
    }

    override fun getTemperature(): Double = temperature

    override fun setTemperature(temperature: Double) {
        this.temperature = temperature.coerceIn(0.0, 1.0)
    }

    override fun getMaxTokens(): Int = maxTokens

    override fun setMaxTokens(maxTokens: Int) {
        this.maxTokens = maxTokens.coerceIn(1, 10000)
    }

    override fun sendRequest(query: String): ApiResponse = runBlocking {
        sendRequestAsync(query)
    }

    private suspend fun sendRequestAsync(userPrompt: String): ApiResponse =
        try {
            val userMessage = MessageDto(role = "user", text = userPrompt)
            messageHistory.add(userMessage)

            val request = RequestDto(
                modelUri = "gpt://b1g2vhjdd9rgjq542poc/yandexgpt/rc",
                completionOptions = CompletionOptionsDto(
                    stream = false,
                    temperature = temperature,
                    maxTokens = maxTokens
                ),
                messages = listOf(MessageDto("system", systemPrompt)) + messageHistory
            )

            println("Sending POST request to: $URL\n$request")
            val startTime = System.currentTimeMillis()
            val response: HttpResponse = httpClient.post(URL) {
                header("accept", "application/json")
                header("content-type", "application/json")
                header("Authorization", "Api-Key $apiKey")
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            val endTime = System.currentTimeMillis()
            val executionTime = endTime - startTime
            println("Status: ${response.status}")
            val body = response.body<ResponseDto>()
            println(body)
            val answer = body.result.alternatives.first().message.text
            val assistantMessage = MessageDto(role = "assistant", text = answer)
            messageHistory.add(assistantMessage)
            val apiResult = ApiResult(
                elapsedTime = executionTime,
                promptTokens = body.result.usage.inputTextTokens,
                completionTokens = body.result.usage.completionTokens,
                totalTokens = body.result.usage.totalTokens,
                cost = BigDecimal(0.4 / 1000 * body.result.usage.totalTokens).setScale(2, RoundingMode.HALF_UP).toDouble()
            )
            ApiResponse(message = answer, result = Json.encodeToString(apiResult))
        } catch (e: Exception) {
            ApiResponse("Request failed", e.localizedMessage)
        }
}