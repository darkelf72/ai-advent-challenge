package apiclients.gigachat

import apiclients.BaseApiClient
import apiclients.RequestContext
import apiclients.StandardApiResponse
import apiclients.config.ApiClientConfig
import apiclients.gigachat.dto.*
import database.repository.ClientConfigRepository
import database.repository.MessageHistoryRepository
import embedding.rag.RagClient
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import mcp.McpToolsService
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*

/**
 * API клиент для GigaChat (Сбер).
 * Наследует общую логику от BaseApiClient и реализует специфику GigaChat API.
 * Включает логику OAuth аутентификации с кешированием токена.
 * Динамически получает список tools из MCP серверов при каждом запросе через McpToolsService.
 */
class GigaChatApiClient(
    httpClient: HttpClient,
    apiClientConfig: ApiClientConfig,
    clientName: String,
    configRepository: ClientConfigRepository,
    messageHistoryRepository: MessageHistoryRepository,
    private val mcpToolsService: McpToolsService,
    ragClient: RagClient? = null
) : BaseApiClient(httpClient, apiClientConfig, clientName, configRepository, messageHistoryRepository, ragClient) {

    private val logger = LoggerFactory.getLogger(GigaChatApiClient::class.java)

    private companion object {
        val apiKey: String = System.getenv("GIGA_CHAT_API_KEY")
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
        try {
            logger.info("Starting API request execution")

            val token = getAccessToken()
            val systemMessage = GigaChatMessage(role = "system", content = context.systemPrompt)
            val historyMessages = context.messageHistory.map { msg ->
                GigaChatMessage(role = msg.role, content = msg.content)
            }

            var currentMessages = listOf(systemMessage) + historyMessages
            var totalPromptTokens = 0
            var totalCompletionTokens = 0
            var totalTokensCount = 0
            val maxIterations = 10 // Защита от бесконечного цикла
            var iteration = 0

            while (iteration < maxIterations) {
                iteration++
                logger.info("Request iteration: $iteration")

                val request = GigaChatRequest(
                    model = "GigaChat",
                    messages = currentMessages,
                    temperature = context.temperature,
                    max_tokens = context.maxTokens,
                    functions = getTools()
                )

                val response = sendGigaChatRequest(token, request)

                // Накапливаем токены
                totalPromptTokens += response.usage.prompt_tokens
                totalCompletionTokens += response.usage.completion_tokens
                totalTokensCount += response.usage.total_tokens

                val firstChoice = response.choices.firstOrNull()
                if (firstChoice == null) {
                    logger.error("No choices in API response")
                    return createErrorResponse("API вернул пустой ответ", totalPromptTokens, totalCompletionTokens, totalTokensCount)
                }

                // Если нет function_call - возвращаем финальный ответ
                if (firstChoice.finish_reason != "function_call") {
                    logger.info("Received final response without function_call")
                    return StandardApiResponse(
                        answer = firstChoice.message.content,
                        promptTokens = totalPromptTokens,
                        completionTokens = totalCompletionTokens,
                        totalTokens = totalTokensCount
                    )
                }

                // Обрабатываем function_call
                val functionCall = firstChoice.message.function_call
                if (functionCall?.name == null || functionCall.arguments == null) {
                    logger.error("Invalid function_call in response: $functionCall")
                    return createErrorResponse(
                        "Получен некорректный function_call",
                        totalPromptTokens,
                        totalCompletionTokens,
                        totalTokensCount
                    )
                }

                logger.info("Processing function_call: ${functionCall.name} with arguments${functionCall.arguments}")
                val functionResult = mcpToolsService.executeTool(functionCall.name, functionCall.arguments)

                // Формируем новый список сообщений с результатом функции
                currentMessages = buildMessagesWithFunctionResult(
                    previousMessages = currentMessages,
                    assistantMessage = firstChoice.message,
                    functionName = functionCall.name,
                    functionResult = functionResult
                )
            }

            logger.error("Maximum iterations ($maxIterations) reached")
            return createErrorResponse(
                "Превышено максимальное количество итераций обработки function_call",
                totalPromptTokens,
                totalCompletionTokens,
                totalTokensCount
            )
        } catch (e: Exception) {
            logger.error("Error executing API request", e)
            return createErrorResponse("Произошла ошибка при выполнении запроса: ${e.message}", 0, 0, 0)
        }
    }

    /**
     * Отправляет запрос к GigaChat API и возвращает ответ
     */
    private suspend fun sendGigaChatRequest(token: String, request: GigaChatRequest): GigaChatResponse {
        return try {
            logger.debug("Sending request to GigaChat API: $BASE_URL")
            println("Sending POST request to: $BASE_URL\n$request")

            val response: HttpResponse = httpClient.post(BASE_URL) {
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.ContentType, "application/json")
                setBody(request)
            }

            println("Status: ${response.status}")

            // Если ошибка - выводим тело ответа для отладки
            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                logger.error("GigaChat API error response: $errorBody")
                println("ERROR RESPONSE BODY: $errorBody")
                throw Exception("GigaChat API returned error ${response.status}: $errorBody")
            }

            val body = response.body<GigaChatResponse>()
            println(body)

            logger.debug("Received response from GigaChat API")
            body
        } catch (e: Exception) {
            logger.error("Error sending request to GigaChat API", e)
            throw e
        }
    }

    /**
     * Формирует список сообщений с результатом выполнения функции
     */
    private fun buildMessagesWithFunctionResult(
        previousMessages: List<GigaChatMessage>,
        assistantMessage: GigaChatMessage,
        functionName: String,
        functionResult: String
    ): List<GigaChatMessage> {
        logger.debug("Building messages with function result for: $functionName")
        val functionResultMessage = GigaChatMessage(
            role = "function",
            content = functionResult,
            name = functionName
        )
        return previousMessages + assistantMessage + functionResultMessage
    }

    /**
     * Создает ответ с ошибкой
     */
    private fun createErrorResponse(
        errorMessage: String,
        promptTokens: Int,
        completionTokens: Int,
        totalTokens: Int
    ): StandardApiResponse {
        logger.error("Creating error response: $errorMessage")
        return StandardApiResponse(
            answer = errorMessage,
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            totalTokens = totalTokens
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

    /**
     * Получает список доступных инструментов из MCP серверов через McpToolsService.
     * Преобразует формат MCP Tool в формат GigaChat Tool.
     * Запрашивает актуальный список tools при каждом вызове.
     */
    private suspend fun getTools(): List<GigaChatTool> =
        mcpToolsService.getAvailableTools().map {
            GigaChatTool(
                name = it.name,
                description = it.description ?: "No description",
                parameters = it.inputSchema.properties
            )
        }
}
