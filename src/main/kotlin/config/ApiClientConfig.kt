package config

import kotlinx.serialization.Serializable

/**
 * Конфигурация API клиента с валидацией параметров
 */
@Serializable
data class ApiClientConfig(
    val systemPrompt: String = "Ты - генеративная языковая модель",
    val temperature: Double = 0.7,
    val maxTokens: Int = 100,
    val summaryEdge: Int? = null  // Опциональный параметр для управления суммаризацией
) {
    init {
        require(temperature in 0.0..1.0) {
            "Temperature must be in range [0.0, 1.0], but was $temperature"
        }
        require(maxTokens in 1..10000) {
            "MaxTokens must be in range [1, 10000], but was $maxTokens"
        }
        summaryEdge?.let { edge ->
            require(edge > 0) {
                "SummaryEdge must be positive, but was $edge"
            }
        }
    }
}
