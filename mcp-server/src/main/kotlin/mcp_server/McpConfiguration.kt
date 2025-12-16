import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.serialization.json.*

/**
 * Configures the MCP (Model Context Protocol) server with tools.
 */
fun Application.configureMcpServer() {
    routing {
        // Health check endpoint
        get("/health") {
            call.respondText("MCP Server is running on port $MCP_SERVER_PORT")
        }

        // MCP endpoint
        mcp {
            Server(
                serverInfo = Implementation(
                    name = "reverse-string-mcp-server",
                    version = "1.0.0"
                ),
                options = ServerOptions(
                    capabilities = ServerCapabilities(
                        tools = ServerCapabilities.Tools()
                    )
                )
            ).apply {
                addTool(
                    name = "reverse",
                    description = "Reverses the input string",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("text") {
                                    put("type", "string")
                                }
                            }
                            putJsonArray("required") {
                                add(JsonPrimitive("text"))
                            }
                        }
                    )
                ) { arguments: CallToolRequest ->
                    val text = arguments.arguments
                        ?.get("text")
                        ?.jsonPrimitive
                        ?.content
                        ?: ""

                    CallToolResult(
                        content = listOf(TextContent(text.reversed()))
                    )
                }
            }
        }
    }
}
