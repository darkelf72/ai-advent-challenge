package mcp

import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * Сервис для работы с MCP tools.
 * Динамически получает список tools из MCP серверов и выполняет их.
 * Не хранит никакой информации о tools - все операции выполняются в реальном времени.
 */
class McpToolsService(
    private val mcpClientManager: McpClientManager
) {
    private val logger = LoggerFactory.getLogger(McpToolsService::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Получает список всех доступных tools из всех подключенных MCP серверов.
     * Возвращает только те tools, которые доступны в данный момент.
     */
    suspend fun getAvailableTools(): List<Tool> {
        val tools = mutableListOf<Tool>()

        // Получаем tools из DB MCP клиента
        mcpClientManager.getDbClient()?.let { client ->
            try {
                val dbTools = client.listTools(ListToolsRequest())
                tools.addAll(dbTools.tools)
                logger.debug("Retrieved ${dbTools.tools.size} tools from DB MCP client")
            } catch (e: Exception) {
                logger.warn("Failed to get tools from DB MCP client: ${e.message}")
            }
        }

        // Получаем tools из HTTP MCP клиента
        mcpClientManager.getHttpClient()?.let { client ->
            try {
                val httpTools = client.listTools(ListToolsRequest())
                tools.addAll(httpTools.tools)
                logger.debug("Retrieved ${httpTools.tools.size} tools from HTTP MCP client")
            } catch (e: Exception) {
                logger.warn("Failed to get tools from HTTP MCP client: ${e.message}")
            }
        }

        // Получаем tools из Local MCP клиента
        mcpClientManager.getLocalClient()?.let { client ->
            try {
                val localTools = client.listTools(ListToolsRequest())
                tools.addAll(localTools.tools)
                logger.debug("Retrieved ${localTools.tools.size} tools from Local MCP client")
            } catch (e: Exception) {
                logger.warn("Failed to get tools from Local MCP client: ${e.message}")
            }
        }

        // Получаем tools из Git MCP клиента
        mcpClientManager.getGitClient()?.let { client ->
            try {
                val gitTools = client.listTools(ListToolsRequest())
                tools.addAll(gitTools.tools)
                logger.debug("Retrieved ${gitTools.tools.size} tools from Git MCP client")
            } catch (e: Exception) {
                logger.warn("Failed to get tools from Git MCP client: ${e.message}")
            }
        }

        logger.info("Total available tools: ${tools.size}")
        return tools
    }

    /**
     * Выполняет tool с заданными аргументами.
     * Автоматически определяет в каком MCP сервере находится tool и выполняет его.
     *
     * @param toolName имя tool для выполнения
     * @param arguments аргументы для tool
     * @return результат выполнения в виде JSON строки
     */
    suspend fun executeTool(toolName: String, arguments: JsonObject): String {
        return try {
            logger.info("Executing tool: $toolName")

            // Пробуем найти и выполнить tool через DB MCP клиент
            mcpClientManager.getDbClient()?.let { client ->
                try {
                    val tools = client.listTools(ListToolsRequest())
                    if (tools.tools.any { it.name == toolName }) {
                        logger.debug("Tool $toolName found in DB MCP client")
                        val request = CallToolRequest(CallToolRequestParams(toolName, arguments))
                        val result = client.callTool(request)
                        return extractResultContent(result, toolName)
                    }
                } catch (e: Exception) {
                    logger.debug("Failed to execute $toolName via DB MCP client: ${e.message}")
                }
            }

            // Пробуем найти и выполнить tool через HTTP MCP клиент
            mcpClientManager.getHttpClient()?.let { client ->
                try {
                    val tools = client.listTools(ListToolsRequest())
                    if (tools.tools.any { it.name == toolName }) {
                        logger.debug("Tool $toolName found in HTTP MCP client")
                        val request = CallToolRequest(CallToolRequestParams(toolName, arguments))
                        val result = client.callTool(request)
                        return extractResultContent(result, toolName)
                    }
                } catch (e: Exception) {
                    logger.debug("Failed to execute $toolName via HTTP MCP client: ${e.message}")
                }
            }

            // Пробуем найти и выполнить tool через Local MCP клиент
            mcpClientManager.getLocalClient()?.let { client ->
                try {
                    val tools = client.listTools(ListToolsRequest())
                    if (tools.tools.any { it.name == toolName }) {
                        logger.debug("Tool $toolName found in Local MCP client")
                        val request = CallToolRequest(CallToolRequestParams(toolName, arguments))
                        val result = client.callTool(request)
                        return extractResultContent(result, toolName)
                    }
                } catch (e: Exception) {
                    logger.debug("Failed to execute $toolName via Local MCP client: ${e.message}")
                }
            }

            // Пробуем найти и выполнить tool через Git MCP клиент
            mcpClientManager.getGitClient()?.let { client ->
                try {
                    val tools = client.listTools(ListToolsRequest())
                    if (tools.tools.any { it.name == toolName }) {
                        logger.debug("Tool $toolName found in Git MCP client")
                        val request = CallToolRequest(CallToolRequestParams(toolName, arguments))
                        val result = client.callTool(request)
                        return extractResultContent(result, toolName)
                    }
                } catch (e: Exception) {
                    logger.debug("Failed to execute $toolName via Git MCP client: ${e.message}")
                }
            }

            // Tool не найден ни в одном клиенте
            logger.error("Tool '$toolName' not found in any connected MCP server")
            """{"error": "Unknown tool: $toolName", "status": "error"}"""
        } catch (e: Exception) {
            logger.error("Error executing tool $toolName", e)
            """{"error": "Failed to execute $toolName: ${e.message}", "status": "error"}"""
        }
    }

    /**
     * Извлекает текстовый результат из CallToolResult и форматирует его как JSON
     */
    private fun extractResultContent(result: CallToolResult, toolName: String): String {
        val content = when (val firstContent = result.content.firstOrNull()) {
            is TextContent -> firstContent.text
            else -> {
                logger.warn("No TextContent in response from $toolName")
                return """{"error": "No data returned from tool", "status": "error"}"""
            }
        }

        logger.info("Successfully executed $toolName")

        // Проверяем, является ли результат уже валидным JSON
        return if (isValidJson(content)) {
            logger.debug("Result is already valid JSON")
            content
        } else {
            logger.debug("Result is plain text, wrapping in JSON")
            wrapInJson(content)
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
     * Оборачивает plain text результат в JSON объект
     */
    private fun wrapInJson(text: String): String {
        val escapedText = text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return """{"result": "$escapedText"}"""
    }
}
