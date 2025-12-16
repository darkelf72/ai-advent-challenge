package mcp_server

import kotlinx.serialization.Serializable

@Serializable
data class TextInput(
    val text: String
)