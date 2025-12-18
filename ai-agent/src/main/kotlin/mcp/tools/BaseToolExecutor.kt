package mcp.tools

import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * Базовый класс для исполнителей MCP инструментов.
 * Содержит общую логику вызова MCP сервера и обработки ошибок.
 */
abstract class BaseToolExecutor(
    protected val mcpClient: Client
) : ToolExecutor {

    protected val logger = LoggerFactory.getLogger(this::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Выполняет вызов MCP инструмента через клиента.
     * Обрабатывает стандартные ошибки и извлечение результата.
     * Гарантирует, что результат всегда будет валидным JSON.
     */
    override suspend fun execute(arguments: JsonObject): String {
        return try {
            logger.info("Calling MCP server for $toolName with arguments: $arguments")

            val request = CallToolRequest(CallToolRequestParams(toolName, arguments))
            val result = mcpClient.callTool(request)

            // Извлекаем текстовое содержимое из результата
            val content = when (val firstContent = result.content.firstOrNull()) {
                is TextContent -> firstContent.text
                else -> {
                    logger.warn("No TextContent in response from $toolName")
                    return """{"error": "No data returned from tool", "status": "error"}"""
                }
            }

            logger.info("Successfully executed $toolName")

            // Проверяем, является ли результат уже валидным JSON
            val finalResult = if (isValidJson(content)) {
                // Уже JSON - возвращаем как есть
                logger.debug("Result is already valid JSON")
                content
            } else {
                // Не JSON - оборачиваем в JSON объект
                logger.debug("Result is plain text, wrapping in JSON")
                wrapInJson(content)
            }

            finalResult
        } catch (e: Exception) {
            logger.error("Error executing tool $toolName", e)
            """{"error": "Failed to execute $toolName: ${e.message}", "status": "error"}"""
        }
    }

    /**
     * Проверяет, является ли строка валидным JSON
     */
    private fun isValidJson(text: String): Boolean {
        return try {
            json.parseToJsonElement(text)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Оборачивает plain text результат в JSON объект.
     * Экранирует специальные символы для валидного JSON.
     */
    private fun wrapInJson(text: String): String {
        val escapedText = text
            .replace("\\", "\\\\")  // Экранируем обратный слэш
            .replace("\"", "\\\"")  // Экранируем кавычки
            .replace("\n", "\\n")   // Экранируем переводы строк
            .replace("\r", "\\r")   // Экранируем возврат каретки
            .replace("\t", "\\t")   // Экранируем табуляцию

        return """{"result": "$escapedText"}"""
    }
}
