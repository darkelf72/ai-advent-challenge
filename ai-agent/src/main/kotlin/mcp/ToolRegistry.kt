package mcp

import kotlinx.serialization.json.JsonObject
import mcp.tools.ToolExecutor
import org.slf4j.LoggerFactory

/**
 * Реестр MCP инструментов.
 * Управляет всеми доступными инструментами, кэширует их описания и предоставляет
 * единую точку доступа для выполнения инструментов.
 */
class ToolRegistry(
    private val executors: List<ToolExecutor>
) {
    private val logger = LoggerFactory.getLogger(ToolRegistry::class.java)
    private val executorMap: Map<String, ToolExecutor>
    private var isInitialized = false

    init {
        executorMap = executors.associateBy { it.toolName }
        logger.info("ToolRegistry created with ${executors.size} executors")
    }

    /**
     * Инициализирует реестр инструментов.
     * Проверяет доступность всех зарегистрированных инструментов и логирует статистику.
     * Вызывается один раз при старте приложения.
     */
    fun initialize() {
        if (isInitialized) {
            logger.warn("ToolRegistry already initialized")
            return
        }

        logger.info("Initializing ToolRegistry...")
        executors.forEach { executor ->
            logger.info(
                "Registered tool: '${executor.toolName}' " +
                        "(client: ${executor.mcpClientName}, description: ${executor.description})"
            )
        }

        isInitialized = true
        logger.info("ToolRegistry initialized successfully with ${executors.size} tools")
    }

    /**
     * Проверяет, что реестр инициализирован.
     * @throws IllegalStateException если реестр не инициализирован
     */
    private fun ensureInitialized() {
        if (!isInitialized) {
            throw IllegalStateException("ToolRegistry not initialized. Call initialize() first.")
        }
    }

    /**
     * Возвращает список всех доступных инструментов
     */
    fun getAllTools(): List<ToolExecutor> {
        ensureInitialized()
        return executors
    }

    /**
     * Возвращает инструмент по имени
     * @param toolName имя инструмента
     * @return исполнитель инструмента или null если не найден
     */
    fun getToolByName(toolName: String): ToolExecutor? {
        ensureInitialized()
        return executorMap[toolName]
    }

    /**
     * Проверяет, существует ли инструмент с заданным именем
     */
    fun hasTool(toolName: String): Boolean {
        ensureInitialized()
        return executorMap.containsKey(toolName)
    }

    /**
     * Возвращает список имен всех доступных инструментов
     */
    fun getToolNames(): List<String> {
        ensureInitialized()
        return executorMap.keys.toList()
    }

    /**
     * Выполняет инструмент по имени с переданными аргументами
     * @param toolName имя инструмента
     * @param arguments JSON объект с аргументами
     * @return JSON строка с результатом выполнения или ошибкой
     */
    suspend fun executeTool(toolName: String, arguments: JsonObject): String {
        ensureInitialized()

        val executor = executorMap[toolName]
        if (executor == null) {
            logger.error("Tool '$toolName' not found in registry")
            return """{"error": "Unknown tool: $toolName", "status": "error"}"""
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
        ensureInitialized()
        val clientGroups = executors.groupBy { it.mcpClientName }
        return RegistryStatistics(
            totalTools = executors.size,
            toolsByClient = clientGroups.mapValues { it.value.size },
            toolNames = executorMap.keys.toList()
        )
    }
}

/**
 * Статистика реестра инструментов
 */
data class RegistryStatistics(
    val totalTools: Int,
    val toolsByClient: Map<String, Int>,
    val toolNames: List<String>
)
