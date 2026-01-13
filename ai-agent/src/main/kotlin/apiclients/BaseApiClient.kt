package apiclients

import apiclients.config.ApiClientConfig
import database.repository.ClientConfigRepository
import database.repository.MessageHistoryRepository
import dto.ApiResponse
import dto.ApiResult
import dto.ChatMessage
import embedding.rag.RagClient
import io.ktor.client.*
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

/**
 * Контекст запроса, передаваемый в executeApiRequest
 */
data class RequestContext(
    val systemPrompt: String,
    val messageHistory: List<ChatMessage>,
    val temperature: Double,
    val maxTokens: Int
)

/**
 * Стандартизированный ответ от API
 */
data class StandardApiResponse(
    val answer: String,
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)

/**
 * Базовый абстрактный класс для API клиентов.
 * Реализует паттерн Template Method для инкапсуляции общей логики.
 *
 * Общая логика включает:
 * - Управление конфигурацией
 * - Управление историей сообщений
 * - Алгоритм выполнения запроса (добавление в историю, замер времени, расчет метрик)
 * - Обработку ошибок
 * - Распознавание code review запросов и загрузку специального system prompt
 *
 * Наследники должны реализовать специфику конкретного API:
 * - executeApiRequest: логика запроса к конкретному API
 * - calculateCost: формула расчета стоимости
 */
abstract class BaseApiClient(
    protected val httpClient: HttpClient,
    protected val apiClientConfig: ApiClientConfig,
    protected val clientName: String,
    private val configRepository: ClientConfigRepository,
    private val messageHistoryRepository: MessageHistoryRepository
) : ApiClientInterface {
    private val logger = LoggerFactory.getLogger(this::class.java)

    // Конфигурация клиента (общая для всех реализаций)
    private var _config: ApiClientConfig = apiClientConfig
    override var config: ApiClientConfig
        get() = _config
        set(value) {
            _config = value
            // Автоматически сохраняем изменения конфигурации в БД
            configRepository.saveConfig(clientName, value)
        }

    // История сообщений (общая для всех реализаций)
    private val _messageHistory = mutableListOf<ChatMessage>()
    override val messageHistory: List<ChatMessage>
        get() = _messageHistory.toList()

    init {
        // Загружаем данные из БД при инициализации
        loadFromDatabase()
    }

    /**
     * Загружает конфигурацию и историю сообщений из базы данных
     */
    private fun loadFromDatabase() {
        // Загрузка конфигурации
        val savedConfig = configRepository.loadConfig(clientName)
        if (savedConfig != null) {
            _config = savedConfig
        } else {
            // Если конфигурации нет в БД, сохраняем текущую
            configRepository.saveConfig(clientName, _config)
        }

        // Загрузка истории сообщений
        val savedMessages = messageHistoryRepository.loadMessages(clientName)
        _messageHistory.clear()
        _messageHistory.addAll(savedMessages)
    }

    /**
     * Сохраняет текущую историю сообщений в БД
     */
    private fun saveMessageHistory() {
        messageHistoryRepository.saveMessages(clientName, _messageHistory)
    }

    override fun clearMessages() {
        _messageHistory.clear()
        // Автоматически сохраняем изменения в БД
        messageHistoryRepository.clearMessages(clientName)
    }

    /**
     * Синхронная обертка над асинхронным запросом
     */
    override fun sendRequest(query: String): ApiResponse = runBlocking {
        sendRequestAsync(query)
    }

    /**
     * Template Method - определяет общий алгоритм выполнения запроса.
     * Вызывает abstract методы для специфичной логики конкретного API.
     */
    private suspend fun sendRequestAsync(userPrompt: String): ApiResponse {
        return try {
            // Шаг 1: Проверяем запрос на code review и загружаем специальный system prompt
            val isCodeReview = isCodeReviewRequest(userPrompt)
            val codeReviewSystemPrompt = if (isCodeReview) {
                logger.info("Code review request detected, loading code review system prompt")
                loadCodeReviewPrompt()
            } else {
                null
            }

            // Шаг 2: Добавляем сообщение пользователя в историю
            val userMessage = ChatMessage(role = "user", content = userPrompt)
            _messageHistory.add(userMessage)

            // Шаг 3: Подготавливаем контекст для запроса
            val requestContext = RequestContext(
                systemPrompt = codeReviewSystemPrompt ?: config.systemPrompt,
                messageHistory = _messageHistory,
                temperature = config.temperature,
                maxTokens = config.maxTokens
            )

            // Шаг 4: Выполняем API-специфичный запрос (делегируем наследникам)
            val startTime = System.currentTimeMillis()
            val apiResponse = executeApiRequest(requestContext)
            val executionTime = System.currentTimeMillis() - startTime

            // Шаг 5: Добавляем ответ ассистента в историю
            val assistantMessage = ChatMessage(role = "assistant", content = apiResponse.answer)
            _messageHistory.add(assistantMessage)

            // Сохраняем обновленную историю в БД
            saveMessageHistory()

            // Шаг 6: Формируем результат с метриками
            val apiResult = ApiResult(
                elapsedTime = executionTime,
                promptTokens = apiResponse.promptTokens,
                completionTokens = apiResponse.completionTokens,
                totalTokens = apiResponse.totalTokens,
                cost = calculateCost(apiResponse.totalTokens)
            )

            ApiResponse(message = apiResponse.answer, result = apiResult)
        } catch (e: Exception) {
            handleError(e)
        }
    }

    /**
     * Проверяет является ли запрос запросом на code review.
     * Распознает фразы вида: "сделай ревью", "провести ревью", "review", и наличие ссылки на github PR
     */
    private fun isCodeReviewRequest(prompt: String): Boolean {
        val lowerPrompt = prompt.lowercase()
        val hasReviewKeyword = lowerPrompt.contains("ревью") || lowerPrompt.contains("review")
        val hasGithubPrUrl = prompt.contains("github.com") && prompt.contains("/pull/")

        return hasReviewKeyword && hasGithubPrUrl
    }

    /**
     * Загружает системный промпт для code review из resources
     */
    private fun loadCodeReviewPrompt(): String {
        return try {
            val resource = this::class.java.classLoader.getResourceAsStream("code-review-prompt.txt")
            resource?.bufferedReader()?.use { it.readText() }
                ?: run {
                    logger.warn("Code review prompt file not found, using default")
                    "Ты - опытный code reviewer. Проанализируй Pull Request используя MCP tools."
                }
        } catch (e: Exception) {
            logger.error("Failed to load code review prompt", e)
            "Ты - опытный code reviewer. Проанализируй Pull Request используя MCP tools."
        }
    }

    /**
     * Выполнить запрос к конкретному API.
     * Каждая реализация должна:
     * - Конвертировать RequestContext в специфичные DTO
     * - Выполнить HTTP запрос
     * - Распарсить ответ и вернуть StandardApiResponse
     *
     * @param context контекст с настройками и историей сообщений
     * @return стандартизированный ответ
     */
    protected abstract suspend fun executeApiRequest(context: RequestContext): StandardApiResponse

    /**
     * Рассчитать стоимость запроса.
     * Каждый API имеет свою формулу расчета стоимости.
     *
     * @param totalTokens общее количество токенов
     * @return стоимость в рублях
     */
    protected abstract fun calculateCost(totalTokens: Int): Double

    /**
     * Обработка ошибок.
     * Может быть переопределена для кастомной логики обработки ошибок.
     *
     * @param e исключение
     * @return ответ с информацией об ошибке
     */
    protected open fun handleError(e: Exception): ApiResponse {
        return ApiResponse("Request failed: ${e.localizedMessage}")
    }
}
