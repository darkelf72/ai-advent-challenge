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
const useRagCheckbox = document.getElementById('useRagCheckbox');

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

// Use RAG checkbox handler
useRagCheckbox.addEventListener('change', (e) => {
    const newTemperature = e.target.checked ? 0.2 : 0.7;
    temperatureSlider.value = newTemperature;
    temperatureValue.textContent = newTemperature.toFixed(1);
    updateTemperature(newTemperature);
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
        const useRag = useRagCheckbox.checked;
        const response = await fetch('/api/send', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ message, useRag })
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

const mcpToolsModal = document.getElementById('mcpToolsModal');
const mcpToolsContainer = document.getElementById('mcpToolsContainer');
const viewMcpToolsButton = document.getElementById('viewMcpToolsButton');
const closeMcpModalButton = document.getElementById('closeMcpModalButton');

const loadMcpTools = async () => {
    try {
        viewMcpToolsButton.disabled = true;
        mcpToolsContainer.innerHTML = '<div class="loading">Загрузка инструментов...</div>';
        mcpToolsModal.classList.add('show');

        const response = await fetch('/api/mcp-tools');

        if (response.ok) {
            const data = await response.json();
            displayMcpTools(data.tools);
        } else {
            const error = await response.json();
            mcpToolsContainer.innerHTML = `<div style="color: red;">Ошибка: ${error.error}</div>`;
        }
    } catch (error) {
        mcpToolsContainer.innerHTML = `<div style="color: red;">Ошибка сети: ${error.message}</div>`;
    } finally {
        viewMcpToolsButton.disabled = false;
    }
};

const displayMcpTools = (tools) => {
    if (tools.length === 0) {
        mcpToolsContainer.innerHTML = '<div style="color: #999; text-align: center;">Нет доступных инструментов</div>';
        return;
    }

    mcpToolsContainer.innerHTML = '';

    tools.forEach(tool => {
        const toolDiv = document.createElement('div');
        toolDiv.className = 'tool-item';

        const nameDiv = document.createElement('div');
        nameDiv.className = 'tool-name';
        nameDiv.textContent = tool.name;

        const descDiv = document.createElement('div');
        descDiv.className = 'tool-description';
        descDiv.textContent = tool.description;

        const schemaLabel = document.createElement('div');
        schemaLabel.className = 'tool-schema-label';
        schemaLabel.textContent = 'Input Schema:';

        const schemaDiv = document.createElement('pre');
        schemaDiv.className = 'tool-schema';
        // Pretty print JSON
        try {
            const schema = JSON.parse(tool.inputSchema);
            schemaDiv.textContent = JSON.stringify(schema, null, 2);
        } catch (e) {
            schemaDiv.textContent = tool.inputSchema;
        }

        toolDiv.appendChild(nameDiv);
        toolDiv.appendChild(descDiv);
        toolDiv.appendChild(schemaLabel);
        toolDiv.appendChild(schemaDiv);
        mcpToolsContainer.appendChild(toolDiv);
    });
};

const closeMcpModal = () => {
    mcpToolsModal.classList.remove('show');
};

viewMcpToolsButton.addEventListener('click', loadMcpTools);
closeMcpModalButton.addEventListener('click', closeMcpModal);

// Close modal when clicking outside
mcpToolsModal.addEventListener('click', (e) => {
    if (e.target === mcpToolsModal) {
        closeMcpModal();
    }
});

// ============================================================
// Document Upload and Processing
// ============================================================

(function initDocumentUpload() {
    const loadDocumentButton = document.getElementById('loadDocumentButton');
    const documentFileInput = document.getElementById('documentFileInput');
    const documentProgressModal = document.getElementById('documentProgressModal');
    const uploadStatusMessage = document.getElementById('uploadStatusMessage');
    const progressBar = document.getElementById('progressBar');
    const progressText = document.getElementById('progressText');
    const closeProgressModalButton = document.getElementById('closeProgressModalButton');

    // Only initialize if all required elements exist
    if (!loadDocumentButton || !documentFileInput || !documentProgressModal ||
        !uploadStatusMessage || !progressBar || !progressText || !closeProgressModalButton) {
        console.log('Document upload feature not available - missing UI elements');
        return;
    }

    let progressCheckInterval = null;
    let currentFileName = '';

    // Open file dialog when button is clicked
    loadDocumentButton.addEventListener('click', () => {
        documentFileInput.click();
    });

    // Handle file selection
    documentFileInput.addEventListener('change', async (e) => {
        const file = e.target.files[0];
        if (!file) return;

        // Save file name before clearing input
        currentFileName = file.name;

        // Validate file type
        if (!file.name.endsWith('.txt')) {
            alert('Only .txt files are supported');
            return;
        }

        // Validate file size (10 MB)
        const maxSize = 10 * 1024 * 1024;
        if (file.size > maxSize) {
            alert('File size exceeds maximum allowed size of 10 MB');
            return;
        }

        // Show progress modal
        documentProgressModal.style.display = 'flex';
        uploadStatusMessage.textContent = 'Uploading document...';
        uploadStatusMessage.style.color = '#007bff';
        progressBar.style.width = '0%';
        progressText.textContent = '0 / 0 chunks (0%)';
        closeProgressModalButton.style.display = 'none';

        try {
            // Upload file
            const formData = new FormData();
            formData.append('file', file);

            const response = await fetch('/api/document/upload', {
                method: 'POST',
                body: formData
            });

            const result = await response.json();

            if (!result.success) {
                throw new Error(result.message || 'Upload failed');
            }

            const requestId = result.data.requestId;
            uploadStatusMessage.textContent = 'Processing document...';

            // Start polling for progress
            startProgressPolling(requestId);

        } catch (error) {
            console.error('Error uploading document:', error);
            uploadStatusMessage.textContent = `Error: ${error.message}`;
            uploadStatusMessage.style.color = '#dc3545';
            closeProgressModalButton.style.display = 'inline-block';
        }

        // Clear file input
        e.target.value = '';
    });

    // Start polling for progress
    function startProgressPolling(requestId) {
        // Clear any existing interval
        if (progressCheckInterval) {
            clearInterval(progressCheckInterval);
        }

        // Poll every 500ms
        progressCheckInterval = setInterval(async () => {
            try {
                const response = await fetch(`/api/document/progress/${requestId}`);
                const result = await response.json();

                if (!result.success) {
                    throw new Error(result.message || 'Failed to get progress');
                }

                const { current, total, percentage, status, error } = result.data;

                // Update progress bar and text
                progressBar.style.width = `${percentage}%`;
                progressText.textContent = `${current} / ${total} chunks (${percentage}%)`;

                if (status === 'COMPLETED') {
                    clearInterval(progressCheckInterval);
                    uploadStatusMessage.textContent = 'Document processed successfully!';
                    uploadStatusMessage.style.color = '#28a745';
                    progressBar.style.backgroundColor = '#28a745';
                    closeProgressModalButton.style.display = 'inline-block';

                    // Add success message to chat
                    addSystemMessage(`Document "${currentFileName || 'document'}" loaded successfully. Created ${total} embedding chunks.`);

                } else if (status === 'FAILED') {
                    clearInterval(progressCheckInterval);
                    uploadStatusMessage.textContent = `Processing failed: ${error || 'Unknown error'}`;
                    uploadStatusMessage.style.color = '#dc3545';
                    progressBar.style.backgroundColor = '#dc3545';
                    closeProgressModalButton.style.display = 'inline-block';
                }

            } catch (error) {
                console.error('Error checking progress:', error);
                clearInterval(progressCheckInterval);
                uploadStatusMessage.textContent = `Error: ${error.message}`;
                uploadStatusMessage.style.color = '#dc3545';
                closeProgressModalButton.style.display = 'inline-block';
            }
        }, 500);
    }

    // Close progress modal
    closeProgressModalButton.addEventListener('click', () => {
        documentProgressModal.style.display = 'none';
        if (progressCheckInterval) {
            clearInterval(progressCheckInterval);
            progressCheckInterval = null;
        }
    });

    // Helper function to add system message to chat
    function addSystemMessage(message) {
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
        content.textContent = message;
        content.style.fontStyle = 'italic';
        content.style.color = '#999';

        messageDiv.appendChild(label);
        messageDiv.appendChild(content);
        chatBox.appendChild(messageDiv);
        chatBox.scrollTop = chatBox.scrollHeight;
    }
})();

loadSystemPrompt();
loadTemperature();
loadMaxTokens();
loadAutoSummarizeThreshold();
loadMessageHistory();
messageInput.focus();
