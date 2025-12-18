package views

import kotlinx.html.*

/**
 * View for rendering the main chat page
 */
fun HTML.chatPage() {
    head {
        title { +"AI Chat" }
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")

        // External libraries
        script {
            src = "https://cdn.jsdelivr.net/npm/marked/marked.min.js"
        }

        // Application CSS
        link {
            rel = "stylesheet"
            href = "/static/css/style.css"
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
                                +"YandexGPT Pro 5.1"
                            }
                            option {
                                value = "GigaChat 2 Lite"
                                selected = true
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
                    button {
                        id = "viewMcpToolsButton"
                        classes = setOf("control-btn", "btn-primary")
                        +"View MCP Tools"
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

            // MCP Tools Modal
            div {
                id = "mcpToolsModal"
                classes = setOf("modal-overlay")
                div(classes = "modal-content") {
                    div(classes = "modal-header") {
                        div(classes = "modal-title") { +"MCP Tools" }
                        button(classes = "modal-close") {
                            id = "closeMcpModalButton"
                            +"\u00D7"
                        }
                    }
                    div { id = "mcpToolsContainer" }
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

        // Application JavaScript
        script {
            src = "/static/js/app.js"
        }
    }
}
