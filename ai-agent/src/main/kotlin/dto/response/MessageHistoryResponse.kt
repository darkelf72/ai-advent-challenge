package dto.response

import kotlinx.serialization.Serializable

@Serializable
data class MessageHistoryResponse(val messages: List<Map<String, String>>)
