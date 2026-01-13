# GitHub MCP Server

MCP-сервер для работы с GitHub Pull Requests через Model Context Protocol. Предоставляет 4 инструмента для получения информации о PR и публикации комментариев.

## Возможности

Сервер предоставляет следующие MCP tools для работы с GitHub:

### 1. github_get_pr
Получить информацию о Pull Request (title, описание, статистику, автора).

**Параметры:**
- `url` (string): URL Pull Request (например: https://github.com/owner/repo/pull/123)
- **ИЛИ**
- `owner` (string): Владелец репозитория
- `repo` (string): Название репозитория
- `prNumber` (number): Номер Pull Request

**Пример:**
```json
{
  "url": "https://github.com/torvalds/linux/pull/123"
}
```

или

```json
{
  "owner": "torvalds",
  "repo": "linux",
  "prNumber": 123
}
```

### 2. github_get_pr_files
Получить список измененных файлов в Pull Request с указанием количества изменений.

**Параметры:** те же что и в `github_get_pr`

**Пример:**
```json
{
  "url": "https://github.com/owner/repo/pull/123"
}
```

### 3. github_get_pr_diff
Получить полный diff изменений в Pull Request в формате unified diff.

**Параметры:** те же что и в `github_get_pr`

**Пример:**
```json
{
  "url": "https://github.com/owner/repo/pull/123"
}
```

### 4. github_post_comment
Опубликовать комментарий к Pull Request. **Требуется GITHUB_TOKEN.**

**Параметры:**
- `url` / `owner`, `repo`, `prNumber` (как в предыдущих tools)
- `comment` (string, required): Текст комментария (поддерживает Markdown)

**Пример:**
```json
{
  "url": "https://github.com/owner/repo/pull/123",
  "comment": "## AI Code Review\n\nВсе выглядит хорошо! ✅"
}
```

## Запуск

### Локальный запуск

```bash
./gradlew :github-mcp-server:run
```

Сервер будет доступен на `http://localhost:8084`

### Health Check

```bash
curl http://localhost:8084/health
```

Ответ: `GitHub MCP Server is running on port 8084`

### Docker

Сервер можно запустить через docker-compose вместе с остальными сервисами.

## Архитектура

- **Framework**: Ktor с Netty engine
- **SDK**: io.modelcontextprotocol:kotlin-sdk v0.8.1
- **HTTP Client**: Ktor CIO для работы с GitHub API
- **Transport**: Server-Sent Events (SSE)
- **Port**: 8084 (HTTP)

## Структура проекта

```
github-mcp-server/
├── build.gradle.kts
├── README.md
└── src/
    └── main/
        ├── kotlin/mcp_server/
        │   ├── Application.kt          # Главная точка входа
        │   ├── GitHubService.kt         # Сервис для работы с GitHub API
        │   └── McpConfiguration.kt      # Конфигурация MCP сервера с 4 tools
        └── resources/
            └── logback.xml              # Конфигурация логирования
```

## Интеграция с AI Agent

GitHub MCP Server интегрируется в AI Agent через `McpClientManager` и `McpToolsService`.

При запросе пользователя в чате с ссылкой на PR, GigaChatApiClient:
1. Получает список всех доступных tools (включая github tools)
2. Вызывает `github_get_pr` для получения информации
3. Вызывает `github_get_pr_diff` для получения изменений
4. Анализирует код с использованием RAG
5. Вызывает `github_post_comment` для публикации ревью

### Пример использования в чате

Пользователь пишет в чате:
```
Сделай ревью этого PR: https://github.com/owner/repo/pull/123
```

Система автоматически:
1. Распознает запрос на code review
2. Извлекает URL PR
3. Вызывает `github_get_pr(url)` для получения инфо
4. Вызывает `github_get_pr_diff(url)` для получения diff
5. Использует RAG для поиска похожих паттернов и coding guidelines
6. GigaChat анализирует код и генерирует ревью
7. Вызывает `github_post_comment(url, comment)` для публикации
8. Возвращает результат пользователю в чате

## Переменные окружения

- `GITHUB_TOKEN` - GitHub Personal Access Token для аутентификации (обязателен для `github_post_comment`)
  - Получить: https://github.com/settings/tokens
  - Требуемые права: `repo` (для приватных репозиториев) или `public_repo` (для публичных)
- `GITHUB_MCP_SERVER_URL` - URL для подключения из ai-agent (по умолчанию: `http://localhost:8084`)

## Создание GitHub Token

1. Перейдите на https://github.com/settings/tokens
2. Нажмите "Generate new token" → "Generate new token (classic)"
3. Выберите права:
   - `public_repo` - для публичных репозиториев
   - `repo` - для приватных репозиториев
4. Сохраните токен и добавьте в `.env`:
   ```
   GITHUB_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxx
   ```

## Разработка

### Добавление нового GitHub tool

1. Добавьте метод в `GitHubService.kt`:
```kotlin
suspend fun myNewGitHubOperation(owner: String, repo: String): String {
    return try {
        val response = httpClient.get("$baseUrl/repos/$owner/$repo/...") {
            // ...
        }
        // Обработка ответа
    } catch (e: Exception) {
        "Error: ${e.message}"
    }
}
```

2. Добавьте tool в `McpConfiguration.kt`:
```kotlin
addTool(
    name = "github_my_operation",
    description = "Описание операции",
    inputSchema = ToolSchema(/* параметры */)
) { request: CallToolRequest ->
    try {
        val result = githubService.myNewGitHubOperation(...)
        CallToolResult(content = listOf(TextContent(result)))
    } catch (e: Exception) {
        CallToolResult(content = listOf(TextContent("Ошибка: ${e.message}")))
    }
}
```

## Зависимости

- Kotlin 2.2.21
- Ktor 3.0.2
- MCP Kotlin SDK 0.8.1
- GitHub REST API v3

## Rate Limits

GitHub API имеет ограничения:
- Без токена: 60 запросов/час
- С токеном: 5000 запросов/час

Рекомендуется всегда использовать `GITHUB_TOKEN`.

## Примечания

- Сервер работает с публичными и приватными репозиториями (при наличии прав)
- Для публикации комментариев обязательно требуется `GITHUB_TOKEN`
- Поддерживает Markdown в комментариях
- Автоматически парсит GitHub URLs формата: `https://github.com/owner/repo/pull/123`
