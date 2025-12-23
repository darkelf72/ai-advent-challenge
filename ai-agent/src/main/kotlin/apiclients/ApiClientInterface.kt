package apiclients

import apiclients.config.ApiClientConfig
import dto.ApiResponse
import dto.ChatMessage

/**
 * Интерфейс API клиента для работы с языковыми моделями
 *
 * Использует config-подход для управления конфигурацией,
 * что обеспечивает атомарность изменений и централизованную валидацию
 */
interface ApiClientInterface {
    /**
     * Отправка запроса к API
     * @param query текст запроса пользователя
     * @param useRag использовать ли RAG (Retrieval-Augmented Generation)
     * @return ответ от API
     */
    fun sendRequest(query: String, useRag: Boolean = false): ApiResponse

    /**
     * Конфигурация клиента (иммутабельная)
     * При изменении создается новая копия с обновленными параметрами
     */
    var config: ApiClientConfig

    /**
     * История сообщений (read-only)
     * Содержит все сообщения из текущей сессии
     */
    val messageHistory: List<ChatMessage>

    /**
     * Очистка истории сообщений
     */
    fun clearMessages()
}
