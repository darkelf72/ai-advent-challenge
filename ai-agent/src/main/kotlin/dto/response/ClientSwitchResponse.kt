package dto.response

import kotlinx.serialization.Serializable

@Serializable
data class ClientSwitchResponse(
    val clientName: String,
    val systemPrompt: String,
    val temperature: Double
)
