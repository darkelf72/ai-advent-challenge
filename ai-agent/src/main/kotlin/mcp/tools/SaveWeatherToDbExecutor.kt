package mcp.tools

import io.modelcontextprotocol.kotlin.sdk.client.Client
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Исполнитель инструмента save_weather_to_db.
 * Сохраняет детализированную информацию о погоде в базу данных через DB MCP сервер.
 */
class SaveWeatherToDbExecutor(
    mcpClient: Client
) : BaseToolExecutor(mcpClient) {

    override val toolName: String = "save_weather_to_db"

    override val description: String = "Сохраняет детализированную информацию о погоде в базу данных weather_in_city"

    override val mcpClientName: String = "dbMcpClient"

    override val parameters: JsonElement = buildJsonObject {
        // GigaChat API ожидает объект с полем "properties", но без "type" и "required"
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
    }
}
