package dto.request

import kotlinx.serialization.Serializable

@Serializable
data class MaxTokensRequest(val maxTokens: Int)
