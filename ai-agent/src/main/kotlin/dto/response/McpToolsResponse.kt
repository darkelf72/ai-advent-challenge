package dto.response

import kotlinx.serialization.Serializable

@Serializable
data class McpToolsResponse(
    val tools: List<McpToolDto>
)

@Serializable
data class McpToolDto(
    val name: String,
    val description: String,
    val inputSchema: String // JSON string
)
