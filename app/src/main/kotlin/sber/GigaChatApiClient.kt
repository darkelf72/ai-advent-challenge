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
import org.koin.core.qualifier.named
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

                logger.info("Processing function_call: ${functionCall.name}")
                val functionResult = processFunctionCall(functionCall)

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
     * Обрабатывает function_call и возвращает результат выполнения
     */
    private suspend fun processFunctionCall(functionCall: FunctionCall): String {
        return try {
            logger.info("Executing function: ${functionCall.name}")

            val result = when (functionCall.name) {
                "weather_in_city" -> execWeatherInCityTool(functionCall.arguments!!)
                "save_weather_to_db" -> execSaveWeatherToDbTool(functionCall.arguments!!)
                else -> {
                    logger.error("Unknown function call: ${functionCall.name}")
                    """{"error": "Неизвестная функция: ${functionCall.name}"}"""
                }
            }

            logger.info("Function ${functionCall.name} executed successfully")
            result
        } catch (e: Exception) {
            logger.error("Error processing function_call: ${functionCall.name}", e)
            """{"error": "Ошибка при выполнении функции ${functionCall.name}: ${e.message}"}"""
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

    private suspend fun getTools(): List<Tool> {
        val dbMcpClient = get<Client>(Client::class.java, named("dbMcpClient"))
        val httpMcpClient = get<Client>(Client::class.java, named("httpMcpClient"))
        val tools = dbMcpClient.listTools().tools + httpMcpClient.listTools().tools
        return tools.map {
            Tool(
                name = it.name,
                description = it.description!!,
                parameters = it.inputSchema.properties
            )
        }
    }

    private suspend fun execWeatherInCityTool(arguments: JsonObject): String {
        return try {
            val mcpClient = get<Client>(Client::class.java, named("httpMcpClient"))
            logger.info("Calling MCP server for weather_in_city with arguments: $arguments")

            val request = CallToolRequest(CallToolRequestParams("weather_in_city", arguments))
            val result = mcpClient.callTool(request)

            // Извлекаем text из первого TextContent в result.content
            val weatherData = when (val firstContent = result.content.firstOrNull()) {
                is TextContent -> firstContent.text
                else -> "Нет данных о погоде"
            }

            logger.info("Weather data received from MCP server: $weatherData")

            // Возвращаем результат как JSON строку
            """{"weather_data": "${weatherData.replace("\"", "\\\"").replace("\n", "\\n")}"}"""
        } catch (e: Exception) {
            logger.error("Error calling MCP server for weather_in_city", e)
            """{"error": "Ошибка при вызове MCP сервера: ${e.message}"}"""
        }
    }

    private suspend fun execSaveWeatherToDbTool(arguments: JsonObject): String {
        return try {
            val mcpClient = get<Client>(Client::class.java, named("dbMcpClient"))
            logger.info("Calling MCP server for save_weather_to_db with arguments: $arguments")

            val request = CallToolRequest(CallToolRequestParams("save_weather_to_db", arguments))
            val result = mcpClient.callTool(request)

            // Извлекаем text из первого TextContent в result.content
            val saveResult = when (val firstContent = result.content.firstOrNull()) {
                is TextContent -> firstContent.text
                else -> """{"error": "No data returned", "status": "error"}"""
            }

            logger.info("Save weather result from MCP server: $saveResult")

            // Возвращаем результат как есть (уже JSON)
            saveResult
        } catch (e: Exception) {
            logger.error("Error calling MCP server for save_weather_to_db", e)
            """{"error": "Ошибка при вызове MCP сервера: ${e.message}", "status": "error"}"""
        }
    }
}
