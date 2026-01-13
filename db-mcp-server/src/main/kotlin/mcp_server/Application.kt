package mcp_server

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.sse.*
import kotlinx.serialization.json.Json

const val MCP_SERVER_PORT = 8081

fun main() {
    // Инициализируем базу данных перед запуском сервера
    DatabaseManager.init()

    embeddedServer(
        factory = Netty,
        configure = {
            // HTTP connector
            connector {
                port = MCP_SERVER_PORT
            }
        },
        module = Application::module
    ).start(wait = true)
}


fun Application.module() {
    // Install ContentNegotiation with JSON
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            isLenient = true
        })
    }

    // Install SSE (Server-Sent Events) - required for MCP
    install(SSE)

    // Configure MCP Server (includes all routing)
    configureMcpServer()
}
