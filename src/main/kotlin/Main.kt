import database.DatabaseManager
import di.appModule
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.html.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import org.koin.java.KoinJavaComponent.get
import service.SummarizationService

@Serializable
data class ChatRequest(val message: String)

@Serializable
data class ChatResponse(val question: String, val answer: String, val result: String)

@Serializable
data class SystemPromptRequest(val prompt: String)

@Serializable
data class SystemPromptResponse(val prompt: String)

@Serializable
data class TemperatureRequest(val temperature: Double)

@Serializable
data class TemperatureResponse(val temperature: Double)

@Serializable
data class MaxTokensRequest(val maxTokens: Int)

@Serializable
data class MaxTokensResponse(val maxTokens: Int)

@Serializable
data class MessageHistoryResponse(val messages: List<Map<String, String>>)

@Serializable
data class ClientSwitchRequest(val clientName: String)

@Serializable
data class ClientSwitchResponse(val clientName: String, val systemPrompt: String, val temperature: Double)

@Serializable
data class SummarizeResponse(
    val newSystemPrompt: String,
    val oldMessagesCount: Int,
    val oldTokensCount: Int,
    val newTokensCount: Int,
    val compressionPercent: Int
)

@Serializable
data class AutoSummarizeThresholdRequest(val threshold: Int)

@Serializable
data class AutoSummarizeThresholdResponse(val threshold: Int)

// Текущий активный клиент
private var currentClientName: String = "YandexGPT Pro 5.1"
private lateinit var apiClient: ApiClientInterface
private lateinit var availableClients: Map<String, ApiClientInterface>
private lateinit var summarizeApiClient: ApiClientInterface
private lateinit var summarizationService: SummarizationService

fun main() {
    // Инициализация базы данных ДО запуска Koin
    DatabaseManager.init()

    // Инициализация Koin
    startKoin {
        modules(appModule)
    }

    // Получение зависимостей через Koin
    availableClients = get(Map::class.java, named("availableClients")) as Map<String, ApiClientInterface>
    apiClient = availableClients.getValue(currentClientName)
    summarizeApiClient = get(ApiClientInterface::class.java, named("summarizeApiClient"))
    summarizationService = get(SummarizationService::class.java)

    embeddedServer(Netty, port = 9999, host = "0.0.0.0") {
        configureServer()
    }.start(wait = true)
}

fun Application.configureServer() {
    install(ContentNegotiation) { json() }
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
    }

    routing {
        get("/") { call.respondHtml { chatPage() } }
        post("/api/send") { handleSendMessage(call) }
        get("/api/system-prompt") { handleGetSystemPrompt(call) }
        post("/api/system-prompt") { handleSetSystemPrompt(call) }
        get("/api/temperature") { handleGetTemperature(call) }
        post("/api/temperature") { handleSetTemperature(call) }
        get("/api/max-tokens") { handleGetMaxTokens(call) }
        post("/api/max-tokens") { handleSetMaxTokens(call) }
        get("/api/message-history") { handleGetMessageHistory(call) }
        post("/api/clear-history") { handleClearHistory(call) }
        get("/api/current-client") { handleGetCurrentClient(call) }
        post("/api/switch-client") { handleSwitchClient(call) }
        get("/api/available-clients") { handleGetAvailableClients(call) }
        post("/api/summarize") { handleSummarize(call) }
        get("/api/auto-summarize-threshold") { handleGetAutoSummarizeThreshold(call) }
        post("/api/auto-summarize-threshold") { handleSetAutoSummarizeThreshold(call) }
    }
}

private val json = Json { prettyPrint = true }

suspend fun handleSendMessage(call: ApplicationCall) {
    val request = call.receive<ChatRequest>()
    val apiResponse = apiClient.sendRequest(request.message)

    val foo = """
        🕒 ${apiResponse.result!!.elapsedTime} мс
        ⬆️ ${apiResponse.result.promptTokens} токенов
        ⬇️ ${apiResponse.result.completionTokens} токенов
        ↕️ ${apiResponse.result.totalTokens} токенов
        💸 ${apiResponse.result.cost} руб
    """.trimIndent()
    val chatResponse = ChatResponse(
        question = request.message,
        answer = apiResponse.message,
        result = foo//json.encodeToString(apiResponse.result)
    )
    call.respond(chatResponse)
}

suspend fun handleGetSystemPrompt(call: ApplicationCall) {
    call.respond(SystemPromptResponse(prompt = apiClient.config.systemPrompt))
}

suspend fun handleSetSystemPrompt(call: ApplicationCall) {
    val request = call.receive<SystemPromptRequest>()
    apiClient.config = apiClient.config.copy(systemPrompt = request.prompt)
    call.respond(SystemPromptResponse(prompt = request.prompt))
}

suspend fun handleGetMessageHistory(call: ApplicationCall) {
    val history = apiClient.messageHistory.map { message ->
        mapOf("role" to message.role, "content" to message.content)
    }
    call.respond(MessageHistoryResponse(messages = history))
}

suspend fun handleClearHistory(call: ApplicationCall) {
    apiClient.clearMessages()
    call.respond(mapOf("status" to "ok"))
}

suspend fun handleGetTemperature(call: ApplicationCall) {
    call.respond(TemperatureResponse(temperature = apiClient.config.temperature))
}

suspend fun handleSetTemperature(call: ApplicationCall) {
    val request = call.receive<TemperatureRequest>()
    apiClient.config = apiClient.config.copy(temperature = request.temperature)
    call.respond(TemperatureResponse(temperature = request.temperature))
}

suspend fun handleGetMaxTokens(call: ApplicationCall) {
    call.respond(MaxTokensResponse(maxTokens = apiClient.config.maxTokens))
}

suspend fun handleSetMaxTokens(call: ApplicationCall) {
    val request = call.receive<MaxTokensRequest>()
    apiClient.config = apiClient.config.copy(maxTokens = request.maxTokens)
    call.respond(MaxTokensResponse(maxTokens = request.maxTokens))
}

suspend fun handleGetCurrentClient(call: ApplicationCall) {
    call.respond(mapOf("clientName" to currentClientName))
}

suspend fun handleGetAvailableClients(call: ApplicationCall) {
    call.respond(mapOf("clients" to availableClients.keys.toList()))
}

suspend fun handleSwitchClient(call: ApplicationCall) {
    val request = call.receive<ClientSwitchRequest>()
    val newClient = availableClients[request.clientName]

    if (newClient != null) {
        apiClient = newClient
        currentClientName = request.clientName
        call.respond(
            ClientSwitchResponse(
                clientName = currentClientName,
                systemPrompt = apiClient.config.systemPrompt,
                temperature = apiClient.config.temperature
            )
        )
    } else {
        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Unknown client: ${request.clientName}"))
    }
}

suspend fun handleSummarize(call: ApplicationCall) {
    try {
        // Используем SummarizationService для выполнения суммаризации
        val result = summarizationService.summarize(apiClient)
        call.respond(result)
    } catch (e: Exception) {
        // Логируем ошибку, но не прерываем основной поток
        println("Auto-summarization failed: ${e.message}")
    }
}

suspend fun handleGetAutoSummarizeThreshold(call: ApplicationCall) {
    call.respond(AutoSummarizeThresholdResponse(threshold = apiClient.config.autoSummarizeThreshold))
}

suspend fun handleSetAutoSummarizeThreshold(call: ApplicationCall) {
    val request = call.receive<AutoSummarizeThresholdRequest>()
    apiClient.config = apiClient.config.copy(autoSummarizeThreshold = request.threshold)
    call.respond(AutoSummarizeThresholdResponse(threshold = request.threshold))
}

fun HTML.chatPage() {
    head {
        title { +"AI Chat" }
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        script {
            src = "https://cdn.jsdelivr.net/npm/marked/marked.min.js"
        }
        style {
            unsafe {
                raw("""
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        height: 100vh;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        padding: 20px;
                    }
                    .container {
                        width: 100%;
                        max-width: 800px;
                        height: 90vh;
                        background: white;
                        border-radius: 20px;
                        box-shadow: 0 20px 60px rgba(0,0,0,0.3);
                        display: flex;
                        flex-direction: column;
                        overflow: hidden;
                    }
                    .header {
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        color: white;
                        padding: 20px;
                        text-align: center;
                        font-size: 24px;
                        font-weight: bold;
                        box-shadow: 0 2px 10px rgba(0,0,0,0.1);
                    }
                    .controls-area {
                        background: #f9f9f9;
                        padding: 15px;
                        border-bottom: 1px solid #e0e0e0;
                        display: flex;
                        flex-direction: column;
                        gap: 10px;
                    }
                    .system-prompt-area {
                        display: flex;
                        flex-direction: column;
                        gap: 8px;
                    }
                    .system-prompt-label {
                        font-size: 12px;
                        font-weight: bold;
                        color: #666;
                    }
                    .prompt-input-row {
                        display: flex;
                        gap: 10px;
                        align-items: stretch;
                    }
                    #systemPromptInput {
                        flex: 1;
                        padding: 10px;
                        border: 2px solid #e0e0e0;
                        border-radius: 8px;
                        font-size: 13px;
                        font-family: monospace;
                        resize: vertical;
                        min-height: 80px;
                        max-height: 200px;
                        outline: none;
                        transition: border-color 0.3s;
                    }
                    #systemPromptInput:focus {
                        border-color: #667eea;
                    }
                    .control-buttons {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        gap: 10px;
                    }
                    .client-selector-area {
                        display: flex;
                        align-items: center;
                        gap: 10px;
                    }
                    .client-selector-label {
                        font-size: 13px;
                        font-weight: bold;
                        color: #666;
                        white-space: nowrap;
                    }
                    #clientSelector {
                        padding: 8px 12px;
                        border: 2px solid #e0e0e0;
                        border-radius: 6px;
                        font-size: 13px;
                        font-weight: bold;
                        background: white;
                        cursor: pointer;
                        outline: none;
                        transition: border-color 0.3s;
                    }
                    #clientSelector:focus {
                        border-color: #667eea;
                    }
                    .control-btn {
                        padding: 8px 16px;
                        border: none;
                        border-radius: 6px;
                        font-size: 13px;
                        font-weight: bold;
                        cursor: pointer;
                        transition: all 0.2s;
                    }
                    .control-btn:hover:not(:disabled) {
                        transform: translateY(-1px);
                        box-shadow: 0 2px 8px rgba(0,0,0,0.15);
                    }
                    .control-btn:disabled {
                        opacity: 0.5;
                        cursor: not-allowed;
                    }
                    .btn-primary {
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        color: white;
                    }
                    .btn-danger {
                        background: #e74c3c;
                        color: white;
                    }
                    .temperature-slider-area {
                        display: flex;
                        align-items: center;
                        gap: 15px;
                        padding: 10px 0;
                    }
                    .temperature-label {
                        font-size: 13px;
                        font-weight: bold;
                        color: #666;
                        white-space: nowrap;
                    }
                    .slider-container {
                        flex: 1;
                        display: flex;
                        align-items: center;
                        gap: 10px;
                    }
                    #temperatureSlider {
                        flex: 1;
                        height: 6px;
                        border-radius: 3px;
                        background: #e0e0e0;
                        outline: none;
                        -webkit-appearance: none;
                    }
                    #temperatureSlider::-webkit-slider-thumb {
                        -webkit-appearance: none;
                        appearance: none;
                        width: 18px;
                        height: 18px;
                        border-radius: 50%;
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        cursor: pointer;
                        box-shadow: 0 2px 4px rgba(0,0,0,0.2);
                    }
                    #temperatureSlider::-moz-range-thumb {
                        width: 18px;
                        height: 18px;
                        border-radius: 50%;
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        cursor: pointer;
                        border: none;
                        box-shadow: 0 2px 4px rgba(0,0,0,0.2);
                    }
                    #temperatureValue {
                        font-size: 14px;
                        font-weight: bold;
                        color: #667eea;
                        min-width: 35px;
                        text-align: right;
                    }
                    .max-tokens-container {
                        display: flex;
                        align-items: center;
                        gap: 10px;
                        margin-left: 20px;
                    }
                    .max-tokens-label {
                        font-size: 13px;
                        font-weight: bold;
                        color: #666;
                        white-space: nowrap;
                    }
                    #maxTokensInput {
                        width: 100px;
                        padding: 6px 10px;
                        border: 2px solid #e0e0e0;
                        border-radius: 6px;
                        font-size: 14px;
                        font-weight: bold;
                        color: #667eea;
                        text-align: center;
                        outline: none;
                        transition: border-color 0.3s;
                    }
                    #maxTokensInput:focus {
                        border-color: #667eea;
                    }
                    #maxTokensInput::-webkit-inner-spin-button,
                    #maxTokensInput::-webkit-outer-spin-button {
                        -webkit-appearance: none;
                        margin: 0;
                    }
                    #maxTokensInput[type=number] {
                        -moz-appearance: textfield;
                    }
                    .auto-summarize-container {
                        display: flex;
                        align-items: center;
                        gap: 10px;
                        margin-left: 20px;
                    }
                    .auto-summarize-label {
                        font-size: 13px;
                        font-weight: bold;
                        color: #666;
                        white-space: nowrap;
                    }
                    #autoSummarizeThresholdInput {
                        width: 80px;
                        padding: 6px 10px;
                        border: 2px solid #e0e0e0;
                        border-radius: 6px;
                        font-size: 14px;
                        font-weight: bold;
                        color: #667eea;
                        text-align: center;
                        outline: none;
                        transition: border-color 0.3s;
                    }
                    #autoSummarizeThresholdInput:focus {
                        border-color: #667eea;
                    }
                    #autoSummarizeThresholdInput::-webkit-inner-spin-button,
                    #autoSummarizeThresholdInput::-webkit-outer-spin-button {
                        -webkit-appearance: none;
                        margin: 0;
                    }
                    #autoSummarizeThresholdInput[type=number] {
                        -moz-appearance: textfield;
                    }
                    @media (max-width: 600px) {
                        .controls-area {
                            padding: 10px;
                        }
                        #systemPromptInput {
                            min-height: 60px;
                            font-size: 12px;
                        }
                        .prompt-input-row {
                            flex-direction: column;
                        }
                        .control-btn {
                            width: 100%;
                        }
                    }
                    .chat-box {
                        flex: 1;
                        overflow-y: auto;
                        padding: 20px;
                        background: #f5f5f5;
                    }
                    .message {
                        margin-bottom: 15px;
                        animation: fadeIn 0.3s ease-in;
                    }
                    @keyframes fadeIn {
                        from { opacity: 0; transform: translateY(10px); }
                        to { opacity: 1; transform: translateY(0); }
                    }
                    .message-label {
                        font-size: 12px;
                        font-weight: bold;
                        margin-bottom: 5px;
                        color: #666;
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                    }
                    .user-label { color: #667eea; }
                    .ai-label { color: #764ba2; }
                    .message-time {
                        font-size: 10px;
                        font-weight: normal;
                        color: #999;
                        font-family: 'Courier New', Courier, monospace;
                    }
                    .message-content {
                        background: white;
                        padding: 12px 16px;
                        border-radius: 12px;
                        box-shadow: 0 2px 5px rgba(0,0,0,0.1);
                        word-wrap: break-word;
                    }
                    .user-message .message-content {
                        background: #667eea;
                        color: white;
                        margin-left: auto;
                        max-width: 80%;
                    }
                    .ai-message .message-content {
                        background: white;
                        color: #333;
                        max-width: 80%;
                    }
                    .message-content h1, .message-content h2, .message-content h3,
                    .message-content h4, .message-content h5, .message-content h6 {
                        margin: 12px 0 8px 0;
                        font-weight: bold;
                        line-height: 1.3;
                    }
                    .message-content h1 { font-size: 1.8em; }
                    .message-content h2 { font-size: 1.5em; }
                    .message-content h3 { font-size: 1.3em; }
                    .message-content h4 { font-size: 1.1em; }
                    .message-content h5 { font-size: 1em; }
                    .message-content h6 { font-size: 0.9em; }
                    .message-content p {
                        margin: 8px 0;
                        line-height: 1.6;
                    }
                    .message-content code {
                        background: #f4f4f4;
                        padding: 2px 6px;
                        border-radius: 4px;
                        font-family: 'Courier New', Courier, monospace;
                        font-size: 0.9em;
                    }
                    .user-message .message-content code {
                        background: rgba(255, 255, 255, 0.2);
                    }
                    .message-content pre {
                        background: #2d2d2d;
                        color: #f8f8f2;
                        padding: 12px;
                        border-radius: 6px;
                        overflow-x: auto;
                        margin: 8px 0;
                    }
                    .message-content pre code {
                        background: transparent;
                        padding: 0;
                        color: inherit;
                    }
                    .message-content ul, .message-content ol {
                        margin: 8px 0;
                        padding-left: 24px;
                    }
                    .message-content li {
                        margin: 4px 0;
                        line-height: 1.6;
                    }
                    .message-content blockquote {
                        border-left: 4px solid #667eea;
                        padding-left: 12px;
                        margin: 8px 0;
                        color: #666;
                        font-style: italic;
                    }
                    .message-content a {
                        color: #667eea;
                        text-decoration: none;
                    }
                    .message-content a:hover {
                        text-decoration: underline;
                    }
                    .message-content table {
                        border-collapse: collapse;
                        width: 100%;
                        margin: 8px 0;
                    }
                    .message-content th, .message-content td {
                        border: 1px solid #ddd;
                        padding: 8px;
                        text-align: left;
                    }
                    .message-content th {
                        background: #f4f4f4;
                        font-weight: bold;
                    }
                    .message-content hr {
                        border: none;
                        border-top: 2px solid #e0e0e0;
                        margin: 16px 0;
                    }
                    .message-content strong {
                        font-weight: bold;
                    }
                    .message-content em {
                        font-style: italic;
                    }
                    .input-area {
                        padding: 20px;
                        background: white;
                        border-top: 1px solid #e0e0e0;
                        display: flex;
                        gap: 10px;
                    }
                    #messageInput {
                        flex: 1;
                        padding: 12px 16px;
                        border: 2px solid #e0e0e0;
                        border-radius: 10px;
                        font-size: 14px;
                        font-family: inherit;
                        outline: none;
                        transition: border-color 0.3s;
                        resize: none;
                        min-height: 96px;
                        max-height: 150px;
                        overflow-y: auto;
                    }
                    #messageInput:focus {
                        border-color: #667eea;
                    }
                    #sendButton {
                        padding: 12px 30px;
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        color: white;
                        border: none;
                        border-radius: 25px;
                        font-size: 14px;
                        font-weight: bold;
                        cursor: pointer;
                        transition: transform 0.2s, box-shadow 0.2s;
                    }
                    #sendButton:hover:not(:disabled) {
                        transform: translateY(-2px);
                        box-shadow: 0 5px 15px rgba(102, 126, 234, 0.4);
                    }
                    #sendButton:disabled {
                        opacity: 0.6;
                        cursor: not-allowed;
                    }
                    .loading {
                        text-align: center;
                        color: #999;
                        font-style: italic;
                        padding: 10px;
                    }
                """)
            }
        }
    }
    body {
        div(classes = "container") {
            div(classes = "header") { +"AI Chat Assistant" }

            div(classes = "controls-area") {
                div(classes = "control-buttons") {
                    div(classes = "client-selector-area") {
                        span(classes = "client-selector-label") { +"API Client:" }
                        select {
                            id = "clientSelector"
                            option {
                                value = "YandexGPT Pro 5.1"
                                selected = true
                                +"YandexGPT Pro 5.1"
                            }
                            option {
                                value = "GigaChat 2 Lite"
                                +"GigaChat 2 Lite"
                            }
                        }
                    }
                    div(classes = "auto-summarize-container") {
                        div(classes = "auto-summarize-label") { +"Auto (msgs):" }
                        input {
                            type = InputType.number
                            id = "autoSummarizeThresholdInput"
                            name = "autoSummarizeThreshold"
                            attributes["min"] = "0"
                            attributes["max"] = "20"
                            attributes["step"] = "1"
                            attributes["value"] = "0"
                        }
                    }
                    button {
                        id = "clearHistoryButton"
                        classes = setOf("control-btn", "btn-danger")
                        +"Clear history"
                    }
                }

                div(classes = "system-prompt-area") {
                    div(classes = "prompt-input-row") {
                        button {
                            id = "setPromptButton"
                            classes = setOf("control-btn", "btn-primary")
                            +"Set"
                        }
                        textArea {
                            id = "systemPromptInput"
                            placeholder = "Введите системный промпт..."
                        }
                    }
                }

                div(classes = "temperature-slider-area") {
                    div(classes = "temperature-label") { +"Temperature:" }
                    div(classes = "slider-container") {
                        input {
                            type = InputType.range
                            id = "temperatureSlider"
                            name = "temperature"
                            attributes["min"] = "0"
                            attributes["max"] = "1.0"
                            attributes["step"] = "0.1"
                            attributes["value"] = "0"
                        }
                        span {
                            id = "temperatureValue"
                            +"0.0"
                        }
                    }
                    div(classes = "max-tokens-container") {
                        div(classes = "max-tokens-label") { +"Max Tokens:" }
                        input {
                            type = InputType.number
                            id = "maxTokensInput"
                            name = "maxTokens"
                            attributes["min"] = "1"
                            attributes["max"] = "10000"
                            attributes["value"] = "100"
                        }
                    }
                }
            }

            div(classes = "chat-box") { id = "chatBox" }

            div(classes = "input-area") {
                textArea {
                    id = "messageInput"
                    placeholder = "Введите сообщение... (Shift+Enter для новой строки)"
                    rows = "1"
                }
                button {
                    id = "sendButton"
                    +"Send"
                }
            }
        }
        script {
            unsafe {
                raw(
                    $$"""
                    const chatBox = document.getElementById('chatBox');
                    const messageInput = document.getElementById('messageInput');
                    const sendButton = document.getElementById('sendButton');
                    const systemPromptInput = document.getElementById('systemPromptInput');
                    const setPromptButton = document.getElementById('setPromptButton');
                    const clearHistoryButton = document.getElementById('clearHistoryButton');
                    const temperatureSlider = document.getElementById('temperatureSlider');
                    const temperatureValue = document.getElementById('temperatureValue');
                    const maxTokensInput = document.getElementById('maxTokensInput');
                    const autoSummarizeThresholdInput = document.getElementById('autoSummarizeThresholdInput');
                    const clientSelector = document.getElementById('clientSelector');

                    const loadTemperature = async () => {
                        try {
                            const response = await fetch('/api/temperature');
                            if (response.ok) {
                                const data = await response.json();
                                temperatureSlider.value = data.temperature;
                                temperatureValue.textContent = data.temperature.toFixed(1);
                            }
                        } catch (error) {
                            console.error('Failed to load temperature:', error);
                        }
                    };

                    const updateTemperature = async (value) => {
                        try {
                            const response = await fetch('/api/temperature', {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/json' },
                                body: JSON.stringify({ temperature: parseFloat(value) })
                            });

                            if (response.ok) {
                                const messageDiv = document.createElement('div');
                                messageDiv.className = 'message ai-message';

                                const label = document.createElement('div');
                                label.className = 'message-label ai-label';

                                const labelText = document.createElement('span');
                                labelText.textContent = 'System';

                                const timeStamp = document.createElement('span');
                                timeStamp.className = 'message-time';
                                timeStamp.textContent = formatTimestamp();

                                label.appendChild(labelText);
                                label.appendChild(timeStamp);

                                const content = document.createElement('div');
                                content.className = 'message-content';
                                content.textContent = `Установлена температура ${value.toFixed(1)}`;
                                content.style.fontStyle = 'italic';
                                content.style.color = '#999';

                                messageDiv.appendChild(label);
                                messageDiv.appendChild(content);
                                chatBox.appendChild(messageDiv);
                                chatBox.scrollTop = chatBox.scrollHeight;
                            } else {
                                console.error('Failed to update temperature');
                            }
                        } catch (error) {
                            console.error('Error updating temperature:', error);
                        }
                    };

                    temperatureSlider.addEventListener('input', (e) => {
                        const value = parseFloat(e.target.value);
                        temperatureValue.textContent = value.toFixed(1);
                    });
                    temperatureSlider.addEventListener('change', (e) => {
                        const value = parseFloat(e.target.value);
                        updateTemperature(value);
                    });

                    const loadMaxTokens = async () => {
                        try {
                            const response = await fetch('/api/max-tokens');
                            if (response.ok) {
                                const data = await response.json();
                                maxTokensInput.value = data.maxTokens;
                            }
                        } catch (error) {
                            console.error('Failed to load max tokens:', error);
                        }
                    };

                    const updateMaxTokens = async (value) => {
                        try {
                            const response = await fetch('/api/max-tokens', {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/json' },
                                body: JSON.stringify({ maxTokens: parseInt(value) })
                            });

                            if (response.ok) {
                                const messageDiv = document.createElement('div');
                                messageDiv.className = 'message ai-message';

                                const label = document.createElement('div');
                                label.className = 'message-label ai-label';

                                const labelText = document.createElement('span');
                                labelText.textContent = 'System';

                                const timeStamp = document.createElement('span');
                                timeStamp.className = 'message-time';
                                timeStamp.textContent = formatTimestamp();

                                label.appendChild(labelText);
                                label.appendChild(timeStamp);

                                const content = document.createElement('div');
                                content.className = 'message-content';
                                content.textContent = `Установлено максимальное количество токенов: ${value}`;
                                content.style.fontStyle = 'italic';
                                content.style.color = '#999';

                                messageDiv.appendChild(label);
                                messageDiv.appendChild(content);
                                chatBox.appendChild(messageDiv);
                                chatBox.scrollTop = chatBox.scrollHeight;
                            } else {
                                console.error('Failed to update max tokens');
                            }
                        } catch (error) {
                            console.error('Error updating max tokens:', error);
                        }
                    };

                    maxTokensInput.addEventListener('input', (e) => {
                        // Разрешаем только цифры
                        e.target.value = e.target.value.replace(/[^0-9]/g, '');
                    });

                    maxTokensInput.addEventListener('keydown', (e) => {
                        if (e.key === 'Enter') {
                            e.preventDefault();
                            maxTokensInput.blur();
                        }
                    });

                    maxTokensInput.addEventListener('change', (e) => {
                        let value = parseInt(e.target.value);
                        if (isNaN(value) || value < 1) value = 1;
                        if (value > 10000) value = 10000;
                        maxTokensInput.value = value;
                        updateMaxTokens(value);
                    });

                    const loadAutoSummarizeThreshold = async () => {
                        try {
                            const response = await fetch('/api/auto-summarize-threshold');
                            if (response.ok) {
                                const data = await response.json();
                                autoSummarizeThresholdInput.value = data.threshold;
                            }
                        } catch (error) {
                            console.error('Failed to load auto-summarize threshold:', error);
                        }
                    };

                    const updateAutoSummarizeThreshold = async (value) => {
                        try {
                            const response = await fetch('/api/auto-summarize-threshold', {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/json' },
                                body: JSON.stringify({ threshold: parseInt(value) })
                            });

                            if (response.ok) {
                                const messageDiv = document.createElement('div');
                                messageDiv.className = 'message ai-message';

                                const label = document.createElement('div');
                                label.className = 'message-label ai-label';

                                const labelText = document.createElement('span');
                                labelText.textContent = 'System';

                                const timeStamp = document.createElement('span');
                                timeStamp.className = 'message-time';
                                timeStamp.textContent = formatTimestamp();

                                label.appendChild(labelText);
                                label.appendChild(timeStamp);

                                const content = document.createElement('div');
                                content.className = 'message-content';
                                const thresholdText = value === '0'
                                    ? 'Автосуммаризация отключена'
                                    : `Установлен порог автосуммаризации: ${value} сообщений`;
                                content.textContent = thresholdText;
                                content.style.fontStyle = 'italic';
                                content.style.color = '#999';

                                messageDiv.appendChild(label);
                                messageDiv.appendChild(content);
                                chatBox.appendChild(messageDiv);
                                chatBox.scrollTop = chatBox.scrollHeight;
                            } else {
                                console.error('Failed to update auto-summarize threshold');
                            }
                        } catch (error) {
                            console.error('Error updating auto-summarize threshold:', error);
                        }
                    };

                    autoSummarizeThresholdInput.addEventListener('input', (e) => {
                        // Разрешаем только цифры
                        e.target.value = e.target.value.replace(/[^0-9]/g, '');
                    });

                    autoSummarizeThresholdInput.addEventListener('keydown', (e) => {
                        if (e.key === 'Enter') {
                            e.preventDefault();
                            autoSummarizeThresholdInput.blur();
                        }
                    });

                    autoSummarizeThresholdInput.addEventListener('change', (e) => {
                        let value = parseInt(e.target.value);
                        if (isNaN(value) || value < 0) value = 0;
                        if (value > 20) value = 20;
                        autoSummarizeThresholdInput.value = value;
                        updateAutoSummarizeThreshold(value);
                    });

                    const loadSystemPrompt = async () => {
                        try {
                            const response = await fetch('/api/system-prompt');
                            if (response.ok) {
                                const data = await response.json();
                                systemPromptInput.value = data.prompt;
                            }
                        } catch (error) {
                            console.error('Failed to load system prompt:', error);
                        }
                    };

                    const setSystemPrompt = async () => {
                        const prompt = systemPromptInput.value.trim();
                        if (!prompt) {
                            alert('System prompt не может быть пустым');
                            return;
                        }

                        try {
                            setPromptButton.disabled = true;
                            const response = await fetch('/api/system-prompt', {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/json' },
                                body: JSON.stringify({ prompt })
                            });

                            if (response.ok) {
                                const messageDiv = document.createElement('div');
                                messageDiv.className = 'message ai-message';

                                const label = document.createElement('div');
                                label.className = 'message-label ai-label';

                                const labelText = document.createElement('span');
                                labelText.textContent = 'System';

                                const timeStamp = document.createElement('span');
                                timeStamp.className = 'message-time';
                                timeStamp.textContent = formatTimestamp();

                                label.appendChild(labelText);
                                label.appendChild(timeStamp);

                                const content = document.createElement('div');
                                content.className = 'message-content';
                                content.textContent = `Установлен системный промпт: ${prompt}`;
                                content.style.fontStyle = 'italic';
                                content.style.color = '#999';

                                messageDiv.appendChild(label);
                                messageDiv.appendChild(content);
                                chatBox.appendChild(messageDiv);
                                chatBox.scrollTop = chatBox.scrollHeight;
                            } else {
                                alert('Ошибка при обновлении system prompt');
                            }
                        } catch (error) {
                            alert('Ошибка сети: ' + error.message);
                        } finally {
                            setPromptButton.disabled = false;
                        }
                    };

                    const addSystemMessage = (text) => {
                        const messageDiv = document.createElement('div');
                        messageDiv.className = 'message ai-message';

                        const label = document.createElement('div');
                        label.className = 'message-label ai-label';

                        const labelText = document.createElement('span');
                        labelText.textContent = 'System';

                        const timeStamp = document.createElement('span');
                        timeStamp.className = 'message-time';
                        timeStamp.textContent = formatTimestamp();

                        label.appendChild(labelText);
                        label.appendChild(timeStamp);

                        const content = document.createElement('div');
                        content.className = 'message-content';
                        content.innerHTML = marked.parse(text);
                        content.style.fontStyle = 'italic';
                        content.style.color = '#999';

                        messageDiv.appendChild(label);
                        messageDiv.appendChild(content);
                        chatBox.appendChild(messageDiv);
                        chatBox.scrollTop = chatBox.scrollHeight;
                    };

                    const clearHistory = async () => {
                        try {
                            clearHistoryButton.disabled = true;
                            const response = await fetch('/api/clear-history', {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/json' }
                            });

                            if (response.ok) {
                                addSystemMessage('История сообщений была очищена');
                            } else {
                                alert('Ошибка при очистке истории');
                            }
                        } catch (error) {
                            alert('Ошибка сети: ' + error.message);
                        } finally {
                            clearHistoryButton.disabled = false;
                        }
                    };

                    const displaySummarizeResult = (data) => {
                        // Обновляем системный промпт в UI
                        systemPromptInput.value = data.newSystemPrompt;

                        // Формируем сообщение со статистикой
                        const compressionSign = data.compressionPercent > 0 ? '↓' : '↑';
                        const message = `**История диалога суммаризована**\n\n` +
                            `📝 Сообщений обработано: **${data.oldMessagesCount}**\n` +
                            `📊 Токенов до суммаризации: **${data.oldTokensCount}**\n` +
                            `✨ Токенов после суммаризации: **${data.newTokensCount}**\n` +
                            `${compressionSign} Сжатие контекста: **${Math.abs(data.compressionPercent)}%**`;

                        addSystemMessage(message);
                    };

                    const checkAndSummarize = async () => {
                        const threshold = parseInt(autoSummarizeThresholdInput.value);
                        if (threshold === 0) return;

                        try {
                            const historyResponse = await fetch('/api/message-history');
                            if (historyResponse.ok) {
                                const historyData = await historyResponse.json();
                                const userMessagesCount = historyData.messages.filter(msg => msg.role === 'user').length;

                                if (userMessagesCount >= threshold) {
                                    const summarizeResponse = await fetch('/api/summarize', {
                                        method: 'POST',
                                        headers: { 'Content-Type': 'application/json' }
                                    });

                                    if (summarizeResponse.ok) {
                                        const data = await summarizeResponse.json();
                                        displaySummarizeResult(data);
                                    }
                                }
                            }
                        } catch (error) {
                            console.error('Auto-summarization failed:', error);
                        }
                    };

                    const formatTimestamp = () => {
                        const now = new Date();
                        const hours = String(now.getHours()).padStart(2, '0');
                        const minutes = String(now.getMinutes()).padStart(2, '0');
                        const seconds = String(now.getSeconds()).padStart(2, '0');
                        const milliseconds = String(now.getMilliseconds()).padStart(3, '0');
                        return `${hours}:${minutes}:${seconds}.${milliseconds}`;
                    };

                    const addMessage = (text, isUser) => {
                        const messageDiv = document.createElement('div');
                        messageDiv.className = `message ${isUser ? 'user' : 'ai'}-message`;

                        const label = document.createElement('div');
                        label.className = `message-label ${isUser ? 'user' : 'ai'}-label`;

                        const labelText = document.createElement('span');
                        labelText.textContent = isUser ? 'You' : 'AI';

                        const timeStamp = document.createElement('span');
                        timeStamp.className = 'message-time';
                        timeStamp.textContent = formatTimestamp();

                        label.appendChild(labelText);
                        label.appendChild(timeStamp);

                        const content = document.createElement('div');
                        content.className = 'message-content';

                        // Для AI сообщений используем Markdown рендеринг
                        if (isUser) {
                            content.textContent = text;
                        } else {
                            content.innerHTML = marked.parse(text);
                        }

                        messageDiv.appendChild(label);
                        messageDiv.appendChild(content);
                        chatBox.appendChild(messageDiv);
                        chatBox.scrollTop = chatBox.scrollHeight;
                    };

                    const sendMessage = async () => {
                        const message = messageInput.value.trim();
                        if (!message) return;

                        sendButton.disabled = true;
                        addMessage(message, true);
                        messageInput.value = '';

                        const loadingDiv = document.createElement('div');
                        loadingDiv.className = 'loading';
                        loadingDiv.textContent = 'Думаю...';
                        chatBox.appendChild(loadingDiv);

                        try {
                            const response = await fetch('/api/send', {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/json' },
                                body: JSON.stringify({ message })
                            });

                            chatBox.removeChild(loadingDiv);

                            if (response.ok) {
                                const data = await response.json();
                                addMessage(data.answer + '\n\n' + data.result, false);

                                // Проверяем необходимость автоматической суммаризации
                                await checkAndSummarize();
                            } else {
                                addMessage('Ошибка при получении ответа', false);
                            }
                        } catch (error) {
                            chatBox.removeChild(loadingDiv);
                            addMessage('Ошибка сети: ' + error.message, false);
                        } finally {
                            sendButton.disabled = false;
                            messageInput.focus();
                        }
                    };

                    const switchClient = async (clientName) => {
                        try {
                            clientSelector.disabled = true;
                            const response = await fetch('/api/switch-client', {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/json' },
                                body: JSON.stringify({ clientName })
                            });

                            if (response.ok) {
                                const data = await response.json();

                                // Очищаем чат перед загрузкой истории нового клиента
                                chatBox.innerHTML = '';

                                // Обновляем параметры
                                systemPromptInput.value = data.systemPrompt;
                                temperatureSlider.value = data.temperature;
                                temperatureValue.textContent = data.temperature.toFixed(1);

                                await loadSystemPrompt();
                                await loadTemperature();
                                await loadMaxTokens();
                                await loadAutoSummarizeThreshold();
                                await loadMessageHistory();

                                // Выводим сообщение в чат
                                const messageDiv = document.createElement('div');
                                messageDiv.className = 'message ai-message';

                                const label = document.createElement('div');
                                label.className = 'message-label ai-label';

                                const labelText = document.createElement('span');
                                labelText.textContent = 'System';

                                const timeStamp = document.createElement('span');
                                timeStamp.className = 'message-time';
                                timeStamp.textContent = formatTimestamp();

                                label.appendChild(labelText);
                                label.appendChild(timeStamp);

                                const content = document.createElement('div');
                                content.className = 'message-content';
                                content.textContent = `Переключено на ${data.clientName}`;
                                content.style.fontStyle = 'italic';
                                content.style.color = '#999';

                                messageDiv.appendChild(label);
                                messageDiv.appendChild(content);
                                chatBox.appendChild(messageDiv);
                                chatBox.scrollTop = chatBox.scrollHeight;
                            } else {
                                alert('Ошибка при переключении клиента');
                            }
                        } catch (error) {
                            alert('Ошибка сети: ' + error.message);
                        } finally {
                            clientSelector.disabled = false;
                        }
                    };

                    clientSelector.addEventListener('change', (e) => {
                        switchClient(e.target.value);
                    });

                    sendButton.addEventListener('click', sendMessage);
                    setPromptButton.addEventListener('click', setSystemPrompt);
                    clearHistoryButton.addEventListener('click', clearHistory);

                    messageInput.addEventListener('keydown', (e) => {
                        if (e.key === 'Enter' && !e.shiftKey && !sendButton.disabled) {
                            e.preventDefault();
                            sendMessage();
                        }
                    });

                    const loadMessageHistory = async () => {
                        try {
                            const response = await fetch('/api/message-history');
                            if (response.ok) {
                                const data = await response.json();
                                // Отображаем каждое сообщение из истории (уже отсортировано по message_order)
                                data.messages.forEach(msg => {
                                    if (msg.role === 'user') {
                                        addMessage(msg.content, true);
                                    } else if (msg.role === 'assistant') {
                                        addMessage(msg.content, false);
                                    }
                                    // system сообщения не отображаем в чате
                                });
                            }
                        } catch (error) {
                            console.error('Failed to load message history:', error);
                        }
                    };

                    loadSystemPrompt();
                    loadTemperature();
                    loadMaxTokens();
                    loadAutoSummarizeThreshold();
                    loadMessageHistory();
                    messageInput.focus();
                """
                )
            }
        }
    }
}
