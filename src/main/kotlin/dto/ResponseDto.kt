package dto

import kotlinx.serialization.Serializable


@Serializable
data class TextJson(
    val message: String,
    val elapsedTime: Int,
    var tokens: Int? = null,
    var cost: Double? = null,
)