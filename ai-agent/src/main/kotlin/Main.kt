import apiclients.ApiClientInterface
import controllers.ChatController
import controllers.ClientController
import controllers.ConfigController
import database.DatabaseManager
import di.appModule
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.html.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.routing.*
import mcp.ToolRegistry
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import org.koin.java.KoinJavaComponent.get
import service.SummarizationService
import views.chatPage

fun main() {
    // Initialize database
    DatabaseManager.init()

    // Initialize Koin DI
    startKoin {
        modules(appModule)
    }

    // ToolRegistry теперь с lazy-подключением, инициализация не требуется при запуске
    val toolRegistry = get<ToolRegistry>(ToolRegistry::class.java)

    // Логируем, что приложение готово к работе (MCP серверы подключатся при первом обращении)
    println("AI Agent initialized. MCP servers will be connected on first use.")
    println("Available tools: ${toolRegistry.getToolNames().joinToString(", ")}")

    // Get dependencies from Koin
    val availableClients = get<Map<String, ApiClientInterface>>(Map::class.java, named("availableClients"))
    val summarizationService = get<SummarizationService>(SummarizationService::class.java)

    // Initialize controllers
    val clientController = ClientController(availableClients)
    val chatController = ChatController(clientController)
    val configController = ConfigController(clientController, summarizationService)

    // Start server
    embeddedServer(Netty, port = 9999, host = "0.0.0.0") {
        configureServer(clientController, chatController, configController)
    }.start(wait = true)
}

fun Application.configureServer(
    clientController: ClientController,
    chatController: ChatController,
    configController: ConfigController
) {
    install(ContentNegotiation) { json() }
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
    }

    routing {
        // Serve static files (CSS, JS)
        staticResources("/static", "static")

        // Main page
        get("/") {
            call.respondHtml { chatPage() }
        }

        // Chat endpoints
        post("/api/send") { chatController.handleSendMessage(call) }
        get("/api/message-history") { chatController.handleGetMessageHistory(call) }
        post("/api/clear-history") { chatController.handleClearHistory(call) }

        // Configuration endpoints
        get("/api/system-prompt") { configController.handleGetSystemPrompt(call) }
        post("/api/system-prompt") { configController.handleSetSystemPrompt(call) }
        get("/api/temperature") { configController.handleGetTemperature(call) }
        post("/api/temperature") { configController.handleSetTemperature(call) }
        get("/api/max-tokens") { configController.handleGetMaxTokens(call) }
        post("/api/max-tokens") { configController.handleSetMaxTokens(call) }
        get("/api/auto-summarize-threshold") { configController.handleGetAutoSummarizeThreshold(call) }
        post("/api/auto-summarize-threshold") { configController.handleSetAutoSummarizeThreshold(call) }
        post("/api/summarize") { configController.handleSummarize(call) }

        // Client switching endpoints
        get("/api/current-client") { clientController.handleGetCurrentClient(call) }
        get("/api/available-clients") { clientController.handleGetAvailableClients(call) }
        post("/api/switch-client") { clientController.handleSwitchClient(call) }
    }
}
