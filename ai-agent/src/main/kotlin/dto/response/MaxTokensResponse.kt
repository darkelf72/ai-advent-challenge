package dto.response

import kotlinx.serialization.Serializable

@Serializable
data class MaxTokensResponse(val maxTokens: Int)
