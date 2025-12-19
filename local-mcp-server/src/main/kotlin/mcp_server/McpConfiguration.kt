package mcp_server

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Configures the MCP (Model Context Protocol) server with tools for managing Docker containers.
 */
fun Application.configureMcpServer() {
    val logger = LoggerFactory.getLogger("McpConfiguration")
    val dockerService = DockerService()

    routing {
        // Health check endpoint
        get("/health") {
            call.respondText("Local MCP Server is running on port $MCP_SERVER_PORT")
        }

        // MCP endpoint
        mcp {
            Server(
                serverInfo = Implementation(
                    name = "local-mcp-server",
                    version = "1.0.0"
                ),
                options = ServerOptions(
                    capabilities = ServerCapabilities(
                        tools = ServerCapabilities.Tools()
                    )
                )
            ).apply {
                // Tool: run_mcp_servers
                addTool(
                    name = "run_mcp_servers",
                    description = "Запускает MCP серверы в Docker контейнерах",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                // No parameters needed
                            }
                        }
                    )
                ) { _: CallToolRequest ->
                    try {
                        logger.info("Executing run_mcp_servers tool")

                        val result = dockerService.startMcpServers()

                        if (result.success) {
                            logger.info("MCP servers started successfully")
                            val responseJson = buildJsonObject {
                                put("status", "success")
                                put("message", result.message)
                                putJsonArray("servers") {
                                    result.servers.forEach { server ->
                                        addJsonObject {
                                            put("name", server.name)
                                            put("status", server.status)
                                            put("url", server.url)
                                            putJsonArray("tools") {
                                                server.tools.forEach { tool ->
                                                    add(JsonPrimitive(tool))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            CallToolResult(
                                content = listOf(TextContent(responseJson.toString()))
                            )
                        } else {
                            logger.error("Failed to start MCP servers: ${result.message}")
                            CallToolResult(
                                content = listOf(TextContent("""{"status": "error", "message": "${result.message}"}"""))
                            )
                        }
                    } catch (e: Exception) {
                        logger.error("Error executing run_mcp_servers tool", e)
                        CallToolResult(
                            content = listOf(TextContent("""{"status": "error", "message": "${e.message}"}"""))
                        )
                    }
                }

                // Tool: stop_mcp_servers
                addTool(
                    name = "stop_mcp_servers",
                    description = "Останавливает MCP серверы в Docker контейнерах",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                // No parameters needed
                            }
                        }
                    )
                ) { _: CallToolRequest ->
                    try {
                        logger.info("Executing stop_mcp_servers tool")

                        val result = dockerService.stopMcpServers()

                        if (result.success) {
                            logger.info("MCP servers stopped successfully")
                            val responseJson = buildJsonObject {
                                put("status", "success")
                                put("message", result.message)
                                putJsonArray("servers") {
                                    result.servers.forEach { server ->
                                        addJsonObject {
                                            put("name", server.name)
                                            put("status", server.status)
                                            put("url", server.url)
                                            putJsonArray("tools") {
                                                server.tools.forEach { tool ->
                                                    add(JsonPrimitive(tool))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            CallToolResult(
                                content = listOf(TextContent(responseJson.toString()))
                            )
                        } else {
                            logger.error("Failed to stop MCP servers: ${result.message}")
                            CallToolResult(
                                content = listOf(TextContent("""{"status": "error", "message": "${result.message}"}"""))
                            )
                        }
                    } catch (e: Exception) {
                        logger.error("Error executing stop_mcp_servers tool", e)
                        CallToolResult(
                            content = listOf(TextContent("""{"status": "error", "message": "${e.message}"}"""))
                        )
                    }
                }
            }
        }
    }
}
