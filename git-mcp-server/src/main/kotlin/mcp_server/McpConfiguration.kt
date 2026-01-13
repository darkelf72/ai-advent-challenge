package mcp_server

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
 * Конфигурация MCP сервера для Git операций.
 * Предоставляет 10 инструментов для работы с Git через JGit.
 */
fun Application.configureMcpServer() {
    val logger = LoggerFactory.getLogger("McpConfiguration")
    val gitService = GitService()

    routing {
        // Health check endpoint
        get("/health") {
            call.respondText("Git MCP Server is running on port $GIT_MCP_SERVER_PORT")
        }

        // MCP endpoint
        mcp {
            Server(
                serverInfo = Implementation(
                    name = "git-mcp-server",
                    version = "1.0.0"
                ),
                options = ServerOptions(
                    capabilities = ServerCapabilities(
                        tools = ServerCapabilities.Tools()
                    )
                )
            ).apply {
                // 1. git_status - Получить статус репозитория
                addTool(
                    name = "git_status",
                    description = "Получить статус git репозитория (измененные, добавленные, неотслеживаемые файлы)",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {}
                        }
                    )
                ) { _: CallToolRequest ->
                    try {
                        logger.info("Executing git_status tool")
                        val result = gitService.getStatus()
                        CallToolResult(content = listOf(TextContent(result)))
                    } catch (e: Exception) {
                        logger.error("Error executing git_status", e)
                        CallToolResult(content = listOf(TextContent("Ошибка: ${e.message}")))
                    }
                }

                // 2. git_log - Показать историю коммитов
                addTool(
                    name = "git_log",
                    description = "Показать историю коммитов с указанием автора, даты и сообщения",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("limit") {
                                    put("type", "number")
                                    put("description", "Количество коммитов для отображения (по умолчанию 10)")
                                }
                                putJsonObject("branch") {
                                    put("type", "string")
                                    put("description", "Имя ветки (необязательно)")
                                }
                            }
                        }
                    )
                ) { request: CallToolRequest ->
                    try {
                        val limit = request.arguments?.get("limit")?.jsonPrimitive?.content?.toIntOrNull() ?: 10
                        val branch = request.arguments?.get("branch")?.jsonPrimitive?.contentOrNull

                        logger.info("Executing git_log tool with limit=$limit, branch=$branch")
                        val result = gitService.getLog(limit, branch)
                        CallToolResult(content = listOf(TextContent(result)))
                    } catch (e: Exception) {
                        logger.error("Error executing git_log", e)
                        CallToolResult(content = listOf(TextContent("Ошибка: ${e.message}")))
                    }
                }

                // 3. git_diff - Показать изменения
                addTool(
                    name = "git_diff",
                    description = "Показать изменения в рабочей директории или staged файлах",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("cached") {
                                    put("type", "boolean")
                                    put("description", "Показать staged изменения (по умолчанию false)")
                                }
                                putJsonObject("fileName") {
                                    put("type", "string")
                                    put("description", "Имя файла для фильтрации (необязательно)")
                                }
                            }
                        }
                    )
                ) { request: CallToolRequest ->
                    try {
                        val cached = request.arguments?.get("cached")?.jsonPrimitive?.booleanOrNull ?: false
                        val fileName = request.arguments?.get("fileName")?.jsonPrimitive?.contentOrNull

                        logger.info("Executing git_diff tool with cached=$cached, fileName=$fileName")
                        val result = gitService.getDiff(cached, fileName)
                        CallToolResult(content = listOf(TextContent(result)))
                    } catch (e: Exception) {
                        logger.error("Error executing git_diff", e)
                        CallToolResult(content = listOf(TextContent("Ошибка: ${e.message}")))
                    }
                }

                // 4. git_branch_list - Список веток
                addTool(
                    name = "git_branch_list",
                    description = "Получить список локальных и/или удаленных веток",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("listMode") {
                                    put("type", "string")
                                    put("description", "Режим: 'local', 'remote', или 'all' (по умолчанию 'all')")
                                }
                            }
                        }
                    )
                ) { request: CallToolRequest ->
                    try {
                        val listMode = request.arguments?.get("listMode")?.jsonPrimitive?.contentOrNull ?: "all"

                        logger.info("Executing git_branch_list tool with listMode=$listMode")
                        val result = gitService.listBranches(listMode)
                        CallToolResult(content = listOf(TextContent(result)))
                    } catch (e: Exception) {
                        logger.error("Error executing git_branch_list", e)
                        CallToolResult(content = listOf(TextContent("Ошибка: ${e.message}")))
                    }
                }

                // 5. git_branch_create - Создать ветку
                addTool(
                    name = "git_branch_create",
                    description = "Создать новую ветку",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("branchName") {
                                    put("type", "string")
                                    put("description", "Имя новой ветки")
                                }
                                putJsonObject("startPoint") {
                                    put("type", "string")
                                    put("description", "Начальная точка (коммит/ветка), необязательно")
                                }
                            }
                            putJsonArray("required") {
                                add(JsonPrimitive("branchName"))
                            }
                        }
                    )
                ) { request: CallToolRequest ->
                    try {
                        val branchName = request.arguments?.get("branchName")?.jsonPrimitive?.content
                            ?: return@addTool CallToolResult(
                                content = listOf(TextContent("Ошибка: branchName обязателен"))
                            )
                        val startPoint = request.arguments?.get("startPoint")?.jsonPrimitive?.contentOrNull

                        logger.info("Executing git_branch_create tool with branchName=$branchName, startPoint=$startPoint")
                        val result = gitService.createBranch(branchName, startPoint)
                        CallToolResult(content = listOf(TextContent(result)))
                    } catch (e: Exception) {
                        logger.error("Error executing git_branch_create", e)
                        CallToolResult(content = listOf(TextContent("Ошибка: ${e.message}")))
                    }
                }

                // 6. git_checkout - Переключить ветку
                addTool(
                    name = "git_checkout",
                    description = "Переключиться на другую ветку",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("branchName") {
                                    put("type", "string")
                                    put("description", "Имя ветки для переключения")
                                }
                                putJsonObject("createIfNotExists") {
                                    put("type", "boolean")
                                    put("description", "Создать ветку если не существует (по умолчанию false)")
                                }
                            }
                            putJsonArray("required") {
                                add(JsonPrimitive("branchName"))
                            }
                        }
                    )
                ) { request: CallToolRequest ->
                    try {
                        val branchName = request.arguments?.get("branchName")?.jsonPrimitive?.content
                            ?: return@addTool CallToolResult(
                                content = listOf(TextContent("Ошибка: branchName обязателен"))
                            )
                        val createIfNotExists = request.arguments?.get("createIfNotExists")?.jsonPrimitive?.booleanOrNull ?: false

                        logger.info("Executing git_checkout tool with branchName=$branchName, createIfNotExists=$createIfNotExists")
                        val result = gitService.checkout(branchName, createIfNotExists)
                        CallToolResult(content = listOf(TextContent(result)))
                    } catch (e: Exception) {
                        logger.error("Error executing git_checkout", e)
                        CallToolResult(content = listOf(TextContent("Ошибка: ${e.message}")))
                    }
                }

                // 7. git_commit - Создать коммит
                addTool(
                    name = "git_commit",
                    description = "Создать коммит с указанным сообщением",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("message") {
                                    put("type", "string")
                                    put("description", "Сообщение коммита")
                                }
                                putJsonObject("addAll") {
                                    put("type", "boolean")
                                    put("description", "Добавить все измененные файлы перед коммитом (git add . && git commit)")
                                }
                            }
                            putJsonArray("required") {
                                add(JsonPrimitive("message"))
                            }
                        }
                    )
                ) { request: CallToolRequest ->
                    try {
                        val message = request.arguments?.get("message")?.jsonPrimitive?.content
                            ?: return@addTool CallToolResult(
                                content = listOf(TextContent("Ошибка: message обязателен"))
                            )
                        val addAll = request.arguments?.get("addAll")?.jsonPrimitive?.booleanOrNull ?: false

                        logger.info("Executing git_commit tool with message='$message', addAll=$addAll")
                        val result = gitService.commit(message, addAll)
                        CallToolResult(content = listOf(TextContent(result)))
                    } catch (e: Exception) {
                        logger.error("Error executing git_commit", e)
                        CallToolResult(content = listOf(TextContent("Ошибка: ${e.message}")))
                    }
                }

                // 8. git_push - Отправить на remote
                addTool(
                    name = "git_push",
                    description = "Отправить коммиты на удаленный репозиторий",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("remote") {
                                    put("type", "string")
                                    put("description", "Имя удаленного репозитория (по умолчанию 'origin')")
                                }
                                putJsonObject("branch") {
                                    put("type", "string")
                                    put("description", "Имя ветки (необязательно)")
                                }
                                putJsonObject("username") {
                                    put("type", "string")
                                    put("description", "Имя пользователя для аутентификации (необязательно)")
                                }
                                putJsonObject("password") {
                                    put("type", "string")
                                    put("description", "Пароль или токен для аутентификации (необязательно)")
                                }
                            }
                        }
                    )
                ) { request: CallToolRequest ->
                    try {
                        val remote = request.arguments?.get("remote")?.jsonPrimitive?.contentOrNull ?: "origin"
                        val branch = request.arguments?.get("branch")?.jsonPrimitive?.contentOrNull
                        val username = request.arguments?.get("username")?.jsonPrimitive?.contentOrNull
                        val password = request.arguments?.get("password")?.jsonPrimitive?.contentOrNull

                        logger.info("Executing git_push tool with remote=$remote, branch=$branch")
                        val result = gitService.push(remote, branch, username, password)
                        CallToolResult(content = listOf(TextContent(result)))
                    } catch (e: Exception) {
                        logger.error("Error executing git_push", e)
                        CallToolResult(content = listOf(TextContent("Ошибка: ${e.message}")))
                    }
                }

                // 9. git_pull - Получить изменения
                addTool(
                    name = "git_pull",
                    description = "Получить изменения из удаленного репозитория",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("remote") {
                                    put("type", "string")
                                    put("description", "Имя удаленного репозитория (по умолчанию 'origin')")
                                }
                                putJsonObject("branch") {
                                    put("type", "string")
                                    put("description", "Имя ветки (необязательно)")
                                }
                                putJsonObject("username") {
                                    put("type", "string")
                                    put("description", "Имя пользователя для аутентификации (необязательно)")
                                }
                                putJsonObject("password") {
                                    put("type", "string")
                                    put("description", "Пароль или токен для аутентификации (необязательно)")
                                }
                            }
                        }
                    )
                ) { request: CallToolRequest ->
                    try {
                        val remote = request.arguments?.get("remote")?.jsonPrimitive?.contentOrNull ?: "origin"
                        val branch = request.arguments?.get("branch")?.jsonPrimitive?.contentOrNull
                        val username = request.arguments?.get("username")?.jsonPrimitive?.contentOrNull
                        val password = request.arguments?.get("password")?.jsonPrimitive?.contentOrNull

                        logger.info("Executing git_pull tool with remote=$remote, branch=$branch")
                        val result = gitService.pull(remote, branch, username, password)
                        CallToolResult(content = listOf(TextContent(result)))
                    } catch (e: Exception) {
                        logger.error("Error executing git_pull", e)
                        CallToolResult(content = listOf(TextContent("Ошибка: ${e.message}")))
                    }
                }

                // 10. git_show_file - Показать содержимое файла в коммите
                addTool(
                    name = "git_show_file",
                    description = "Показать содержимое файла в определенном коммите",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("commitHash") {
                                    put("type", "string")
                                    put("description", "Hash коммита или ref (например, HEAD, HEAD~1)")
                                }
                                putJsonObject("filePath") {
                                    put("type", "string")
                                    put("description", "Путь к файлу в репозитории")
                                }
                            }
                            putJsonArray("required") {
                                add(JsonPrimitive("commitHash"))
                                add(JsonPrimitive("filePath"))
                            }
                        }
                    )
                ) { request: CallToolRequest ->
                    try {
                        val commitHash = request.arguments?.get("commitHash")?.jsonPrimitive?.content
                            ?: return@addTool CallToolResult(
                                content = listOf(TextContent("Ошибка: commitHash обязателен"))
                            )
                        val filePath = request.arguments?.get("filePath")?.jsonPrimitive?.content
                            ?: return@addTool CallToolResult(
                                content = listOf(TextContent("Ошибка: filePath обязателен"))
                            )

                        logger.info("Executing git_show_file tool with commitHash=$commitHash, filePath=$filePath")
                        val result = gitService.showFile(commitHash, filePath)
                        CallToolResult(content = listOf(TextContent(result)))
                    } catch (e: Exception) {
                        logger.error("Error executing git_show_file", e)
                        CallToolResult(content = listOf(TextContent("Ошибка: ${e.message}")))
                    }
                }
            }
        }
    }
}
