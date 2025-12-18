package mcp.tools

import io.modelcontextprotocol.kotlin.sdk.client.Client
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Исполнитель инструмента weather_in_city.
 * Получает данные о погоде для заданных координат через HTTP MCP сервер.
 */
class WeatherInCityExecutor(
    mcpClient: Client
) : BaseToolExecutor(mcpClient) {

    override val toolName: String = "weather_in_city"

    override val description: String = "Возвращает текущую погоду для заданных координат города"

    override val mcpClientName: String = "httpMcpClient"

    override val parameters: JsonElement = buildJsonObject {
        // GigaChat API ожидает объект с полем "properties", но без "type" и "required"
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
    }
}
