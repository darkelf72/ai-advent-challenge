package config

import kotlinx.serialization.Serializable

/**
 * Конфигурация API клиента с валидацией параметров
 */
@Serializable
data class ApiClientConfig(
    val systemPrompt: String,
    val temperature: Double,
    val maxTokens: Int,
    val autoSummarizeThreshold: Int = 0,
) {
    init {
        require(temperature in 0.0..1.0) {
            "Temperature must be in range [0.0, 1.0], but was $temperature"
        }
        require(maxTokens in 1..10000) {
            "MaxTokens must be in range [1, 10000], but was $maxTokens"
        }
        require(autoSummarizeThreshold in 0..20) {
            "AutoSummarizeThreshold must be in range [0, 20], but was $autoSummarizeThreshold"
        }
    }
}
