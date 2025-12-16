package dto.response

import kotlinx.serialization.Serializable

@Serializable
data class SummarizeResponse(
    val newSystemPrompt: String,
    val oldMessagesCount: Int,
    val oldTokensCount: Int,
    val newTokensCount: Int,
    val compressionPercent: Int
)
