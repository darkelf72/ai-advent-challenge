package mcp

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import mcp.dto.*

/**
 * MCP (Model Context Protocol) client for interacting with MCP servers.
 * Implements JSON-RPC 2.0 protocol over HTTP.
 */
class McpClient(
    private val httpClient: HttpClient
) {
    private companion object {
        const val MCP_URL = "https://mcp.context7.com/mcp"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    private var requestId = 0
    private var isInitialized = false

    /**
     * Initializes the MCP connection
     */
    suspend fun initialize(): InitializeResult {
        println("[MCP] Initializing connection to $MCP_URL")

        val params = InitializeParams(
            protocolVersion = "2024-11-05",
            capabilities = ClientCapabilities(),
            clientInfo = ClientInfo(
                name = "Kotlin-MCP-Client",
                version = "1.0.0"
            )
        )

        val request = JsonRpcRequest(
            id = ++requestId,
            method = "initialize",
            params = json.encodeToJsonElement(InitializeParams.serializer(), params)
        )

        val response = sendRequest<InitializeResult>(request)
        isInitialized = true

        println("[MCP] Initialization successful. Server: ${response.serverInfo.name} v${response.serverInfo.version}")
        return response
    }

    /**
     * Fetches the list of available tools.
     * Supports pagination via cursor.
     */
    suspend fun listTools(cursor: String? = null): ToolsListResult {
        if (!isInitialized) {
            println("[MCP] Not initialized, initializing now...")
            initialize()
        }

        println("[MCP] Fetching tools list" + if (cursor != null) " (cursor: $cursor)" else "")

        val params = if (cursor != null) {
            json.encodeToJsonElement(ToolsListParams.serializer(), ToolsListParams(cursor))
        } else {
            buildJsonObject {}
        }

        val request = JsonRpcRequest(
            id = ++requestId,
            method = "tools/list",
            params = params
        )

        val result = sendRequest<ToolsListResult>(request)
        println("[MCP] Received ${result.tools.size} tools" +
                if (result.nextCursor != null) " (has more pages)" else "")

        return result
    }

    /**
     * Fetches ALL tools, handling pagination automatically
     */
    suspend fun listAllTools(): List<Tool> {
        println("[MCP] Fetching all tools (with automatic pagination)")

        val allTools = mutableListOf<Tool>()
        var cursor: String? = null

        do {
            val result = listTools(cursor)
            allTools.addAll(result.tools)
            cursor = result.nextCursor
        } while (cursor != null)

        println("[MCP] Total tools fetched: ${allTools.size}")
        return allTools
    }

    /**
     * Internal method to send JSON-RPC requests
     */
    private suspend inline fun <reified T> sendRequest(request: JsonRpcRequest): T {
        println("[MCP] Sending request: ${request.method} (id=${request.id})")

        try {
            val response: HttpResponse = httpClient.post(MCP_URL) {
                header("Accept", "application/json, text/event-stream")
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            println("[MCP] Response status: ${response.status}")

            val bodyText = response.bodyAsText()

            val jsonRpcResponse = json.decodeFromString<JsonRpcResponse<T>>(bodyText)

            if (jsonRpcResponse.error != null) {
                throw McpException(
                    "MCP Error ${jsonRpcResponse.error.code}: ${jsonRpcResponse.error.message}",
                    jsonRpcResponse.error
                )
            }

            return jsonRpcResponse.result
                ?: throw McpException("MCP response missing result field", null)

        } catch (e: McpException) {
            throw e
        } catch (e: Exception) {
            println("[MCP] Error during request: ${e.message}")
            e.printStackTrace()
            throw McpException("Failed to send MCP request: ${e.message}", null)
        }
    }
}

/**
 * Custom exception for MCP errors
 */
class McpException(
    message: String,
    val error: JsonRpcError?
) : Exception(message)
