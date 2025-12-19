package mcp

import kotlinx.serialization.json.JsonObject
import mcp.tools.ToolExecutor
import org.slf4j.LoggerFactory

/**
 * Реестр MCP инструментов с поддержкой ленивой инициализации.
 * Управляет всеми доступными инструментами, кэширует их описания и предоставляет
 * единую точку доступа для выполнения инструментов.
 * Инструменты могут быть недоступны при создании реестра и загружаются при первом обращении.
 */
class ToolRegistry(
    private val executors: List<ToolExecutor>,
    private val mcpClientManager: McpClientManager
) {
    private val logger = LoggerFactory.getLogger(ToolRegistry::class.java)
    private val executorMap: Map<String, ToolExecutor>

    init {
        executorMap = executors.associateBy { it.toolName }
        logger.info("ToolRegistry created with ${executors.size} potential executors (lazy loading enabled)")
    }

    /**
     * Возвращает список всех доступных инструментов.
     * ВНИМАНИЕ: Может инициировать подключение к MCP серверам при первом вызове.
     */
    suspend fun getAllTools(): List<ToolExecutor> {
        ensureClientsConnected()
        return executors
    }

    /**
     * Возвращает инструмент по имени
     * @param toolName имя инструмента
     * @return исполнитель инструмента или null если не найден
     */
    suspend fun getToolByName(toolName: String): ToolExecutor? {
        ensureClientsConnected()
        return executorMap[toolName]
    }

    /**
     * Проверяет, существует ли инструмент с заданным именем
     */
    fun hasTool(toolName: String): Boolean {
        return executorMap.containsKey(toolName)
    }

    /**
     * Возвращает список имен всех доступных инструментов
     */
    fun getToolNames(): List<String> {
        return executorMap.keys.toList()
    }

    /**
     * Выполняет инструмент по имени с переданными аргументами.
     * Автоматически подключается к необходимым MCP серверам при первом вызове.
     * @param toolName имя инструмента
     * @param arguments JSON объект с аргументами
     * @return JSON строка с результатом выполнения или ошибкой
     */
    suspend fun executeTool(toolName: String, arguments: JsonObject): String {
        val executor = executorMap[toolName]
        if (executor == null) {
            logger.error("Tool '$toolName' not found in registry")
            return """{"error": "Unknown tool: $toolName", "status": "error"}"""
        }

        // Подключаемся к нужному MCP серверу перед выполнением
        val clientConnected = ensureClientConnected(executor.mcpClientName)
        if (!clientConnected) {
            logger.error("Failed to connect to MCP server for tool '$toolName'")
            return """{"error": "MCP server '${executor.mcpClientName}' is not available", "status": "error"}"""
        }

        logger.info("Executing tool '$toolName' via ToolRegistry")
        return try {
            val result = executor.execute(arguments)
            logger.debug("Tool '$toolName' executed successfully")
            result
        } catch (e: Exception) {
            logger.error("Error executing tool '$toolName'", e)
            """{"error": "Failed to execute $toolName: ${e.message}", "status": "error"}"""
        }
    }

    /**
     * Возвращает статистику по реестру инструментов
     */
    fun getStatistics(): RegistryStatistics {
        val clientGroups = executors.groupBy { it.mcpClientName }
        val connectionStatus = mcpClientManager.getConnectionStatus()

        return RegistryStatistics(
            totalTools = executors.size,
            toolsByClient = clientGroups.mapValues { it.value.size },
            toolNames = executorMap.keys.toList(),
            mcpConnectionStatus = connectionStatus
        )
    }

    /**
     * Пытается подключиться ко всем необходимым MCP серверам
     */
    private suspend fun ensureClientsConnected() {
        // Подключаемся к обоим серверам параллельно (если еще не подключены)
        mcpClientManager.getDbClient()
        mcpClientManager.getHttpClient()
    }

    /**
     * Подключается к конкретному MCP серверу по имени клиента
     * @return true если подключение успешно, false если не удалось подключиться
     */
    private suspend fun ensureClientConnected(mcpClientName: String): Boolean {
        return when (mcpClientName) {
            "dbMcpClient" -> mcpClientManager.getDbClient() != null
            "httpMcpClient" -> mcpClientManager.getHttpClient() != null
            else -> {
                logger.error("Unknown MCP client name: $mcpClientName")
                false
            }
        }
    }
}

/**
 * Статистика реестра инструментов
 */
data class RegistryStatistics(
    val totalTools: Int,
    val toolsByClient: Map<String, Int>,
    val toolNames: List<String>,
    val mcpConnectionStatus: ConnectionStatus
)
