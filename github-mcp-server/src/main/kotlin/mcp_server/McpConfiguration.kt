package mcp_server

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Конфигурация MCP сервера для работы с GitHub API.
 * Предоставляет инструменты для получения информации о Pull Requests.
 */
fun Application.configureMcpServer() {
    val logger = LoggerFactory.getLogger("McpConfiguration")

    // GitHub token из переменных окружения
    val githubToken = System.getenv("GITHUB_TOKEN")
    if (githubToken.isNullOrBlank()) {
        logger.warn("GITHUB_TOKEN not set. Some operations (posting comments) will fail.")
    }

    // HTTP клиент для GitHub API
    val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            })
        }
        install(Logging) {
            level = LogLevel.INFO
        }
    }

    val githubService = GitHubService(httpClient, githubToken)

    routing {
        // Health check endpoint
        get("/health") {
            call.respondText("GitHub MCP Server is running on port $GITHUB_MCP_SERVER_PORT")
        }

        // MCP endpoint
        mcp {
            Server(
                serverInfo = Implementation(
                    name = "github-mcp-server",
                    version = "1.0.0"
                ),
                options = ServerOptions(
                    capabilities = ServerCapabilities(
                        tools = ServerCapabilities.Tools()
                    )
                )
            ).apply {
                // 1. github_get_pr - Получить информацию о Pull Request
                addTool(
                    name = "github_get_pr",
                    description = "Получить информацию о Pull Request (title, описание, статистику, автора). Принимает URL PR или owner/repo/number.",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("url") {
                                    put("type", "string")
                                    put("description", "URL Pull Request (например: https://github.com/owner/repo/pull/123)")
                                }
                                putJsonObject("owner") {
                                    put("type", "string")
                                    put("description", "Владелец репозитория (альтернатива URL)")
                                }
                                putJsonObject("repo") {
                                    put("type", "string")
                                    put("description", "Название репозитория (альтернатива URL)")
                                }
                                putJsonObject("prNumber") {
                                    put("type", "number")
                                    put("description", "Номер Pull Request (альтернатива URL)")
                                }
                            }
                        }
                    )
                ) { request: CallToolRequest ->
                    try {
                        val url = request.arguments?.get("url")?.jsonPrimitive?.contentOrNull

                        val (owner, repo, prNumber) = if (url != null) {
                            // Парсинг URL
                            githubService.parsePrUrl(url)
                                ?: return@addTool CallToolResult(
                                    content = listOf(TextContent("Ошибка: Неверный формат URL. Ожидается: https://github.com/owner/repo/pull/123"))
                                )
                        } else {
                            // Использование параметров
                            val owner = request.arguments?.get("owner")?.jsonPrimitive?.contentOrNull
                            val repo = request.arguments?.get("repo")?.jsonPrimitive?.contentOrNull
                            val prNumber = request.arguments?.get("prNumber")?.jsonPrimitive?.intOrNull

                            if (owner == null || repo == null || prNumber == null) {
                                return@addTool CallToolResult(
                                    content = listOf(TextContent("Ошибка: Укажите либо 'url', либо 'owner', 'repo' и 'prNumber'"))
                                )
                            }
                            Triple(owner, repo, prNumber)
                        }

                        logger.info("Executing github_get_pr for $owner/$repo#$prNumber")
                        val result = githubService.getPullRequest(owner, repo, prNumber)
                        CallToolResult(content = listOf(TextContent(result)))
                    } catch (e: Exception) {
                        logger.error("Error executing github_get_pr", e)
                        CallToolResult(content = listOf(TextContent("Ошибка: ${e.message}")))
                    }
                }

                // 2. github_get_pr_files - Получить список измененных файлов
                addTool(
                    name = "github_get_pr_files",
                    description = "Получить список измененных файлов в Pull Request с указанием количества изменений",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("url") {
                                    put("type", "string")
                                    put("description", "URL Pull Request")
                                }
                                putJsonObject("owner") {
                                    put("type", "string")
                                    put("description", "Владелец репозитория (альтернатива URL)")
                                }
                                putJsonObject("repo") {
                                    put("type", "string")
                                    put("description", "Название репозитория (альтернатива URL)")
                                }
                                putJsonObject("prNumber") {
                                    put("type", "number")
                                    put("description", "Номер Pull Request (альтернатива URL)")
                                }
                            }
                        }
                    )
                ) { request: CallToolRequest ->
                    try {
                        val url = request.arguments?.get("url")?.jsonPrimitive?.contentOrNull

                        val (owner, repo, prNumber) = if (url != null) {
                            githubService.parsePrUrl(url)
                                ?: return@addTool CallToolResult(
                                    content = listOf(TextContent("Ошибка: Неверный формат URL"))
                                )
                        } else {
                            val owner = request.arguments?.get("owner")?.jsonPrimitive?.contentOrNull
                            val repo = request.arguments?.get("repo")?.jsonPrimitive?.contentOrNull
                            val prNumber = request.arguments?.get("prNumber")?.jsonPrimitive?.intOrNull

                            if (owner == null || repo == null || prNumber == null) {
                                return@addTool CallToolResult(
                                    content = listOf(TextContent("Ошибка: Укажите либо 'url', либо 'owner', 'repo' и 'prNumber'"))
                                )
                            }
                            Triple(owner, repo, prNumber)
                        }

                        logger.info("Executing github_get_pr_files for $owner/$repo#$prNumber")
                        val result = githubService.getPullRequestFiles(owner, repo, prNumber)
                        CallToolResult(content = listOf(TextContent(result)))
                    } catch (e: Exception) {
                        logger.error("Error executing github_get_pr_files", e)
                        CallToolResult(content = listOf(TextContent("Ошибка: ${e.message}")))
                    }
                }

                // 3. github_get_pr_diff - Получить полный diff
                addTool(
                    name = "github_get_pr_diff",
                    description = "Получить полный diff изменений в Pull Request в формате unified diff",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("url") {
                                    put("type", "string")
                                    put("description", "URL Pull Request")
                                }
                                putJsonObject("owner") {
                                    put("type", "string")
                                    put("description", "Владелец репозитория (альтернатива URL)")
                                }
                                putJsonObject("repo") {
                                    put("type", "string")
                                    put("description", "Название репозитория (альтернатива URL)")
                                }
                                putJsonObject("prNumber") {
                                    put("type", "number")
                                    put("description", "Номер Pull Request (альтернатива URL)")
                                }
                            }
                        }
                    )
                ) { request: CallToolRequest ->
                    try {
                        val url = request.arguments?.get("url")?.jsonPrimitive?.contentOrNull

                        val (owner, repo, prNumber) = if (url != null) {
                            githubService.parsePrUrl(url)
                                ?: return@addTool CallToolResult(
                                    content = listOf(TextContent("Ошибка: Неверный формат URL"))
                                )
                        } else {
                            val owner = request.arguments?.get("owner")?.jsonPrimitive?.contentOrNull
                            val repo = request.arguments?.get("repo")?.jsonPrimitive?.contentOrNull
                            val prNumber = request.arguments?.get("prNumber")?.jsonPrimitive?.intOrNull

                            if (owner == null || repo == null || prNumber == null) {
                                return@addTool CallToolResult(
                                    content = listOf(TextContent("Ошибка: Укажите либо 'url', либо 'owner', 'repo' и 'prNumber'"))
                                )
                            }
                            Triple(owner, repo, prNumber)
                        }

                        logger.info("Executing github_get_pr_diff for $owner/$repo#$prNumber")
                        val result = githubService.getPullRequestDiff(owner, repo, prNumber)
                        CallToolResult(content = listOf(TextContent(result)))
                    } catch (e: Exception) {
                        logger.error("Error executing github_get_pr_diff", e)
                        CallToolResult(content = listOf(TextContent("Ошибка: ${e.message}")))
                    }
                }

                // 4. github_post_comment - Опубликовать комментарий
                addTool(
                    name = "github_post_comment",
                    description = "Опубликовать комментарий к Pull Request. Требуется GITHUB_TOKEN.",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("url") {
                                    put("type", "string")
                                    put("description", "URL Pull Request")
                                }
                                putJsonObject("owner") {
                                    put("type", "string")
                                    put("description", "Владелец репозитория (альтернатива URL)")
                                }
                                putJsonObject("repo") {
                                    put("type", "string")
                                    put("description", "Название репозитория (альтернатива URL)")
                                }
                                putJsonObject("prNumber") {
                                    put("type", "number")
                                    put("description", "Номер Pull Request (альтернатива URL)")
                                }
                                putJsonObject("comment") {
                                    put("type", "string")
                                    put("description", "Текст комментария (поддерживает Markdown)")
                                }
                            }
                            putJsonArray("required") {
                                add(JsonPrimitive("comment"))
                            }
                        }
                    )
                ) { request: CallToolRequest ->
                    try {
                        val comment = request.arguments?.get("comment")?.jsonPrimitive?.content
                            ?: return@addTool CallToolResult(
                                content = listOf(TextContent("Ошибка: comment обязателен"))
                            )

                        val url = request.arguments?.get("url")?.jsonPrimitive?.contentOrNull

                        val (owner, repo, prNumber) = if (url != null) {
                            githubService.parsePrUrl(url)
                                ?: return@addTool CallToolResult(
                                    content = listOf(TextContent("Ошибка: Неверный формат URL"))
                                )
                        } else {
                            val owner = request.arguments?.get("owner")?.jsonPrimitive?.contentOrNull
                            val repo = request.arguments?.get("repo")?.jsonPrimitive?.contentOrNull
                            val prNumber = request.arguments?.get("prNumber")?.jsonPrimitive?.intOrNull

                            if (owner == null || repo == null || prNumber == null) {
                                return@addTool CallToolResult(
                                    content = listOf(TextContent("Ошибка: Укажите либо 'url', либо 'owner', 'repo' и 'prNumber'"))
                                )
                            }
                            Triple(owner, repo, prNumber)
                        }

                        logger.info("Executing github_post_comment for $owner/$repo#$prNumber")
                        val result = githubService.postComment(owner, repo, prNumber, comment)
                        CallToolResult(content = listOf(TextContent(result)))
                    } catch (e: Exception) {
                        logger.error("Error executing github_post_comment", e)
                        CallToolResult(content = listOf(TextContent("Ошибка: ${e.message}")))
                    }
                }
            }
        }
    }
}
