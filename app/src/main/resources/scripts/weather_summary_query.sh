#!/bin/bash

# Скрипт для запроса сводной информации из таблицы weather за последние 30 минут
# Проверяет запуск сервисов, запрашивает данные и показывает уведомление
# Автор: AI Assistant
# Дата: 2025-12-18

set -e

# Константы
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../../.." && pwd)"
LOG_DIR="$(cd "$SCRIPT_DIR/.." && pwd)/logs"
LOG_FILE="$LOG_DIR/weather_summary_query.log"
APP_PORT=9999
MCP_PORT=8082

# Создаем директорию для логов, если её нет
mkdir -p "$LOG_DIR"

# Функция для логирования
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "$LOG_FILE"
}

# Функция для проверки, запущен ли процесс на порту
is_port_listening() {
    local port=$1
    lsof -i :$port -sTCP:LISTEN -t >/dev/null 2>&1
}

# Функция для проверки доступности сервиса
check_service() {
    local url=$1
    local max_attempts=30
    local attempt=0

    while [ $attempt -lt $max_attempts ]; do
        if curl -s -f "$url" >/dev/null 2>&1; then
            return 0
        fi
        attempt=$((attempt + 1))
        sleep 1
    done
    return 1
}

# Функция для запуска MCP сервера
start_mcp_server() {
    log "Запуск MCP сервера..."
    cd "$PROJECT_ROOT"

    # Запускаем MCP сервер в фоне
    ./gradlew :mcp-server:run > /dev/null 2>&1 &
    local mcp_pid=$!

    log "MCP сервер запущен (PID: $mcp_pid)"

    # Ждем, пока сервер станет доступен
    if check_service "http://localhost:$MCP_PORT/health"; then
        log "MCP сервер успешно запущен и доступен"
        return 0
    else
        log "ОШИБКА: MCP сервер не стал доступен в течение 30 секунд"
        return 1
    fi
}

# Функция для запуска приложения
start_app() {
    log "Запуск приложения app..."
    cd "$PROJECT_ROOT"

    # Запускаем приложение в фоне
    ./gradlew :app:run > /dev/null 2>&1 &
    local app_pid=$!

    log "Приложение app запущено (PID: $app_pid)"

    # Ждем, пока приложение станет доступно
    if check_service "http://localhost:$APP_PORT"; then
        log "Приложение app успешно запущено и доступно"
        return 0
    else
        log "ОШИБКА: Приложение app не стало доступно в течение 30 секунд"
        return 1
    fi
}

# Функция для показа macOS уведомления
show_notification() {
    local title="$1"
    local message="$2"

    osascript -e "display notification \"$message\" with title \"$title\" sound name \"default\""
}

# Функция для запроса сводной информации из БД
request_summary() {
    log "Запрос сводной информации из таблицы weather за последние 30 минут..."

    local message="Дай мне сводную информацию из таблицы weather. Не запрашивай у меня доп.информацию - просто предоставь данные так, как посчтиаешь нужным."

    local response=$(curl -s -X POST "http://localhost:$APP_PORT/api/send" \
        -H "Content-Type: application/json" \
        -d "{\"message\": \"$message\"}" 2>&1)

    if [ $? -eq 0 ]; then
        log "Успешно получен ответ от приложения"
        log "=================================================="
        log "ОТВЕТ ОТ ПРИЛОЖЕНИЯ:"
        log "$response"
        log "=================================================="

        # Извлекаем только текст ответа из JSON (если возможно)
        local answer=$(echo "$response" | grep -o '"answer":"[^"]*"' | sed 's/"answer":"//;s/"$//' | sed 's/\\n/ /g' | sed 's/\\//g' || echo "$response")

        # Показываем уведомление (ограничиваем длину для читаемости)
        local notification_text="${answer:0:200}"
        if [ ${#answer} -gt 200 ]; then
            notification_text="${notification_text}..."
        fi

        show_notification "Сводка по погоде" "$notification_text"

        log "Уведомление отправлено пользователю"
        return 0
    else
        log "ОШИБКА при запросе сводной информации: $response"
        show_notification "Ошибка" "Не удалось получить сводную информацию по погоде"
        return 1
    fi
}

# Основная функция
main() {
    log "=================================================="
    log "Запуск скрипта запроса сводной информации по погоде"
    log "=================================================="

    # Проверяем и запускаем MCP сервер
    if ! is_port_listening $MCP_PORT; then
        log "MCP сервер не запущен"
        if ! start_mcp_server; then
            log "КРИТИЧЕСКАЯ ОШИБКА: Не удалось запустить MCP сервер"
            show_notification "Ошибка запуска" "Не удалось запустить MCP сервер"
            exit 1
        fi
    else
        log "MCP сервер уже запущен"
    fi

    # Проверяем и запускаем приложение
    if ! is_port_listening $APP_PORT; then
        log "Приложение app не запущено"
        if ! start_app; then
            log "КРИТИЧЕСКАЯ ОШИБКА: Не удалось запустить приложение app"
            show_notification "Ошибка запуска" "Не удалось запустить приложение app"
            exit 1
        fi
    else
        log "Приложение app уже запущено"
    fi

    log "Все сервисы запущены. Выполняем запрос сводной информации..."
    log ""

    # Запрашиваем сводную информацию
    if request_summary; then
        log "=================================================="
        log "Скрипт успешно завершен"
        log "=================================================="
        exit 0
    else
        log "=================================================="
        log "Скрипт завершен с ошибкой"
        log "=================================================="
        exit 1
    fi
}

# Обработка сигналов для корректного завершения
trap 'log "Получен сигнал завершения. Останавливаем скрипт..."; exit 0' SIGINT SIGTERM

# Запуск
main
