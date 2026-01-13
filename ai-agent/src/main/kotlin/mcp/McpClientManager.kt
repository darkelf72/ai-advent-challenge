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
    private val localMcpClient: Client,
    private val gitMcpClient: Client,
    private val mcpHttpClient: HttpClient,
    private val dbMcpServerUrl: String,
    private val httpMcpServerUrl: String,
    private val localMcpServerUrl: String,
    private val gitMcpServerUrl: String
) {
    private val logger = LoggerFactory.getLogger(McpClientManager::class.java)

    private val dbMutex = Mutex()
    private val httpMutex = Mutex()
    private val localMutex = Mutex()
    private val gitMutex = Mutex()

    @Volatile
    private var dbClientConnected = false

    @Volatile
    private var httpClientConnected = false

    @Volatile
    private var localClientConnected = false

    @Volatile
    private var gitClientConnected = false

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
     * Получает Local MCP клиент, подключаясь к серверу при необходимости
     */
    suspend fun getLocalClient(): Client? {
        if (!localClientConnected) {
            localMutex.withLock {
                if (!localClientConnected) {
                    try {
                        logger.info("Connecting to Local MCP server at $localMcpServerUrl...")
                        localMcpClient.connect(
                            transport = SseClientTransport(
                                urlString = localMcpServerUrl,
                                client = mcpHttpClient
                            )
                        )
                        localClientConnected = true
                        logger.info("Successfully connected to Local MCP server")
                    } catch (e: Exception) {
                        logger.error("Failed to connect to Local MCP server at $localMcpServerUrl: ${e.message}")
                        return null
                    }
                }
            }
        }
        return localMcpClient
    }

    /**
     * Получает Git MCP клиент, подключаясь к серверу при необходимости
     */
    suspend fun getGitClient(): Client? {
        if (!gitClientConnected) {
            gitMutex.withLock {
                if (!gitClientConnected) {
                    try {
                        logger.info("Connecting to Git MCP server at $gitMcpServerUrl...")
                        gitMcpClient.connect(
                            transport = SseClientTransport(
                                urlString = gitMcpServerUrl,
                                client = mcpHttpClient
                            )
                        )
                        gitClientConnected = true
                        logger.info("Successfully connected to Git MCP server")
                    } catch (e: Exception) {
                        logger.error("Failed to connect to Git MCP server at $gitMcpServerUrl: ${e.message}")
                        return null
                    }
                }
            }
        }
        return gitMcpClient
    }
}