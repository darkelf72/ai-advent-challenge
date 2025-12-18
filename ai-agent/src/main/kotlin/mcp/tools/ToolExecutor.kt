package mcp.tools

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Интерфейс для исполнителя MCP инструмента.
 * Каждый инструмент реализует этот интерфейс и регистрируется в ToolRegistry.
 */
interface ToolExecutor {
    /**
     * Имя инструмента, которое используется для вызова
     */
    val toolName: String

    /**
     * Описание инструмента для генеративной модели
     */
    val description: String

    /**
     * JSON Schema параметров инструмента.
     * ВАЖНО: Должно содержать объект с полем "properties",
     * БЕЗ внешних "type" и "required" полей.
     *
     * Пример корректного формата для GigaChat API:
     * {
     *   "properties": {
     *     "field1": { "type": "string", "description": "..." },
     *     "field2": { "type": "number", "description": "..." }
     *   }
     * }
     */
    val parameters: JsonElement?

    /**
     * Имя MCP клиента, который предоставляет этот инструмент
     */
    val mcpClientName: String

    /**
     * Выполняет инструмент с переданными аргументами
     * @param arguments JSON объект с аргументами инструмента
     * @return JSON строка с результатом выполнения
     */
    suspend fun execute(arguments: JsonObject): String
}
