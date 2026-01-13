# AI Agent

Многофункциональный AI агент с поддержкой нескольких LLM провайдеров, RAG (Retrieval-Augmented Generation), MCP (Model Context Protocol) серверов и веб-интерфейса для взаимодействия с языковыми моделями.

## Основные возможности

- **Множественные LLM провайдеры**: Поддержка YandexGPT Pro 5.1 и GigaChat 2 Lite с возможностью переключения между ними
- **MCP серверы**: Интеграция с Model Context Protocol для расширения функциональности через внешние инструменты
  - Database MCP Server (работа с базами данных)
  - HTTP MCP Server (HTTP запросы)
  - Local MCP Server (файловые операции)
  - Git MCP Server (операции с Git репозиториями)
- **RAG система**: Поддержка загрузки документов с созданием эмбеддингов и семантическим поиском
  - Векторное хранилище на SQLite
  - Интеграция с Ollama для создания эмбеддингов
  - Опциональный reranking с использованием HuggingFace моделей
- **Автоматическая суммаризация**: Сжатие длинных диалогов для экономии контекста
- **Веб-интерфейс**: Удобный чат-интерфейс для взаимодействия с AI
- **Настраиваемая конфигурация**: Регулировка температуры, максимального количества токенов, системного промпта
- **История сообщений**: Персистентное хранение диалогов в SQLite
- **Database migrations**: Автоматическое управление схемой БД через Flyway

## Технологический стек

- **Язык**: Kotlin
- **Фреймворк**: Ktor (Server + Client)
- **Dependency Injection**: Koin
- **ORM**: Exposed
- **База данных**: SQLite
- **Миграции**: Flyway
- **Сериализация**: kotlinx.serialization
- **MCP SDK**: io.modelcontextprotocol:kotlin-sdk

## Требования

- JDK 21+
- Gradle (обертка включена в проект)
- Ollama (для RAG функциональности)
- MCP серверы (опционально, если требуется расширенная функциональность)

## Установка и запуск

### 1. Настройка переменных окружения

Скопируйте файл `.env.example` в корень проекта и переименуйте в `.env`:

```bash
cp .env.example .env
```

Заполните необходимые API ключи:

```bash
# API Keys для AI сервисов
YANDEX_API_KEY=your_yandex_api_key_here
GIGA_CHAT_API_KEY=your_giga_chat_api_key_here

# MCP Server URLs (для Docker, настраиваются автоматически)
# DB_MCP_SERVER_URL=http://db-mcp-server:8081
# HTTP_MCP_SERVER_URL=http://http-mcp-server:8082
# LOCAL_MCP_SERVER_URL=http://localhost:8080
# GIT_MCP_SERVER_URL=http://git-mcp-server:8083

# RAG Reranking (опционально)
USE_RERANKING=false
# HUGGINGFACE_API_KEY=your_huggingface_token_here
# RERANKER_MODEL=BAAI/bge-reranker-v2-m3

# Ollama base URL
# OLLAMA_BASE_URL=http://localhost:11434
```

### 2. Установка Ollama (для RAG)

Если планируете использовать RAG функциональность:

```bash
# macOS
brew install ollama

# Запуск Ollama
ollama serve

# Установка модели для эмбеддингов
ollama pull nomic-embed-text
```

### 3. Запуск приложения

```bash
# Сборка и запуск
./gradlew :ai-agent:run

# Или через IDE (запустите Main.kt)
```

Приложение запустится на `http://localhost:9999`

### 4. Docker (опционально)

Если используете Docker Compose со всеми MCP серверами:

```bash
docker-compose up -d
```

## Структура проекта

```
ai-agent/
├── src/main/kotlin/
│   ├── apiclients/          # Клиенты для LLM провайдеров
│   │   ├── yandex/          # YandexGPT API клиент
│   │   ├── gigachat/        # GigaChat API клиент
│   │   └── config/          # Конфигурация клиентов
│   ├── controllers/         # HTTP контроллеры
│   │   ├── ChatController.kt
│   │   ├── ClientController.kt
│   │   ├── ConfigController.kt
│   │   └── DocumentController.kt
│   ├── database/            # Работа с БД
│   │   ├── DatabaseManager.kt
│   │   ├── EmbeddingDatabaseManager.kt
│   │   ├── tables/          # Таблицы Exposed
│   │   └── repository/      # Репозитории
│   ├── di/                  # Koin модули DI
│   │   └── AppModule.kt
│   ├── dto/                 # Data Transfer Objects
│   │   ├── request/
│   │   └── response/
│   ├── embedding/           # RAG система
│   │   ├── service/         # Сервисы эмбеддингов и поиска
│   │   ├── repository/      # Векторное хранилище
│   │   ├── rag/             # RAG клиенты
│   │   └── OllamaClient.kt
│   ├── mcp/                 # Model Context Protocol
│   │   ├── McpClientManager.kt
│   │   └── McpToolsService.kt
│   ├── service/             # Бизнес-логика
│   │   └── SummarizationService.kt
│   ├── views/               # HTML views
│   └── Main.kt              # Точка входа
├── src/main/resources/
│   ├── db/migration/        # Flyway миграции
│   ├── scripts/             # Вспомогательные скрипты
│   ├── static/              # CSS, JS
│   └── truststore.jks       # SSL сертификаты
├── build.gradle.kts         # Конфигурация сборки
├── chat_data.db             # БД с историей чата
└── embeddings.db            # БД с эмбеддингами
```

## API Endpoints

### Чат

- `POST /api/send` - Отправка сообщения AI
- `GET /api/message-history` - Получение истории сообщений
- `POST /api/clear-history` - Очистка истории

### Конфигурация

- `GET /api/system-prompt` - Получить системный промпт
- `POST /api/system-prompt` - Установить системный промпт
- `GET /api/temperature` - Получить температуру
- `POST /api/temperature` - Установить температуру
- `GET /api/max-tokens` - Получить максимальное количество токенов
- `POST /api/max-tokens` - Установить максимальное количество токенов
- `GET /api/auto-summarize-threshold` - Получить порог автосуммаризации
- `POST /api/auto-summarize-threshold` - Установить порог автосуммаризации
- `POST /api/summarize` - Выполнить суммаризацию вручную

### Управление клиентами

- `GET /api/current-client` - Получить текущего LLM провайдера
- `GET /api/available-clients` - Получить список доступных провайдеров
- `POST /api/switch-client` - Переключить LLM провайдера

### Документы (RAG)

- `POST /api/document/upload` - Загрузить документ для создания эмбеддингов
- `GET /api/document/progress/{requestId}` - Получить прогресс обработки документа

## Конфигурация LLM клиентов

Каждый клиент поддерживает следующие параметры:

- **systemPrompt**: Системный промпт для модели
- **temperature**: Температура генерации (0.0-1.0)
- **maxTokens**: Максимальное количество токенов в ответе
- **autoSummarizeThreshold**: Порог автоматической суммаризации (количество сообщений)

Конфигурация хранится в БД и может быть изменена через API или веб-интерфейс.

## MCP серверы

AI Agent поддерживает интеграцию с MCP серверами для расширения функциональности:

- **Database MCP Server** - выполнение SQL запросов
- **HTTP MCP Server** - выполнение HTTP запросов
- **Local MCP Server** - файловые операции (чтение/запись)
- **Git MCP Server** - операции с Git репозиториями

MCP серверы подключаются динамически при первом использовании и предоставляют свои инструменты LLM моделям.

## RAG (Retrieval-Augmented Generation)

AI Agent поддерживает загрузку документов и семантический поиск по ним:

1. Загрузите документ через `/api/document/upload`
2. Документ автоматически разбивается на чанки
3. Для каждого чанка создаются эмбеддинги через Ollama
4. Эмбеддинги сохраняются в векторное хранилище SQLite
5. При отправке сообщения выполняется семантический поиск
6. Релевантные чанки добавляются в контекст LLM

### Reranking

Для улучшения точности поиска можно включить reranking:

```bash
USE_RERANKING=true
HUGGINGFACE_API_KEY=your_token
RERANKER_MODEL=BAAI/bge-reranker-v2-m3
```

Reranker использует cross-encoder модель для переранжирования результатов векторного поиска.

## Суммаризация

AI Agent автоматически суммаризирует длинные диалоги:

1. Установите порог: `POST /api/auto-summarize-threshold` (например, 10 сообщений)
2. При достижении порога диалог автоматически сжимается
3. Сжатая версия используется как контекст для дальнейших сообщений
4. Ручная суммаризация: `POST /api/summarize`

## Разработка

### Добавление нового LLM провайдера

1. Создайте класс, реализующий `ApiClientInterface`
2. Расширьте `BaseApiClient` для стандартной функциональности
3. Зарегистрируйте клиента в `appModule` (di/AppModule.kt)
4. Добавьте в мапу `availableClients`

### Добавление нового MCP сервера

1. Создайте `Client` в `appModule`
2. Добавьте URL сервера в переменные окружения
3. Зарегистрируйте в `McpClientManager`

## Лицензия

MIT
