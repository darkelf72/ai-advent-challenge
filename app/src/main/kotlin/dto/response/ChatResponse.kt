package dto.response

import kotlinx.serialization.Serializable

@Serializable
data class ChatResponse(val question: String, val answer: String, val result: String)
