package dto.request

import kotlinx.serialization.Serializable

@Serializable
data class ChatRequest(val message: String)
