package sber.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class GigaChatMessage(
    val role: String,
    val content: String,
    val function_call: FunctionCall? = null,
    val functions_state_id: String? = null,
    val name: String? = null
)

@Serializable
data class FunctionCall(
    val name: String,
    val arguments: JsonObject?,
)

@Serializable
data class GigaChatRequest(
    val model: String,
    val messages: List<GigaChatMessage>,
    val temperature: Double = 0.7,
    val top_p: Double = 0.9,
    val max_tokens: Int = 1024,
    val function_call: String =  "auto",
    val functions: List<Tool>
)

@Serializable
data class GigaChatChoice(
    val message: GigaChatMessage,
    val index: Int,
    val finish_reason: String
)

@Serializable
data class GigaChatResponse(
    val choices: List<GigaChatChoice>,
    val created: Long,
    val model: String,
    val `object`: String,
    val usage: Usage
)

@Serializable
data class Usage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int,
    val precached_prompt_tokens: Int
)

@Serializable
data class OAuthTokenResponse(
    val access_token: String,
    val expires_at: Long
)

@Serializable
data class Tool(
    val name: String,
    val description: String,
    val parameters: JsonElement?
)