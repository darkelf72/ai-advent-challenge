package yandex.dto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RequestDto(
    val modelUri: String,
    val completionOptions: CompletionOptionsDto,
    val messages: List<MessageDto>,
    @SerialName("json_object")
    val jsonObject: Boolean = true
)

@Serializable
data class CompletionOptionsDto(
    val stream: Boolean,
    val temperature: Double,
    val maxTokens: Int
)

@Serializable
data class MessageDto(
    val role: String,
    var text: String
)

@Serializable
data class ResponseDto(
    val result: ResultDto
)

@Serializable
data class ResultDto(
    val alternatives: List<AlternativeDto>,
    val usage: UsageDto,
    val modelVersion: String
)

@Serializable
data class AlternativeDto(
    val message: MessageDto,
    val status: String
)

@Serializable
data class UsageDto(
    val inputTextTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val completionTokensDetails: CompletionTokensDetailsDto
)

@Serializable
data class CompletionTokensDetailsDto(
    val reasoningTokens: String
)