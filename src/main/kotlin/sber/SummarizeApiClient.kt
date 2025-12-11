package sber

import BaseApiClient
import RequestContext
import StandardApiResponse
import config.ApiClientConfig
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.slf4j.LoggerFactory
import sber.dto.GigaChatMessage
import sber.dto.GigaChatRequest
import sber.dto.GigaChatResponse
import sber.dto.OAuthTokenResponse
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*

/**
 * API клиент для GigaChat (Сбер).
 * Наследует общую логику от BaseApiClient и реализует специфику GigaChat API.
 * Включает логику OAuth аутентификации с кешированием токена.
 */
class SummarizeApiClient(
    httpClient: HttpClient,
    apiClientConfig: ApiClientConfig
) : BaseApiClient(httpClient, apiClientConfig) {

    private val logger = LoggerFactory.getLogger(GigaChatApiClient::class.java)

    private companion object {
        val apiKey: String = System.getProperty("gigaChatApiKey")
        const val BASE_URL = "https://gigachat.devices.sberbank.ru/api/v1//chat/completions"
        const val AUTH_URL = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth"
    }

    private var accessToken: String? = null
    private var tokenExpiresAt: Long = 0

    /**
     * Получает access token для GigaChat API.
     * Использует кеширование для избежания повторных запросов.
     */
    private suspend fun getAccessToken(): String {
        val currentTime = System.currentTimeMillis()
        if (accessToken != null && currentTime < tokenExpiresAt) return accessToken!!

        logger.info("Obtaining new access token from GigaChat")
        val response: HttpResponse = httpClient.post(AUTH_URL) {
            headers {
                append(HttpHeaders.Authorization, "Basic $apiKey")
                append(HttpHeaders.ContentType, "application/x-www-form-urlencoded")
                append("RqUID", UUID.randomUUID().toString())
            }
            setBody("scope=GIGACHAT_API_PERS")
        }

        if (response.status.isSuccess()) {
            val tokenResponse: OAuthTokenResponse = response.body()
            accessToken = tokenResponse.access_token
            tokenExpiresAt = tokenResponse.expires_at
            logger.info("Successfully obtained access token")
            return accessToken!!
        } else {
            val errorBody = response.bodyAsText()
            logger.error("Failed to obtain access token: ${response.status} - $errorBody")
            throw Exception("Failed to obtain access token: ${response.status}")
        }
    }

    /**
     * Выполняет запрос к GigaChat API
     */
    override suspend fun executeApiRequest(context: RequestContext): StandardApiResponse {
        val token = getAccessToken()

        // Конвертируем ChatMessage в GigaChatMessage для GigaChat API
        val systemMessage = GigaChatMessage(role = "system", content = context.systemPrompt)
        val historyMessages = context.messageHistory.map { msg ->
            GigaChatMessage(role = msg.role, content = msg.content)
        }

        val request = GigaChatRequest(
            model = "GigaChat",
            messages = listOf(systemMessage) + historyMessages,
            temperature = context.temperature,
            max_tokens = context.maxTokens
        )

        println("Sending POST request to: $BASE_URL\n$request")
        val response: HttpResponse = httpClient.post(BASE_URL) {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.ContentType, "application/json")
            setBody(request)
        }

        println("Status: ${response.status}")
        val body = response.body<GigaChatResponse>()
        println(body)

        return StandardApiResponse(
            answer = body.choices.first().message.content,
            promptTokens = body.usage.prompt_tokens,
            completionTokens = body.usage.completion_tokens,
            totalTokens = body.usage.total_tokens
        )
    }

    /**
     * Рассчитывает стоимость запроса по тарифам GigaChat
     * Стоимость: 1500 рублей за 1 млн токенов
     */
    override fun calculateCost(totalTokens: Int): Double {
        return BigDecimal(1500.0 / 1000000.0 * totalTokens)
            .setScale(2, RoundingMode.HALF_UP)
            .toDouble()
    }
}
