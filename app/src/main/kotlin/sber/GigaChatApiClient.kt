package sber

import BaseApiClient
import RequestContext
import StandardApiResponse
import config.ApiClientConfig
import database.repository.ClientConfigRepository
import database.repository.MessageHistoryRepository
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.JsonObject
import org.koin.java.KoinJavaComponent.get
import org.slf4j.LoggerFactory
import sber.dto.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*

/**
 * API клиент для GigaChat (Сбер).
 * Наследует общую логику от BaseApiClient и реализует специфику GigaChat API.
 * Включает логику OAuth аутентификации с кешированием токена.
 */
class GigaChatApiClient(
    httpClient: HttpClient,
    apiClientConfig: ApiClientConfig,
    clientName: String,
    configRepository: ClientConfigRepository,
    messageHistoryRepository: MessageHistoryRepository
) : BaseApiClient(httpClient, apiClientConfig, clientName, configRepository, messageHistoryRepository) {

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
            max_tokens = context.maxTokens,
            functions = getTools()
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

        // Обработка function_call
        if (body.choices.first().finish_reason == "function_call") {
            val functionCall = body.choices.first().message.function_call
            if (functionCall?.name == "city_in_uppercase") {
                logger.info("Executing function call: ${functionCall.name}")
                // Выполняем функцию и получаем результат
                val functionResult = execReverseWordTool(functionCall.arguments!!)
                logger.info("Function result: $functionResult")

                // Создаем список сообщений для повторного запроса:
                // система + история + сообщение с function_call + результат функции
                val messagesWithFunctionResult = listOf(systemMessage) +
                    historyMessages +
                    body.choices.first().message + // сообщение с function_call
                    GigaChatMessage(
                        role = "function",
                        content = "{\"city\": \"$functionResult\"}",
                        name = "city_in_uppercase"
                    )

                // Создаем новый запрос с результатом функции
                val secondRequest = GigaChatRequest(
                    model = "GigaChat",
                    messages = messagesWithFunctionResult,
                    temperature = context.temperature,
                    max_tokens = context.maxTokens,
                    functions = getTools()
                )

                println("Sending POST request to: $BASE_URL\n$secondRequest")
                val secondResponse: HttpResponse = httpClient.post(BASE_URL) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header(HttpHeaders.ContentType, "application/json")
                    setBody(secondRequest)
                }

                println("Second request status: ${secondResponse.status}")
                val finalBody = secondResponse.body<GigaChatResponse>()
                println("Final response: $finalBody")

                // Возвращаем финальный ответ
                return StandardApiResponse(
                    answer = finalBody.choices.first().message.content,
                    promptTokens = body.usage.prompt_tokens + finalBody.usage.prompt_tokens,
                    completionTokens = body.usage.completion_tokens + finalBody.usage.completion_tokens,
                    totalTokens = body.usage.total_tokens + finalBody.usage.total_tokens
                )
            }
        }

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

    private suspend fun getTools(): List<Tool> {
        val mcpClient = get<Client>(Client::class.java)
        val toolsResponse = mcpClient.listTools()
        val tools = toolsResponse.tools
        return tools.map {
            Tool(
                name = it.name,
                description = it.description!!,
                parameters = it.inputSchema.properties
            )
        }

    }

    private suspend fun execReverseWordTool(text: JsonObject): String {
        val mcpClient = get<Client>(Client::class.java)
        println("✓ Successfully connected to MCP server")

        val request = CallToolRequest(CallToolRequestParams("city_in_uppercase", text))
        val result = mcpClient.callTool(request)

        // Извлекаем text из первого TextContent в result.content
        return when (val firstContent = result.content.firstOrNull()) {
            is TextContent -> firstContent.text
            else -> ""
        }
    }
}
