# AI Chat Assistant - Project Documentation

## Project Overview

**Type:** Kotlin Backend Application with Server-Side Rendering
**Framework:** Ktor Server 3.0.2
**Language:** Kotlin 2.2.20
**Build Tool:** Gradle
**DI Framework:** Koin 4.0.0
**Port:** 9999
**Main Class:** MainKt

This is a web-based AI chat application that integrates with multiple Russian LLM providers (YandexGPT and GigaChat). The application features a chat interface with configurable parameters, message history management, and conversation summarization capabilities.

## Technology Stack

### Backend
- **Ktor Server 3.0.2** - Web server framework
  - Netty engine
  - HTML Builder DSL for server-side rendering
  - Content Negotiation (JSON)
  - CORS support
- **Ktor Client 3.0.2** - HTTP client for API calls
  - CIO engine
  - Content Negotiation
  - SSL/TLS support
- **Koin 4.0.0** - Dependency Injection
- **Kotlinx Serialization** - JSON serialization

### Database & Persistence
- **SQLite 3.47.1.0** - Embedded database (file: `chat_data.db`)
- **Exposed ORM 0.57.0** - Type-safe SQL DSL for Kotlin
  - Core, DAO, and JDBC modules
- **Flyway 11.1.0** - Database migration tool
- **SLF4J 2.0.16** - Logging facade

### Frontend
- **Server-side HTML rendering** using Ktor HTML Builder DSL
- **Vanilla JavaScript** embedded in HTML
- **marked.js** for Markdown rendering

### External APIs
- **YandexGPT Pro 5.1** (Yandex Foundation Models API)
- **GigaChat 2 Lite** (Sber GigaChat API)

## Architecture

### Design Patterns

#### 1. Template Method Pattern
Base class `BaseApiClient` implements the common request flow, with provider-specific logic delegated to concrete implementations.

```kotlin
BaseApiClient (abstract)
├── sendRequest() - public synchronous wrapper
├── sendRequestAsync() - template method (final)
│   ├── Add user message to history
│   ├── executeApiRequest() - abstract (implemented by subclasses)
│   ├── Add assistant message to history
│   └── Calculate metrics and return response
├── executeApiRequest() - abstract
└── calculateCost() - abstract

YandexApiClient extends BaseApiClient
├── executeApiRequest() - Yandex API specifics
└── calculateCost() - Yandex pricing (0.4₽/1000 tokens)

GigaChatApiClient extends BaseApiClient
├── executeApiRequest() - GigaChat API specifics
├── getAccessToken() - OAuth token caching
└── calculateCost() - GigaChat pricing (1500₽/1M tokens)
```

#### 2. Dependency Injection (Koin)
All components are registered in `di/AppModule.kt`:
- HTTP clients (standard and SSL-enabled)
- API client instances (chat and summarization)
- Available clients map

#### 3. Repository Pattern
Persistence layer uses Repository pattern for database operations:
- `ClientConfigRepository` - manages API client configurations
- `MessageHistoryRepository` - manages chat message history
- Automatic save/load on configuration and history changes

#### 4. Interface-Based Design
`ApiClientInterface` defines the contract for all API clients, enabling polymorphism and easy testing.

### Project Structure

```
src/main/kotlin/
├── Main.kt                          # Entry point, server config, UI, routes
├── ApiClientInterface.kt            # API client contract
├── BaseApiClient.kt                 # Template Method base class
├── config/
│   ├── ApiClientConfig.kt           # Immutable config data class
│   └── ApiClientConfigBuilder.kt    # DSL for config creation
├── database/
│   ├── DatabaseManager.kt           # DB initialization & Flyway migrations
│   ├── tables/
│   │   ├── ClientConfigTable.kt     # Exposed table for client configs
│   │   └── MessageHistoryTable.kt   # Exposed table for message history
│   └── repository/
│       ├── ClientConfigRepository.kt    # Config persistence operations
│       └── MessageHistoryRepository.kt  # History persistence operations
├── dto/
│   ├── ChatMessage.kt               # Message model (role, content)
│   └── ResponseDto.kt               # ApiResponse, ApiResult
├── di/
│   └── AppModule.kt                 # Koin DI module
├── yandex/
│   ├── YandexApiClient.kt           # Yandex implementation
│   └── dto/YandexGptDto.kt          # Yandex-specific DTOs
└── sber/
    ├── GigaChatApiClient.kt         # GigaChat implementation
    └── dto/GigaChatDto.kt           # GigaChat-specific DTOs

resources/
├── truststore.jks                   # SSL certificate for GigaChat
└── db/
    └── migration/
        └── V1__Initial_schema.sql   # Flyway migration script
```

## Key Components

### 1. API Clients

**Interface:** `ApiClientInterface`
- `sendRequest(query: String): ApiResponse` - synchronous request
- `config: ApiClientConfig` - mutable configuration
- `messageHistory: List<ChatMessage>` - read-only history
- `clearMessages()` - clear conversation history

**Base Implementation:** `BaseApiClient`
- Manages message history internally (`_messageHistory`)
- Orchestrates request flow via Template Method
- Handles errors uniformly
- Automatically persists configuration and message history to database
- Loads saved state on initialization

**Concrete Implementations:**
- `YandexApiClient` - Yandex Foundation Models API
- `GigaChatApiClient` - Sber GigaChat API (with OAuth token caching)

### 2. Configuration System

**Data Class:** `ApiClientConfig`
```kotlin
data class ApiClientConfig(
    val systemPrompt: String,
    val temperature: Double,      // [0.0, 1.0]
    val maxTokens: Int            // [1, 10000]
)
```

**Builder DSL:** `apiClientConfig { }`
```kotlin
val config = apiClientConfig {
    systemPrompt = "Custom prompt"
    temperature = 0.7
    maxTokens = 2000
}
```

### 3. Message History

**Model:** `ChatMessage`
```kotlin
data class ChatMessage(
    val role: String,    // "system", "user", "assistant"
    val content: String
)
```

History is automatically maintained by `BaseApiClient`:
- User messages added before API call
- Assistant messages added after response
- Accessible via `messageHistory` property
- **Automatically persisted to SQLite database**
- Restored on application restart

### 4. Conversation Summarization

**Purpose:** Compress long conversation histories to save tokens

**Flow:**
1. User clicks "Summarize" button
2. Frontend calls `POST /api/summarize`
3. Backend:
   - Validates history is not empty
   - Formats structured text (system prompt + message history)
   - Calls `summarizeApiClient.sendRequest()`
   - Updates current client's system prompt with summary
   - Clears message history
4. Frontend:
   - Updates system prompt textarea
   - Displays system message with compression stats

**Summarize Client:** Special GigaChatApiClient instance with custom prompt optimized for summarization (see `di/AppModule.kt:59-74`)

### 5. Database Persistence

**Database:** SQLite (`chat_data.db` file in project root)

**Schema:**
```sql
-- Client configurations
CREATE TABLE client_config (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    client_name VARCHAR(50) UNIQUE NOT NULL,
    system_prompt TEXT NOT NULL,
    temperature REAL NOT NULL,
    max_tokens INTEGER NOT NULL
);

-- Message history
CREATE TABLE message_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    client_name VARCHAR(50) NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    message_order INTEGER NOT NULL,
    created_at INTEGER NOT NULL
);

-- Indexes for performance
CREATE INDEX idx_message_history_client_name ON message_history(client_name);
CREATE INDEX idx_message_history_order ON message_history(client_name, message_order);
```

**Repositories:**

`ClientConfigRepository`:
- `saveConfig(clientName, config)` - upsert configuration
- `loadConfig(clientName)` - retrieve configuration
- `deleteConfig(clientName)` - remove configuration

`MessageHistoryRepository`:
- `saveMessages(clientName, messages)` - replace all messages for client
- `loadMessages(clientName)` - retrieve ordered messages
- `clearMessages(clientName)` - delete all messages for client
- `getMessageCount(clientName)` - count messages

**Automatic Persistence:**
- Configuration changes saved immediately via `config` setter
- Message history saved after each API response
- Data loaded automatically on `BaseApiClient` initialization

**Database Migrations:**
- Managed by Flyway
- Migration scripts in `src/main/resources/db/migration/`
- Naming: `V{version}__{description}.sql`
- Applied automatically on application startup

### 6. HTTP Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/` | Main chat page (HTML) |
| POST | `/api/send` | Send message to AI |
| GET | `/api/system-prompt` | Get current system prompt |
| POST | `/api/system-prompt` | Update system prompt |
| GET | `/api/temperature` | Get current temperature |
| POST | `/api/temperature` | Update temperature |
| GET | `/api/max-tokens` | Get max tokens setting |
| POST | `/api/max-tokens` | Update max tokens |
| GET | `/api/message-history` | Get formatted history |
| POST | `/api/clear-history` | Clear message history |
| GET | `/api/current-client` | Get current client name |
| POST | `/api/switch-client` | Switch between clients |
| GET | `/api/available-clients` | List available clients |
| POST | `/api/summarize` | Summarize conversation |

## Configuration

### Environment Setup

**Required System Properties:**
- `yandexApiKey` - Yandex API key (IAM token)
- `gigaChatApiKey` - GigaChat API key (Base64 encoded credentials)

**Set via gradle.properties:**
```properties
yandexApiKey=YOUR_YANDEX_API_KEY
gigaChatApiKey=YOUR_GIGACHAT_API_KEY
```

### SSL Configuration (GigaChat)

GigaChat requires SSL certificate for API calls:
- Certificate stored in `src/main/resources/truststore.jks`
- Password: `changeit`
- Configured via SSL-enabled HttpClient in `AppModule.kt`

## Development Guidelines

### Adding New API Providers

1. **Create DTO classes** in `{provider}/dto/` package
2. **Extend BaseApiClient:**
   ```kotlin
   class NewProviderApiClient(
       httpClient: HttpClient,
       apiClientConfig: ApiClientConfig,
       clientName: String,
       configRepository: ClientConfigRepository,
       messageHistoryRepository: MessageHistoryRepository
   ) : BaseApiClient(httpClient, apiClientConfig, clientName, configRepository, messageHistoryRepository) {

       override suspend fun executeApiRequest(context: RequestContext): StandardApiResponse {
           // Provider-specific API call
       }

       override fun calculateCost(totalTokens: Int): Double {
           // Provider-specific pricing
       }
   }
   ```
3. **Register in Koin** (`di/AppModule.kt`)
4. **Add to availableClients map**
5. **Update UI** with new option in client selector

### State Management

- **Current client** stored in `apiClient` variable (Main.kt:63)
- **Client switching** updates reference and reloads configuration
- **History per client** - each client maintains its own message history
- **Configuration changes** applied via immutable copy: `config.copy(temperature = 0.8)`

### Server-Side Rendering

All UI is generated server-side using Ktor HTML Builder DSL:
```kotlin
fun HTML.chatPage() {
    head { /* styles, scripts */ }
    body {
        div(classes = "container") {
            // HTML structure
        }
        script {
            unsafe {
                raw("""
                    // Embedded JavaScript
                """)
            }
        }
    }
}
```

### JavaScript Patterns

**Key Functions:**
- `sendMessage()` - POST to `/api/send`
- `switchClient(name)` - POST to `/api/switch-client`
- `summarizeHistory()` - POST to `/api/summarize`
- `clearHistory()` - POST to `/api/clear-history`
- `addMessage(text, isUser)` - Add message to chat
- `addSystemMessage(text)` - Add system notification with markdown

### Error Handling

**Backend:**
- Try-catch in all suspend functions
- HTTP 400 for validation errors
- HTTP 500 for server errors
- Structured error responses: `{ "error": "message" }`

**Frontend:**
- Alert for critical errors
- System messages for user-facing errors
- Loading states during async operations

## Common Workflows

### 1. Sending a Message
```
User types message → sendMessage() →
POST /api/send →
apiClient.sendRequest() →
BaseApiClient adds to history →
executeApiRequest() (provider-specific) →
Add assistant response to history →
Return ApiResponse with metrics →
Display in chat with markdown rendering
```

### 2. Switching Clients
```
User selects client → switchClient() →
POST /api/switch-client →
Update apiClient reference →
Load new client's configuration →
Update UI (system prompt, temperature, maxTokens) →
Display system notification
```

### 3. Summarizing Conversation
```
User clicks Summarize → summarizeHistory() →
POST /api/summarize →
Validate history not empty →
Format structured text →
summarizeApiClient.sendRequest() →
Update current client's system prompt →
Clear message history →
Return compression stats →
Update UI and display stats message
```

## Performance Considerations

### Token Caching (GigaChat)
- OAuth tokens cached with expiration timestamp
- Automatic refresh when expired
- Reduces authentication overhead

### Message History
- Stored in memory during runtime
- **Automatically persisted to SQLite database**
- Restored from database on application startup
- Each API client maintains separate history
- Can be cleared manually or via summarization

### Request Timeouts
- Connect timeout: 60 seconds
- Request timeout: 120 seconds
- Configured in HttpClient engine

## Cost Tracking

Each API response includes cost calculation:
```kotlin
ApiResult(
    elapsedTime: Long,        // Request duration (ms)
    promptTokens: Int,        // Input tokens
    completionTokens: Int,    // Output tokens
    totalTokens: Int,         // Total tokens
    cost: Double              // Calculated cost (₽)
)
```

**Pricing:**
- **YandexGPT:** 0.4₽ per 1,000 tokens
- **GigaChat:** 1,500₽ per 1,000,000 tokens

## Testing

### Manual Testing
1. Start server: `./gradlew run`
2. Open browser: http://localhost:9999/
3. Test features:
   - Send messages to both providers
   - Switch between clients
   - Adjust temperature/maxTokens
   - Update system prompt
   - Clear history
   - Summarize conversation

### Building
```bash
./gradlew build
```

## Important Notes

### Authentication
- **YandexGPT:** Uses IAM token passed as Bearer token
- **GigaChat:** Uses OAuth 2.0 with client credentials (needs SSL)

### Message Roles
- `system` - System prompt (instructions for AI)
- `user` - User messages
- `assistant` - AI responses

### Immutability
- `ApiClientConfig` is immutable (data class)
- Changes create new instances via `copy()`
- Ensures thread-safety and predictable behavior

### UI Updates
- System prompt textarea syncs with backend
- Temperature/maxTokens sliders sync with backend
- All updates persist across client switches

## Troubleshooting

### NoDefinitionFoundException
- Check Koin registrations in `AppModule.kt`
- Ensure types match between registration and injection

### SSL Errors (GigaChat)
- Verify `truststore.jks` exists in resources
- Check SSL client configuration

### API Errors
- Verify API keys in gradle.properties
- Check token expiration (GigaChat)
- Review API rate limits

### Empty Responses
- Check system properties are loaded correctly
- Verify API endpoints are reachable
- Review server logs for errors

## Database Management

### Initialization

Database is initialized automatically on application startup via `DatabaseManager.init()`:
1. Creates SQLite DataSource (`jdbc:sqlite:./chat_data.db`)
2. Runs Flyway migrations from `classpath:db/migration`
3. Connects Exposed ORM to the database

### Adding Migrations

To add a new migration:
1. Create file `src/main/resources/db/migration/V{N}__{Description}.sql`
2. Write SQL DDL statements
3. Restart application - Flyway will apply automatically

Example:
```sql
-- V2__Add_user_preferences.sql
CREATE TABLE user_preferences (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id VARCHAR(100) NOT NULL,
    theme VARCHAR(20) DEFAULT 'light'
);
```

### Database Location

- **Development:** `./chat_data.db` in project root
- **File-based:** Portable across systems
- **Backup:** Simply copy the `.db` file

### Viewing Database

Use any SQLite client:
- **CLI:** `sqlite3 chat_data.db`
- **GUI:** DB Browser for SQLite, DBeaver, DataGrip
- **Query example:** `SELECT * FROM client_config;`

## Future Enhancements

Potential improvements:
- User authentication and multi-user support
- Streaming responses (SSE)
- File upload support
- Export conversation history (JSON/CSV)
- Cost analytics dashboard with charts
- Additional LLM provider integrations
- Database backup/restore UI
- Search across message history
