# Git MCP Server

MCP-сервер для работы с Git репозиторием через Model Context Protocol. Предоставляет 10 инструментов для выполнения основных Git операций через JGit.

## Возможности

Сервер предоставляет следующие MCP tools для работы с Git:

### 1. git_status
Получить статус репозитория (измененные, добавленные, неотслеживаемые файлы).

**Параметры:** нет

**Пример:**
```json
{}
```

### 2. git_log
Показать историю коммитов с указанием автора, даты и сообщения.

**Параметры:**
- `limit` (number, optional): Количество коммитов для отображения (по умолчанию 10)
- `branch` (string, optional): Имя ветки

**Пример:**
```json
{
  "limit": 5,
  "branch": "main"
}
```

### 3. git_diff
Показать изменения в рабочей директории или staged файлах.

**Параметры:**
- `cached` (boolean, optional): Показать staged изменения (по умолчанию false)
- `fileName` (string, optional): Имя файла для фильтрации

**Пример:**
```json
{
  "cached": true,
  "fileName": "src/main/kotlin/Main.kt"
}
```

### 4. git_branch_list
Получить список локальных и/или удаленных веток.

**Параметры:**
- `listMode` (string, optional): Режим: 'local', 'remote', или 'all' (по умолчанию 'all')

**Пример:**
```json
{
  "listMode": "local"
}
```

### 5. git_branch_create
Создать новую ветку.

**Параметры:**
- `branchName` (string, required): Имя новой ветки
- `startPoint` (string, optional): Начальная точка (коммит/ветка)

**Пример:**
```json
{
  "branchName": "feature/new-feature",
  "startPoint": "main"
}
```

### 6. git_checkout
Переключиться на другую ветку.

**Параметры:**
- `branchName` (string, required): Имя ветки для переключения
- `createIfNotExists` (boolean, optional): Создать ветку если не существует (по умолчанию false)

**Пример:**
```json
{
  "branchName": "develop",
  "createIfNotExists": false
}
```

### 7. git_commit
Создать коммит с указанным сообщением.

**Параметры:**
- `message` (string, required): Сообщение коммита
- `addAll` (boolean, optional): Добавить все измененные файлы перед коммитом (git add . && git commit)

**Пример:**
```json
{
  "message": "Add new feature",
  "addAll": true
}
```

### 8. git_push
Отправить коммиты на удаленный репозиторий.

**Параметры:**
- `remote` (string, optional): Имя удаленного репозитория (по умолчанию 'origin')
- `branch` (string, optional): Имя ветки
- `username` (string, optional): Имя пользователя для аутентификации
- `password` (string, optional): Пароль или токен для аутентификации

**Пример:**
```json
{
  "remote": "origin",
  "branch": "main"
}
```

### 9. git_pull
Получить изменения из удаленного репозитория.

**Параметры:**
- `remote` (string, optional): Имя удаленного репозитория (по умолчанию 'origin')
- `branch` (string, optional): Имя ветки
- `username` (string, optional): Имя пользователя для аутентификации
- `password` (string, optional): Пароль или токен для аутентификации

**Пример:**
```json
{
  "remote": "origin",
  "branch": "main"
}
```

### 10. git_show_file
Показать содержимое файла в определенном коммите.

**Параметры:**
- `commitHash` (string, required): Hash коммита или ref (например, HEAD, HEAD~1)
- `filePath` (string, required): Путь к файлу в репозитории

**Пример:**
```json
{
  "commitHash": "HEAD",
  "filePath": "README.md"
}
```

## Запуск

### Локальный запуск

```bash
./gradlew :git-mcp-server:run
```

Сервер будет доступен на `http://localhost:8083`

### Health Check

```bash
curl http://localhost:8083/health
```

Ответ: `Git MCP Server is running on port 8083`

### Docker

Сервер автоматически запускается через docker-compose вместе с остальными сервисами:

```bash
docker-compose up git-mcp-server
```

## Архитектура

- **Framework**: Ktor с Netty engine
- **SDK**: io.modelcontextprotocol:kotlin-sdk v0.8.1
- **Git Library**: JGit (org.eclipse.jgit) v6.10.0
- **Transport**: Server-Sent Events (SSE)
- **Port**: 8083 (HTTP)

## Структура проекта

```
git-mcp-server/
├── build.gradle.kts
├── README.md
└── src/
    └── main/
        ├── kotlin/mcp_server/
        │   ├── Application.kt          # Главная точка входа
        │   ├── GitService.kt            # Сервис для работы с Git через JGit
        │   └── McpConfiguration.kt      # Конфигурация MCP сервера с 10 tools
        └── resources/
            └── logback.xml              # Конфигурация логирования
```

## Интеграция с AI Agent

Git MCP Server автоматически интегрирован в AI Agent через `McpClientManager` и `McpToolsService`.

При запросе пользователя в чате, GigaChatApiClient получает список всех доступных tools (включая git tools) и может вызывать их через MCP протокол.

### Пример использования в чате

Пользователь пишет в чате:
```
git_status
```

Система автоматически:
1. GigaChatApiClient получает список всех tools через `McpToolsService.getAvailableTools()`
2. Определяет, что `git_status` доступен в Git MCP Server
3. Выполняет tool через `McpToolsService.executeTool("git_status", {})`
4. Git MCP Server вызывает `GitService.getStatus()`
5. Результат возвращается пользователю в чате

## Переменные окружения

- `GIT_MCP_SERVER_URL` - URL Git MCP сервера для подключения из ai-agent (по умолчанию: `http://localhost:8083`)
- `GIT_REPOSITORY_PATH` - Путь к корневому каталогу git-репозитория
  - Локально: `..` (на уровень выше от git-mcp-server/)
  - Docker: `.` (текущая директория, где смонтирован .git)

## Разработка

### Добавление нового git-инструмента

1. Добавьте метод в `GitService.kt`:
```kotlin
fun myNewGitOperation(): String {
    return try {
        openGit().use { git ->
            // Ваша логика с JGit
            "Result"
        }
    } catch (e: Exception) {
        "Error: ${e.message}"
    }
}
```

2. Добавьте tool в `McpConfiguration.kt`:
```kotlin
addTool(
    name = "git_my_operation",
    description = "Описание операции",
    inputSchema = ToolSchema(/* параметры */)
) { request: CallToolRequest ->
    try {
        val result = gitService.myNewGitOperation()
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
- JGit 6.10.0

## Примечания

- Сервер работает с корневым git-репозиторием проекта `ai-advent-challenge/`
- При локальном запуске использует путь `..` (родительская директория от git-mcp-server/)
- В Docker контейнере .git директория монтируется как read-only volume в `/app/.git`
- Для push/pull операций может потребоваться аутентификация (username/password или токен)
- JGit поддерживает все основные Git операции без необходимости установки git CLI
