package mcp_server

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Configures the MCP (Model Context Protocol) server with tools.
 */
fun Application.configureMcpServer() {
    val logger = LoggerFactory.getLogger("McpConfiguration")
    val weatherService = WeatherService()

    routing {
        // Health check endpoint
        get("/health") {
            call.respondText("MCP Server is running on port $MCP_SERVER_PORT")
        }

        // MCP endpoint
        mcp {
            Server(
                serverInfo = Implementation(
                    name = "uppercase-city-mcp-server",
                    version = "1.0.0"
                ),
                options = ServerOptions(
                    capabilities = ServerCapabilities(
                        tools = ServerCapabilities.Tools()
                    )
                )
            ).apply {
//                addTool(
//                    name = "city_in_uppercase",
//                    description = "Возвращает названия городов в верхнем регистре",
//                    inputSchema = ToolSchema(
//                        buildJsonObject {
//                            put("type", "object")
//                            putJsonObject("properties") {
//                                putJsonObject("city") {
//                                    put("type", "string")
//                                    put("description", "Название города")
//                                }
//                            }
//                            putJsonArray("required") {
//                                add(JsonPrimitive("city"))
//                            }
//                        }
//                    )
//                ) { arguments: CallToolRequest ->
//                    val text = arguments.arguments
//                        ?.get("city")
//                        ?.jsonPrimitive
//                        ?.content
//                        ?: ""
//
//                    CallToolResult(
//                        content = listOf(TextContent(text.uppercase()))
//                    )
//                }

                addTool(
                    name = "weather_in_city",
                    description = "Возвращает текущую погоду для заданных координат города",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("latitude") {
                                    put("type", "number")
                                    put("description", "Широта города")
                                }
                                putJsonObject("longitude") {
                                    put("type", "number")
                                    put("description", "Долгота города")
                                }
                            }
                            putJsonArray("required") {
                                add(JsonPrimitive("latitude"))
                                add(JsonPrimitive("longitude"))
                            }
                        }
                    )
                ) { arguments: CallToolRequest ->
                    try {
                        val latitude = arguments.arguments
                            ?.get("latitude")
                            ?.jsonPrimitive
                            ?.content
                            ?.toDoubleOrNull()
                            ?: 0.0

                        val longitude = arguments.arguments
                            ?.get("longitude")
                            ?.jsonPrimitive
                            ?.content
                            ?.toDoubleOrNull()
                            ?: 0.0

                        logger.info("Executing weather_in_city tool for lat: $latitude, lon: $longitude")

                        val weatherResult = weatherService.getWeather(latitude, longitude)

                        CallToolResult(
                            content = listOf(TextContent(weatherResult))
                        )
                    } catch (e: Exception) {
                        logger.error("Error executing weather_in_city tool", e)
                        CallToolResult(
                            content = listOf(TextContent("Ошибка при выполнении запроса погоды: ${e.message}"))
                        )
                    }
                }
            }
        }
    }
}
