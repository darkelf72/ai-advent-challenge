# Docker инфраструктура для AI Advent Challenge

Этот проект использует Docker для запуска всех модулей в отдельных контейнерах.

## Архитектура

Проект состоит из трех основных модулей:

1. **ai-agent** (порт 9999) - Основное приложение с веб-интерфейсом для взаимодействия с AI моделями
2. **db-mcp-server** (порт 8081) - MCP сервер для работы с базой данных SQLite
3. **http-mcp-server** (порт 8082) - MCP сервер для выполнения HTTP запросов

Все модули взаимодействуют между собой через внутреннюю Docker сеть `ai-network`.

## Файлы конфигурации

- `Dockerfile.ai-agent` - Образ для основного приложения
- `Dockerfile.db-mcp-server` - Образ для DB MCP сервера
- `Dockerfile.http-mcp-server` - Образ для HTTP MCP сервера
- `docker-compose.yml` - Оркестрация всех сервисов
- `docker-build-and-run.sh` - Скрипт для управления контейнерами
- `.env` - Файл с переменными окружения (API ключи)

## Предварительные требования

- Docker Desktop для macOS (версия 4.0+)
- 4+ GB свободной оперативной памяти
- 2+ GB свободного места на диске

## Быстрый старт

### 1. Установка Docker Desktop

Если Docker еще не установлен, скачайте и установите Docker Desktop:
https://www.docker.com/products/docker-desktop

### 2. Настройка переменных окружения

Скопируйте файл с примером переменных окружения:

```bash
cp .env.example .env
```

Отредактируйте `.env` и добавьте свои API ключи:

```bash
YANDEX_API_KEY=ваш_ключ_yandex
GIGA_CHAT_API_KEY=ваш_ключ_gigachat
```

### 3. Запуск контейнеров

Используйте скрипт для запуска:

```bash
./docker-build-and-run.sh up
```

Или используйте docker-compose напрямую:

```bash
docker-compose up -d
```

### 4. Проверка статуса

```bash
./docker-build-and-run.sh status
```

## Команды управления

Скрипт `docker-build-and-run.sh` предоставляет следующие команды:

### Сборка образов

```bash
./docker-build-and-run.sh build
```

Собирает Docker образы для всех модулей с нуля (без кеша).

### Запуск сервисов

```bash
./docker-build-and-run.sh up
```

Запускает все сервисы в фоновом режиме. После запуска сервисы доступны по адресам:
- AI Agent: http://localhost:9999
- DB MCP Server: http://localhost:8081
- HTTP MCP Server: http://localhost:8082

### Остановка сервисов

```bash
./docker-build-and-run.sh stop
# или
./docker-build-and-run.sh down
```

Останавливает все контейнеры.

### Перезапуск сервисов

```bash
./docker-build-and-run.sh restart
```

Перезапускает все контейнеры без их пересборки.

### Просмотр логов

```bash
./docker-build-and-run.sh logs
```

Показывает логи всех сервисов в реальном времени. Для выхода нажмите Ctrl+C.

Для просмотра логов конкретного сервиса:

```bash
docker-compose logs -f ai-agent
docker-compose logs -f db-mcp-server
docker-compose logs -f http-mcp-server
```

### Проверка статуса

```bash
./docker-build-and-run.sh status
```

Показывает статус всех контейнеров.

### Полная пересборка

```bash
./docker-build-and-run.sh rebuild
```

Останавливает контейнеры, пересобирает образы и запускает сервисы заново.

### Очистка

```bash
./docker-build-and-run.sh clean
```

Останавливает контейнеры, удаляет volumes и очищает неиспользуемые Docker ресурсы.

## Автоматический рестарт

Все сервисы настроены на автоматический рестарт в случае падения:

```yaml
restart: unless-stopped
```

Это означает, что контейнеры будут автоматически перезапускаться при сбое, кроме случаев, когда они были явно остановлены командой `docker stop` или `docker-compose down`.

## Health Checks

Каждый сервис имеет настроенные health checks для мониторинга состояния:

- **db-mcp-server**: проверка каждые 10 секунд
- **http-mcp-server**: проверка каждые 10 секунд
- **ai-agent**: проверка каждые 15 секунд

AI Agent запускается только после того, как оба MCP сервера станут healthy.

## Networking

Все сервисы работают в одной Docker сети `ai-network` типа bridge. Это позволяет им обращаться друг к другу по именам сервисов:

- `ai-agent` подключается к `db-mcp-server:8081`
- `ai-agent` подключается к `http-mcp-server:8082`

## Volumes

Проект использует именованный volume `db-data` для хранения данных базы SQLite. Данные сохраняются даже при остановке и удалении контейнеров.

Для полного удаления данных используйте:

```bash
docker-compose down -v
```

## Переменные окружения

### AI Agent

- `YANDEX_API_KEY` - API ключ для Yandex GPT
- `GIGA_CHAT_API_KEY` - API ключ для GigaChat
- `DB_MCP_SERVER_URL` - URL для подключения к DB MCP серверу (автоматически)
- `HTTP_MCP_SERVER_URL` - URL для подключения к HTTP MCP серверу (автоматически)

## Troubleshooting

### Проблема: Контейнеры не запускаются

Проверьте логи:
```bash
docker-compose logs
```

Убедитесь, что Docker Desktop запущен:
```bash
docker info
```

### Проблема: AI Agent не может подключиться к MCP серверам

Проверьте статус MCP серверов:
```bash
docker-compose ps
```

Убедитесь, что они в статусе "healthy":
```bash
docker-compose ps db-mcp-server
docker-compose ps http-mcp-server
```

### Проблема: Ошибка "port already in use"

Убедитесь, что порты 8081, 8082 и 9999 не заняты другими приложениями:
```bash
lsof -i :8081
lsof -i :8082
lsof -i :9999
```

Остановите процессы, использующие эти порты, или измените порты в `docker-compose.yml`.

### Проблема: Образы занимают много места

Очистите неиспользуемые образы и контейнеры:
```bash
./docker-build-and-run.sh clean
```

Или более агрессивная очистка:
```bash
docker system prune -a
```

### Проблема: Ошибка SSL при подключении к GigaChat

Убедитесь, что файл `truststore.jks` присутствует в директории `ai-agent/src/main/resources/`.

## Разработка

### Локальная разработка без Docker

Если вы хотите запустить сервисы локально без Docker:

1. Запустите DB MCP Server:
```bash
./gradlew db-mcp-server:run
```

2. Запустите HTTP MCP Server:
```bash
./gradlew http-mcp-server:run
```

3. Запустите AI Agent:
```bash
./gradlew ai-agent:run -PyandexApiKey=YOUR_KEY -PgigaChatApiKey=YOUR_KEY
```

### Пересборка только одного сервиса

```bash
docker-compose build ai-agent
docker-compose up -d ai-agent
```

### Подключение к запущенному контейнеру

```bash
docker exec -it ai-agent sh
docker exec -it db-mcp-server sh
docker exec -it http-mcp-server sh
```

## Дополнительная информация

- Образы используют multi-stage build для оптимизации размера
- Java приложения собираются на образе с Gradle, а запускаются на легком JRE образе
- Используется Alpine Linux для минимального размера образов
- Все зависимости кешируются для ускорения повторных сборок

## Контакты и поддержка

При возникновении проблем создайте issue в репозитории проекта.
