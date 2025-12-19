package mcp

import io.ktor.client.*
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory

/**
 * Менеджер для управления подключениями к MCP серверам.
 * Поддерживает ленивое (lazy) подключение - серверы подключаются только при первом обращении.
 */
class McpClientManager(
    private val dbMcpClient: Client,
    private val httpMcpClient: Client,
    private val mcpHttpClient: HttpClient,
    private val dbMcpServerUrl: String,
    private val httpMcpServerUrl: String
) {
    private val logger = LoggerFactory.getLogger(McpClientManager::class.java)

    private val dbMutex = Mutex()
    private val httpMutex = Mutex()

    @Volatile
    private var dbClientConnected = false

    @Volatile
    private var httpClientConnected = false

    /**
     * Получает DB MCP клиент, подключаясь к серверу при необходимости
     */
    suspend fun getDbClient(): Client? {
        if (!dbClientConnected) {
            dbMutex.withLock {
                if (!dbClientConnected) {
                    try {
                        logger.info("Connecting to DB MCP server at $dbMcpServerUrl...")
                        dbMcpClient.connect(
                            transport = SseClientTransport(
                                urlString = dbMcpServerUrl,
                                client = mcpHttpClient
                            )
                        )
                        dbClientConnected = true
                        logger.info("Successfully connected to DB MCP server")
                    } catch (e: Exception) {
                        logger.error("Failed to connect to DB MCP server at $dbMcpServerUrl: ${e.message}")
                        return null
                    }
                }
            }
        }
        return dbMcpClient
    }

    /**
     * Получает HTTP MCP клиент, подключаясь к серверу при необходимости
     */
    suspend fun getHttpClient(): Client? {
        if (!httpClientConnected) {
            httpMutex.withLock {
                if (!httpClientConnected) {
                    try {
                        logger.info("Connecting to HTTP MCP server at $httpMcpServerUrl...")
                        httpMcpClient.connect(
                            transport = SseClientTransport(
                                urlString = httpMcpServerUrl,
                                client = mcpHttpClient
                            )
                        )
                        httpClientConnected = true
                        logger.info("Successfully connected to HTTP MCP server")
                    } catch (e: Exception) {
                        logger.error("Failed to connect to HTTP MCP server at $httpMcpServerUrl: ${e.message}")
                        return null
                    }
                }
            }
        }
        return httpMcpClient
    }

    /**
     * Проверяет, подключен ли DB MCP клиент
     */
    fun isDbClientConnected(): Boolean = dbClientConnected

    /**
     * Проверяет, подключен ли HTTP MCP клиент
     */
    fun isHttpClientConnected(): Boolean = httpClientConnected

    /**
     * Возвращает статус подключений
     */
    fun getConnectionStatus(): ConnectionStatus {
        return ConnectionStatus(
            dbMcpConnected = dbClientConnected,
            httpMcpConnected = httpClientConnected
        )
    }
}

/**
 * Статус подключений к MCP серверам
 */
data class ConnectionStatus(
    val dbMcpConnected: Boolean,
    val httpMcpConnected: Boolean
) {
    val allConnected: Boolean
        get() = dbMcpConnected && httpMcpConnected

    val anyConnected: Boolean
        get() = dbMcpConnected || httpMcpConnected
}
