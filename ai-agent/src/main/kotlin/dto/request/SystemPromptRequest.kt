package dto.request

import kotlinx.serialization.Serializable

@Serializable
data class SystemPromptRequest(val prompt: String)
