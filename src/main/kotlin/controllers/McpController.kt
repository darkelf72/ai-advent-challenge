package controllers

import dto.response.McpToolDto
import dto.response.McpToolsResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.serialization.json.Json
import mcp.McpClient
import mcp.McpException

/**
 * Controller for MCP (Model Context Protocol) operations
 */
class McpController(private val mcpClient: McpClient) {

    private val json = Json { prettyPrint = true }

    suspend fun handleGetMcpTools(call: ApplicationCall) {
        try {
            // Fetch all tools from MCP server
            val tools = mcpClient.listAllTools()

            // Convert to response format
            val toolDtos = tools.map { tool ->
                McpToolDto(
                    name = tool.name,
                    description = tool.description ?: "No description",
                    inputSchema = json.encodeToString(mcp.dto.InputSchema.serializer(), tool.inputSchema)
                )
            }

            call.respond(McpToolsResponse(tools = toolDtos))
        } catch (e: McpException) {
            println("MCP error: ${e.message}")
            call.respond(
                HttpStatusCode.BadGateway,
                mapOf("error" to "MCP server error: ${e.message}")
            )
        } catch (e: Exception) {
            println("Error fetching MCP tools: ${e.message}")
            e.printStackTrace()
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Failed to fetch MCP tools: ${e.message}")
            )
        }
    }
}
