package mcp_server

import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

data class OperationResult(
    val success: Boolean,
    val message: String,
    val servers: List<McpServerInfo> = emptyList()
)

data class McpServerInfo(
    val name: String,
    val status: String,
    val url: String,
    val tools: List<String> = emptyList()
)

class DockerService {
    private val logger = LoggerFactory.getLogger(DockerService::class.java)
    private val projectRoot = File(System.getProperty("user.dir")).parentFile.absolutePath
    private val scriptsDir = File(projectRoot, "local-mcp-server/scripts")

    fun startMcpServers(): OperationResult {
        return try {
            val scriptFile = File(scriptsDir, "start-mcp-servers.sh")
            if (!scriptFile.exists()) {
                return OperationResult(
                    success = false,
                    message = "Start script not found: ${scriptFile.absolutePath}"
                )
            }

            logger.info("Executing start script: ${scriptFile.absolutePath}")
            val process = ProcessBuilder("bash", scriptFile.absolutePath)
                .directory(File(projectRoot))
                .redirectErrorStream(true)
                .start()

            val output = readProcessOutput(process)
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                // Get information about running servers and their tools
                val servers = getRunningServers()
                OperationResult(
                    success = true,
                    message = "MCP servers started successfully",
                    servers = servers
                )
            } else {
                OperationResult(
                    success = false,
                    message = "Failed to start MCP servers. Exit code: $exitCode. Output: $output"
                )
            }
        } catch (e: Exception) {
            logger.error("Error starting MCP servers", e)
            OperationResult(
                success = false,
                message = "Exception while starting MCP servers: ${e.message}"
            )
        }
    }

    fun stopMcpServers(): OperationResult {
        return try {
            // Get list of servers before stopping
            val serversBefore = getRunningServers()

            val scriptFile = File(scriptsDir, "stop-mcp-servers.sh")
            if (!scriptFile.exists()) {
                return OperationResult(
                    success = false,
                    message = "Stop script not found: ${scriptFile.absolutePath}"
                )
            }

            logger.info("Executing stop script: ${scriptFile.absolutePath}")
            val process = ProcessBuilder("bash", scriptFile.absolutePath)
                .directory(File(projectRoot))
                .redirectErrorStream(true)
                .start()

            val output = readProcessOutput(process)
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                OperationResult(
                    success = true,
                    message = "MCP servers stopped successfully",
                    servers = serversBefore.map { it.copy(status = "stopped") }
                )
            } else {
                OperationResult(
                    success = false,
                    message = "Failed to stop MCP servers. Exit code: $exitCode. Output: $output"
                )
            }
        } catch (e: Exception) {
            logger.error("Error stopping MCP servers", e)
            OperationResult(
                success = false,
                message = "Exception while stopping MCP servers: ${e.message}"
            )
        }
    }

    private fun getRunningServers(): List<McpServerInfo> {
        val servers = mutableListOf<McpServerInfo>()

        // Check db-mcp-server
        val dbServerRunning = isContainerRunning("db-mcp-server")
        if (dbServerRunning) {
            val tools = getServerTools("http://localhost:8081")
            servers.add(McpServerInfo(
                name = "db-mcp-server",
                status = "running",
                url = "http://localhost:8081",
                tools = tools
            ))
        }

        // Check http-mcp-server
        val httpServerRunning = isContainerRunning("http-mcp-server")
        if (httpServerRunning) {
            val tools = getServerTools("http://localhost:8082")
            servers.add(McpServerInfo(
                name = "http-mcp-server",
                status = "running",
                url = "http://localhost:8082",
                tools = tools
            ))
        }

        return servers
    }

    private fun isContainerRunning(containerName: String): Boolean {
        return try {
            val process = ProcessBuilder("docker", "ps", "--filter", "name=$containerName", "--format", "{{.Names}}")
                .redirectErrorStream(true)
                .start()

            val output = readProcessOutput(process)
            process.waitFor()

            output.trim() == containerName
        } catch (e: Exception) {
            logger.error("Error checking container status: $containerName", e)
            false
        }
    }

    private fun getServerTools(serverUrl: String): List<String> {
        return try {
            // Wait a bit for server to be ready
            Thread.sleep(2000)

            val process = ProcessBuilder(
                "curl", "-s", "-X", "POST",
                "$serverUrl/mcp/v1",
                "-H", "Content-Type: application/json",
                "-d", """{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}"""
            ).redirectErrorStream(true).start()

            val output = readProcessOutput(process)
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                parseToolsFromResponse(output)
            } else {
                logger.warn("Failed to get tools from $serverUrl")
                emptyList()
            }
        } catch (e: Exception) {
            logger.error("Error getting tools from $serverUrl", e)
            emptyList()
        }
    }

    private fun parseToolsFromResponse(jsonResponse: String): List<String> {
        return try {
            // Simple JSON parsing to extract tool names
            val toolsRegex = """"name"\s*:\s*"([^"]+)"""".toRegex()
            toolsRegex.findAll(jsonResponse)
                .map { it.groupValues[1] }
                .toList()
        } catch (e: Exception) {
            logger.error("Error parsing tools from response", e)
            emptyList()
        }
    }

    private fun readProcessOutput(process: Process): String {
        val output = StringBuilder()
        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            reader.lineSequence().forEach { line ->
                output.append(line).append("\n")
                logger.info(line)
            }
        }
        return output.toString()
    }
}
