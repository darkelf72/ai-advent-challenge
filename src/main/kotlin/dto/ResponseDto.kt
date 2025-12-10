package dto

import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse(
    val message: String,
    val result: String
)

@Serializable
data class ApiResult(
    val elapsedTime: Long,
    val promptTokens: Int,
    val completionTokens: Int,
    var totalTokens: Int,
    var cost: Double
)