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
data class ChatResponse(val question: String, val answer: String)

private val apiClient = YandexApiClient()
//private val apiClient = sber.GigaChatApiClient()

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
    }
}

suspend fun handleSendMessage(call: ApplicationCall) {
    val request = call.receive<ChatRequest>()
    val answer = apiClient.sendRequest(request.message)
    call.respond(ChatResponse(question = request.message, answer = answer))
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
                raw("""
                    const chatBox = document.getElementById('chatBox');
                    const messageInput = document.getElementById('messageInput');
                    const sendButton = document.getElementById('sendButton');

                    const addMessage = (text, isUser) => {
                        const messageDiv = document.createElement('div');
                        messageDiv.className = `message ${'$'}{isUser ? 'user' : 'ai'}-message`;

                        const label = document.createElement('div');
                        label.className = `message-label ${'$'}{isUser ? 'user' : 'ai'}-label`;
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
                                addMessage(data.answer, false);
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

                    sendButton.addEventListener('click', sendMessage);
                    messageInput.addEventListener('keydown', (e) => {
                        if (e.key === 'Enter' && !e.shiftKey && !sendButton.disabled) {
                            e.preventDefault();
                            sendMessage();
                        }
                    });

                    messageInput.focus();
                """)
            }
        }
    }
}
