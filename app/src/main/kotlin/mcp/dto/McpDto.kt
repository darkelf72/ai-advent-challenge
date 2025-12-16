package mcp.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * JSON-RPC 2.0 Request
 */
@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: Int,
    val method: String,
    val params: JsonElement? = null
)

/**
 * JSON-RPC 2.0 Response wrapper
 */
@Serializable
data class JsonRpcResponse<T>(
    val jsonrpc: String,
    val id: Int,
    val result: T? = null,
    val error: JsonRpcError? = null
)

/**
 * JSON-RPC 2.0 Error structure
 */
@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
)

/**
 * Initialize request parameters
 */
@Serializable
data class InitializeParams(
    val protocolVersion: String,
    val capabilities: ClientCapabilities,
    val clientInfo: ClientInfo
)

/**
 * Client capabilities
 */
@Serializable
data class ClientCapabilities(
    val experimental: Map<String, JsonElement>? = null,
    val sampling: Map<String, JsonElement>? = null
)

/**
 * Client information
 */
@Serializable
data class ClientInfo(
    val name: String,
    val version: String
)

/**
 * Initialize response result
 */
@Serializable
data class InitializeResult(
    val protocolVersion: String,
    val capabilities: ServerCapabilities,
    val serverInfo: ServerInfo
)

/**
 * Server capabilities
 */
@Serializable
data class ServerCapabilities(
    val logging: Map<String, JsonElement>? = null,
    val prompts: PromptCapability? = null,
    val resources: ResourceCapability? = null,
    val tools: ToolCapability? = null
)

/**
 * Prompt capability
 */
@Serializable
data class PromptCapability(
    val listChanged: Boolean? = null
)

/**
 * Resource capability
 */
@Serializable
data class ResourceCapability(
    val subscribe: Boolean? = null,
    val listChanged: Boolean? = null
)

/**
 * Tool capability
 */
@Serializable
data class ToolCapability(
    val listChanged: Boolean? = null
)

/**
 * Server information
 */
@Serializable
data class ServerInfo(
    val name: String,
    val version: String
)

/**
 * Tools/list request parameters
 */
@Serializable
data class ToolsListParams(
    val cursor: String? = null
)

/**
 * Tools/list response result
 */
@Serializable
data class ToolsListResult(
    val tools: List<Tool>,
    val nextCursor: String? = null
)

/**
 * Tool definition
 */
@Serializable
data class Tool(
    val name: String,
    val description: String? = null,
    val inputSchema: InputSchema
)

/**
 * Tool input schema (JSON Schema)
 */
@Serializable
data class InputSchema(
    val type: String,
    val properties: Map<String, PropertySchema>? = null,
    val required: List<String>? = null
)

/**
 * Property schema for tool input
 */
@Serializable
data class PropertySchema(
    val type: String,
    val description: String? = null,
    val enum: List<String>? = null
)
