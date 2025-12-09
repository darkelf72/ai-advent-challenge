package yandex

import ApiClientInterface
import dto.ApiResponse
import dto.ApiResult
import yandex.dto.CompletionOptionsDto
import yandex.dto.MessageDto
import yandex.dto.RequestDto
import yandex.dto.ResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.math.RoundingMode

class YandexApiClient : ApiClientInterface {
    private companion object {
        const val URL = "https://llm.api.cloud.yandex.net/foundationModels/v1/completion"
        val apiKey: String = System.getProperty("yandexApiKey")
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
    }

    private var temperature: Double = 0.7

    private var systemPrompt: String = """
        Ты - генеративная языковая модель
    """.trimIndent()

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
                    maxTokens = 500
                ),
                messages = listOf(MessageDto("system", systemPrompt)) + messageHistory
            )

            println("Sending POST request to: $URL\n$request")
            val startTime = System.currentTimeMillis()
            val response: HttpResponse = client.post(URL) {
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
                totalTokens = body.result.usage.totalTokens,
                cost = BigDecimal(1500.0 / 1000000.0 * body.result.usage.totalTokens).setScale(2, RoundingMode.HALF_UP).toDouble()
            )
            ApiResponse(message = answer, result = Json.encodeToString(apiResult))
        } catch (e: Exception) {
            ApiResponse("Request failed", e.localizedMessage)
        }
}