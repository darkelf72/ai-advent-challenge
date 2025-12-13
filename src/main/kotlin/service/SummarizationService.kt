package service

import ApiClientInterface
import SummarizeResponse
import dto.ChatMessage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Сервис для суммаризации истории диалога.
 * Инкапсулирует логику сжатия истории сообщений в системный промпт.
 */
class SummarizationService(
    private val summarizeApiClient: ApiClientInterface
) {
    private val json = Json { prettyPrint = true }

    /**
     * Выполняет суммаризацию истории сообщений для указанного клиента.
     *
     * @param targetClient клиент, историю которого нужно суммаризовать
     * @return результат суммаризации с метриками
     * @throws IllegalStateException если история сообщений пуста
     */
    suspend fun summarize(targetClient: ApiClientInterface): SummarizeResponse {
        // Проверяем, что история не пустая
        val messageHistory = targetClient.messageHistory
        if (messageHistory.isEmpty()) {
            throw IllegalStateException("История сообщений пуста. Нечего суммаризовать.")
        }

        // Формируем текст для суммаризации: системный промпт + история сообщений
        val summarizationText = listOf(ChatMessage("system", targetClient.config.systemPrompt))
            .plus(messageHistory)
            .let { json.encodeToString(mapOf("messages" to it)) }

        // Выполняем запрос к summarizeApiClient
        val summarizeResponse = summarizeApiClient.sendRequest(summarizationText)

        // Парсим метрики из результата
        val oldTokensCount = summarizeResponse.result!!.promptTokens
        val newTokensCount = summarizeResponse.result.completionTokens

        // Вычисляем процент сжатия
        val compressionPercent = if (oldTokensCount > 0) {
            ((oldTokensCount - newTokensCount).toDouble() / oldTokensCount * 100).toInt()
        } else {
            0
        }

        // Обновляем системный промпт целевого клиента
        targetClient.config = targetClient.config.copy(systemPrompt = summarizeResponse.message)

        // Очищаем историю сообщений
        targetClient.clearMessages()

        // Возвращаем результат суммаризации
        return SummarizeResponse(
            newSystemPrompt = summarizeResponse.message,
            oldMessagesCount = messageHistory.size,
            oldTokensCount = oldTokensCount,
            newTokensCount = newTokensCount,
            compressionPercent = compressionPercent
        )
    }
}
