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
    val weatherDataService = WeatherDataService()

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

                addTool(
                    name = "get_data_from_db",
                    description = "Получает сводную информацию из базы данных mcp_data по указанной таблице за определенный период времени",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("tableName") {
                                    put("type", "string")
                                    put("description", "Название таблицы (например: weather)")
                                }
                                putJsonObject("startTime") {
                                    put("type", "string")
                                    put("description", "Начало периода в ISO 8601 формате (например: 2025-12-18T10:00:00)")
                                }
                                putJsonObject("endTime") {
                                    put("type", "string")
                                    put("description", "Конец периода в ISO 8601 формате (например: 2025-12-18T11:00:00)")
                                }
                            }
                            putJsonArray("required") {
                                add(JsonPrimitive("tableName"))
                                add(JsonPrimitive("startTime"))
                                add(JsonPrimitive("endTime"))
                            }
                        }
                    )
                ) { arguments: CallToolRequest ->
                    try {
                        val tableName = arguments.arguments
                            ?.get("tableName")
                            ?.jsonPrimitive
                            ?.content
                            ?: "weather"

                        val startTime = arguments.arguments
                            ?.get("startTime")
                            ?.jsonPrimitive
                            ?.content
                            ?: ""

                        val endTime = arguments.arguments
                            ?.get("endTime")
                            ?.jsonPrimitive
                            ?.content
                            ?: ""

                        logger.info("Executing get_data_from_db tool for table: $tableName, period: $startTime to $endTime")

                        val result = weatherDataService.getWeatherData(tableName, startTime, endTime)

                        CallToolResult(
                            content = listOf(TextContent(result))
                        )
                    } catch (e: Exception) {
                        logger.error("Error executing get_data_from_db tool", e)
                        CallToolResult(
                            content = listOf(TextContent("[]"))
                        )
                    }
                }

                addTool(
                    name = "save_weather_to_db",
                    description = "Сохраняет детализированную информацию о погоде в базу данных weather_in_city",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("date_time") {
                                    put("type", "string")
                                    put("description", "Дата и время измерения в ISO 8601 формате (например: 2025-12-18T10:00:00)")
                                }
                                putJsonObject("city_name") {
                                    put("type", "string")
                                    put("description", "Название города")
                                }
                                putJsonObject("temperature") {
                                    put("type", "number")
                                    put("description", "Температура в градусах Цельсия")
                                }
                                putJsonObject("wind_speed") {
                                    put("type", "number")
                                    put("description", "Скорость ветра в м/с")
                                }
                                putJsonObject("wind_direction") {
                                    put("type", "integer")
                                    put("description", "Направление ветра в градусах (0-360)")
                                }
                            }
                            putJsonArray("required") {
                                add(JsonPrimitive("date_time"))
                                add(JsonPrimitive("city_name"))
                                add(JsonPrimitive("temperature"))
                                add(JsonPrimitive("wind_speed"))
                                add(JsonPrimitive("wind_direction"))
                            }
                        }
                    )
                ) { arguments: CallToolRequest ->
                    try {
                        val dateTime = arguments.arguments
                            ?.get("date_time")
                            ?.jsonPrimitive
                            ?.content
                            ?: ""

                        val cityName = arguments.arguments
                            ?.get("city_name")
                            ?.jsonPrimitive
                            ?.content
                            ?: ""

                        val temperature = arguments.arguments
                            ?.get("temperature")
                            ?.jsonPrimitive
                            ?.content
                            ?.toDoubleOrNull()
                            ?: 0.0

                        val windSpeed = arguments.arguments
                            ?.get("wind_speed")
                            ?.jsonPrimitive
                            ?.content
                            ?.toDoubleOrNull()
                            ?: 0.0

                        val windDirection = arguments.arguments
                            ?.get("wind_direction")
                            ?.jsonPrimitive
                            ?.content
                            ?.toIntOrNull()
                            ?: 0

                        logger.info("Executing save_weather_to_db tool for city: $cityName, date: $dateTime")

                        val recordId = weatherDataService.saveWeatherInCity(
                            dateTime = dateTime,
                            cityName = cityName,
                            temperature = temperature,
                            windSpeed = windSpeed,
                            windDirection = windDirection
                        )

                        if (recordId != null) {
                            val result = """{"id": $recordId, "status": "success"}"""
                            logger.info("Weather data saved successfully with ID: $recordId")
                            CallToolResult(
                                content = listOf(TextContent(result))
                            )
                        } else {
                            logger.error("Failed to save weather data")
                            CallToolResult(
                                content = listOf(TextContent("""{"error": "Failed to save weather data", "status": "error"}"""))
                            )
                        }
                    } catch (e: Exception) {
                        logger.error("Error executing save_weather_to_db tool", e)
                        CallToolResult(
                            content = listOf(TextContent("""{"error": "${e.message}", "status": "error"}"""))
                        )
                    }
                }
            }
        }
    }
}
