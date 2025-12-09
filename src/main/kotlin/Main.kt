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
import yandex.YandexApiClient

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
data class ClientSwitchRequest(val clientName: String)

@Serializable
data class ClientSwitchResponse(val clientName: String, val systemPrompt: String, val temperature: Double)

// Карта всех доступных клиентов
private val availableClients = mapOf(
    "YandexGPT Pro 5.1" to YandexApiClient(),
    "GigaChat 2 Lite" to sber.GigaChatApiClient()
)

// Текущий активный клиент
private var currentClientName: String = "YandexGPT Pro 5.1"
private var apiClient: ApiClientInterface = availableClients.getValue(currentClientName)

fun main() {
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
        post("/api/clear-history") { handleClearHistory(call) }
        get("/api/current-client") { handleGetCurrentClient(call) }
        post("/api/switch-client") { handleSwitchClient(call) }
        get("/api/available-clients") { handleGetAvailableClients(call) }
    }
}

suspend fun handleSendMessage(call: ApplicationCall) {
    val request = call.receive<ChatRequest>()
    val apiResponse = apiClient.sendRequest(request.message)
    call.respond(ChatResponse(question = request.message, answer = apiResponse.message, result = apiResponse.result))
}

suspend fun handleGetSystemPrompt(call: ApplicationCall) {
    call.respond(SystemPromptResponse(prompt = apiClient.getSystemPrompt()))
}

suspend fun handleSetSystemPrompt(call: ApplicationCall) {
    val request = call.receive<SystemPromptRequest>()
    apiClient.setSystemPrompt(request.prompt)
    call.respond(SystemPromptResponse(prompt = request.prompt))
}

suspend fun handleClearHistory(call: ApplicationCall) {
    apiClient.clearMessages()
    call.respond(mapOf("status" to "ok"))
}

suspend fun handleGetTemperature(call: ApplicationCall) {
    call.respond(TemperatureResponse(temperature = apiClient.getTemperature()))
}

suspend fun handleSetTemperature(call: ApplicationCall) {
    val request = call.receive<TemperatureRequest>()
    apiClient.setTemperature(request.temperature)
    call.respond(TemperatureResponse(temperature = request.temperature))
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
                systemPrompt = apiClient.getSystemPrompt(),
                temperature = apiClient.getTemperature()
            )
        )
    } else {
        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Unknown client: ${request.clientName}"))
    }
}

fun HTML.chatPage() {
    head {
        title { +"AI Chat" }
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
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
                    }
                    .user-label { color: #667eea; }
                    .ai-label { color: #764ba2; }
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
                                label.textContent = 'System';

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
                                label.textContent = 'System';

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

                    const clearHistory = async () => {
                        try {
                            clearHistoryButton.disabled = true;
                            const response = await fetch('/api/clear-history', {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/json' }
                            });

                            if (response.ok) {
                                const messageDiv = document.createElement('div');
                                messageDiv.className = 'message ai-message';

                                const label = document.createElement('div');
                                label.className = 'message-label ai-label';
                                label.textContent = 'System';

                                const content = document.createElement('div');
                                content.className = 'message-content';
                                content.textContent = 'История сообщений была очищена';
                                content.style.fontStyle = 'italic';
                                content.style.color = '#999';

                                messageDiv.appendChild(label);
                                messageDiv.appendChild(content);
                                chatBox.appendChild(messageDiv);
                                chatBox.scrollTop = chatBox.scrollHeight;
                            } else {
                                alert('Ошибка при очистке истории');
                            }
                        } catch (error) {
                            alert('Ошибка сети: ' + error.message);
                        } finally {
                            clearHistoryButton.disabled = false;
                        }
                    };

                    const addMessage = (text, isUser) => {
                        const messageDiv = document.createElement('div');
                        messageDiv.className = `message ${isUser ? 'user' : 'ai'}-message`;

                        const label = document.createElement('div');
                        label.className = `message-label ${isUser ? 'user' : 'ai'}-label`;
                        label.textContent = isUser ? 'You' : 'AI';

                        const content = document.createElement('div');
                        content.className = 'message-content';
                        content.textContent = text;

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
                                addMessage(data.answer + '\n' + data.result, false);
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

                                // Обновляем параметры
                                systemPromptInput.value = data.systemPrompt;
                                temperatureSlider.value = data.temperature;
                                temperatureValue.textContent = data.temperature.toFixed(1);

                                // Выводим сообщение в чат
                                const messageDiv = document.createElement('div');
                                messageDiv.className = 'message ai-message';

                                const label = document.createElement('div');
                                label.className = 'message-label ai-label';
                                label.textContent = 'System';

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

                    loadSystemPrompt();
                    loadTemperature();
                    messageInput.focus();
                """
                )
            }
        }
    }
}
