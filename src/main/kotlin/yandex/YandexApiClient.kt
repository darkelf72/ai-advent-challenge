package yandex

import BaseApiClient
import RequestContext
import StandardApiResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import yandex.dto.CompletionOptionsDto
import yandex.dto.MessageDto
import yandex.dto.RequestDto
import yandex.dto.ResponseDto
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * API клиент для Yandex GPT.
 * Наследует общую логику от BaseApiClient и реализует специфику Yandex API.
 */
class YandexApiClient(
    httpClient: HttpClient
) : BaseApiClient(httpClient) {

    private companion object {
        const val URL = "https://llm.api.cloud.yandex.net/foundationModels/v1/completion"
        val apiKey: String = System.getProperty("yandexApiKey")
    }

    /**
     * Выполняет запрос к Yandex GPT API
     */
    override suspend fun executeApiRequest(context: RequestContext): StandardApiResponse {
        // Конвертируем ChatMessage в MessageDto для Yandex API
        val systemMessage = MessageDto(role = "system", text = context.systemPrompt)
        val historyMessages = context.messageHistory.map { msg ->
            MessageDto(role = msg.role, text = msg.content)
        }

        val request = RequestDto(
            modelUri = "gpt://b1g2vhjdd9rgjq542poc/yandexgpt/rc",
            completionOptions = CompletionOptionsDto(
                stream = false,
                temperature = context.temperature,
                maxTokens = context.maxTokens
            ),
            messages = listOf(systemMessage) + historyMessages
        )

        println("Sending POST request to: $URL\n$request")
        val response: HttpResponse = httpClient.post(URL) {
            header("accept", "application/json")
            header("content-type", "application/json")
            header("Authorization", "Api-Key $apiKey")
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        println("Status: ${response.status}")
        val body = response.body<ResponseDto>()
        println(body)

        return StandardApiResponse(
            answer = body.result.alternatives.first().message.text,
            promptTokens = body.result.usage.inputTextTokens,
            completionTokens = body.result.usage.completionTokens,
            totalTokens = body.result.usage.totalTokens
        )
    }

    /**
     * Рассчитывает стоимость запроса по тарифам Yandex GPT
     * Стоимость: 0.4 рубля за 1000 токенов
     */
    override fun calculateCost(totalTokens: Int): Double {
        return BigDecimal(0.4 / 1000 * totalTokens)
            .setScale(2, RoundingMode.HALF_UP)
            .toDouble()
    }
}