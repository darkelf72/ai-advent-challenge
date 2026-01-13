package dto.request

import kotlinx.serialization.Serializable

@Serializable
data class AutoSummarizeThresholdRequest(val threshold: Int)
