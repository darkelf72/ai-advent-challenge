package dto

import kotlinx.serialization.Serializable

/**
 * Общий тип сообщения для всех API клиентов
 */
@Serializable
data class ChatMessage(
    val role: String,  // "system", "user", "assistant"
    val content: String
)
