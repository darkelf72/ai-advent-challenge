package dto
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
    val text: String
)