# AI Agent - Контекст проекта для Claude

## Краткое описание
Kotlin-приложение на Ktor для работы с несколькими LLM провайдерами (YandexGPT, GigaChat) с поддержкой RAG, MCP серверов и веб-интерфейса.

## Архитектурные паттерны

### 1. Template Method Pattern (LLM клиенты)
**BaseApiClient** - абстрактный класс с общей логикой:
- Управление конфигурацией (config) и историей (messageHistory)
- Алгоритм выполнения запроса: добавление в историю → RAG аугментация → executeApiRequest() → сохранение ответа
- Автосохранение в БД при изменении config или истории

Наследники (YandexApiClient, GigaChatApiClient) реализуют:
- `executeApiRequest(context: RequestContext): StandardApiResponse` - специфика API
- `calculateCost(totalTokens: Int): Double` - формула стоимости

### 2. Dependency Injection (Koin)
Все зависимости регистрируются в `di/AppModule.kt`:
- HttpClient с разными конфигурациями (named): "standardHttpClient", "sslHttpClient", "mcpHttpClient"
- MCP Clients (named): "dbMcpClient", "httpMcpClient", "localMcpClient", "gitMcpClient"
- Map доступных клиентов: `named("availableClients")`

### 3. Repository Pattern
- `ClientConfigRepository` - конфигурация клиентов (system_prompt, temperature, max_tokens)
- `MessageHistoryRepository` - история диалогов
- `VectorStoreRepository` (интерфейс, SQLite имплементация) - векторное хранилище для RAG

## Ключевые абстракции

### ApiClientInterface
```kotlin
interface ApiClientInterface {
    fun sendRequest(query: String): ApiResponse
    var config: ApiClientConfig  // иммутабельная конфигурация
    val messageHistory: List<ChatMessage>
    fun clearMessages()
}
```

### ApiClientConfig (data class)
```kotlin
systemPrompt: String
temperature: Double
maxTokens: Int
autoSummarizeThreshold: Int
```

### RequestContext
Передается в executeApiRequest():
```kotlin
systemPrompt: String
messageHistory: List<ChatMessage>
temperature: Double
maxTokens: Int
```

## Структура БД

### chat_data.db (main)
- **client_config**: client_name (UNIQUE), system_prompt, temperature, max_tokens, auto_summarize_threshold
- **message_history**: client_name, role, content, message_order, created_at

### embeddings.db (vectors)
- **documents**: id, filename, file_type, upload_date, chunk_count, total_chars
- **chunks**: document_id, chunk_index, content, content_hash (UNIQUE), embedding (BLOB)

## Ключевые компоненты

### MCP (Model Context Protocol)
**McpClientManager** - ленивое подключение к MCP серверам (connect только при первом обращении)
**McpToolsService** - динамическое получение и выполнение tools:
- `getAvailableTools()` - список всех tools со всех серверов
- `executeTool(toolName, arguments)` - автоопределение сервера и выполнение

### RAG (Retrieval-Augmented Generation)
**Команда /help** - активирует RAG аугментацию промпта

**Pipeline**:
1. DocumentEmbeddingService - загрузка документов, chunking, создание embeddings
2. VectorSearchService - косинусное сходство + опциональный reranking (HuggingFace)
3. RagClient (OllamaRagClient) - аугментация промпта с контекстом

**Chunking strategies**: PlainTextChunkingStrategy, MarkdownChunkingStrategy (factory pattern)

### Суммаризация
**SummarizationService** использует отдельный GigaChatApiClient с промптом для сжатия:
- Автоматическая при достижении autoSummarizeThreshold
- Ручная через `/api/summarize`
- Сжатая история помещается в systemPrompt

## Контроллеры (endpoints)

### ChatController (ai-agent/src/main/kotlin/controllers/ChatController.kt)
- `POST /api/send` - отправка сообщения (body: ChatMessage)
- `GET /api/message-history` - получение истории
- `POST /api/clear-history` - очистка

### ClientController (ai-agent/src/main/kotlin/controllers/ClientController.kt)
- `GET /api/current-client` - текущий LLM провайдер
- `GET /api/available-clients` - список провайдеров
- `POST /api/switch-client` - переключение (body: ClientSwitchRequest)

### ConfigController (ai-agent/src/main/kotlin/controllers/ConfigController.kt)
- `GET/POST /api/system-prompt` (body: SystemPromptRequest)
- `GET/POST /api/temperature` (body: TemperatureRequest)
- `GET/POST /api/max-tokens` (body: MaxTokensRequest)
- `GET/POST /api/auto-summarize-threshold` (body: AutoSummarizeThresholdRequest)
- `POST /api/summarize` - ручная суммаризация

### DocumentController (ai-agent/src/main/kotlin/controllers/DocumentController.kt)
- `POST /api/document/upload` - загрузка документа (multipart)
- `GET /api/document/progress/{requestId}` - прогресс обработки

## Типичные паттерны

### Добавление нового LLM провайдера
1. Создать класс extends BaseApiClient
2. Реализовать executeApiRequest() и calculateCost()
3. Зарегистрировать в appModule (single<YourClient>)
4. Добавить в мапу availableClients

### Добавление нового MCP сервера
1. Создать Client instance в appModule: `single<Client>(named("yourMcpClient"))`
2. Добавить URL в .env: YOUR_MCP_SERVER_URL
3. Добавить в McpClientManager конструктор и методы get/connect

### Работа с конфигурацией клиента
```kotlin
// Чтение из БД автоматическое при инициализации
val config = apiClient.config

// Изменение (автоматически сохраняется в БД)
apiClient.config = config.copy(temperature = 0.7)
```

### Асинхронность
- Все API запросы внутренне асинхронные (suspend functions)
- Публичный API синхронный через runBlocking
- HttpClient обертки используют suspend

## Важные файлы (порядок изучения)

1. **Main.kt** (ai-agent/src/main/kotlin/Main.kt) - точка входа, инициализация, роутинг
2. **di/AppModule.kt** - все зависимости
3. **apiclients/ApiClientInterface.kt** + **BaseApiClient.kt** - контракт и базовая логика LLM клиентов
4. **apiclients/gigachat/GigaChatApiClient.kt** - пример реализации с MCP tools
5. **mcp/McpToolsService.kt** - динамическое получение/выполнение MCP tools
6. **embedding/rag/OllamaRagClient.kt** - RAG pipeline
7. **controllers/** - HTTP handlers
8. **database/tables/** - Exposed таблицы

## Соглашения о коде

- **Логирование**: SLF4J logger в каждом классе
- **Сериализация**: kotlinx.serialization (@Serializable)
- **БД операции**: синхронные через Exposed transactions
- **Config изменения**: всегда через .copy() для иммутабельности
- **История**: ChatMessage(role, content), role = "user"|"assistant"|"system"
- **Ошибки**: обрабатываются в BaseApiClient.handleError(), возвращают ApiResponse с сообщением

## Переменные окружения
См. `.env.example` в корне:
- YANDEX_API_KEY, GIGA_CHAT_API_KEY - обязательные
- *_MCP_SERVER_URL - опциональные (по умолчанию localhost:808X)
- USE_RERANKING, HUGGINGFACE_API_KEY, RERANKER_MODEL - для RAG reranking
- OLLAMA_BASE_URL - для embeddings (по умолчанию localhost:11434)

## Быстрый debug

**Проблемы с конфигурацией:**
- Проверь `configRepository.loadConfig(clientName)` в BaseApiClient.init
- Конфиг хранится в chat_data.db → client_config

**Проблемы с историей:**
- История загружается при инициализации клиента из БД
- Проверь `messageHistoryRepository.loadMessages(clientName)`

**MCP tools не работают:**
- Проверь `McpClientManager.connectToDbServer()` и другие connect методы
- MCP клиенты подключаются лениво при первом getDbClient()

**RAG не работает:**
- Проверь наличие Ollama на OLLAMA_BASE_URL
- Проверь наличие модели: `ollama list`
- Проверь embeddings.db → chunks таблицу

**Суммаризация:**
- Срабатывает автоматически при превышении autoSummarizeThreshold
- Проверь config.autoSummarizeThreshold
- Используется отдельный GigaChatApiClient с названием "gigachat-summarize"
