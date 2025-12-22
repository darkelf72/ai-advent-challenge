package dto

import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse(
    val message: String,
    val result: ApiResult? = null
)

@Serializable
data class ApiResult(
    val elapsedTime: Long,
    val promptTokens: Int,
    val completionTokens: Int,
    var totalTokens: Int,
    var cost: Double
)

/**
 * Generic response DTO for API endpoints
 */
@Serializable
data class ResponseDto(
    val success: Boolean,
    val message: String,
    val data: Map<String, @Serializable(with = AnySerializer::class) Any?>? = null
)

object AnySerializer : kotlinx.serialization.KSerializer<Any?> {
    override val descriptor = kotlinx.serialization.descriptors.buildClassSerialDescriptor("Any")

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: Any?) {
        when (value) {
            null -> encoder.encodeNull()
            is String -> encoder.encodeString(value)
            is Int -> encoder.encodeInt(value)
            is Long -> encoder.encodeLong(value)
            is Boolean -> encoder.encodeBoolean(value)
            is Double -> encoder.encodeDouble(value)
            is Float -> encoder.encodeFloat(value)
            else -> encoder.encodeString(value.toString())
        }
    }

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): Any? {
        return decoder.decodeString()
    }
}