#!/bin/bash

# Скрипт для получения сводной информации о погоде
# Автор: AI Assistant
# Дата: 2025-12-18

set -e

# Константы
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../../.." && pwd)"
LOG_FILE="$SCRIPT_DIR/weather_summary.log"
APP_PORT=9999

# Функция для логирования
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "$LOG_FILE"
}

# Функция для проверки, запущен ли процесс на порту
is_port_listening() {
    local port=$1
    lsof -i :$port -sTCP:LISTEN -t >/dev/null 2>&1
}

# Функция для показа уведомления в macOS
show_notification() {
    local title=$1
    local message=$2

    # Экранируем кавычки в сообщении
    local escaped_message=$(echo "$message" | sed 's/"/\\"/g')

    # Отображаем уведомление через AppleScript
    osascript -e "display notification \"$escaped_message\" with title \"$title\" sound name \"default\""
}

# Функция для получения сводной информации
get_weather_summary() {
    log "Запрос сводной информации о погоде..."

    local message="Дай мне сводную информацию о текущей погоде в городах. Возьми данные из нашей переписки"

    local response=$(curl -s -X POST "http://localhost:$APP_PORT/api/send" \
        -H "Content-Type: application/json" \
        -d "{\"message\": \"$message\"}" 2>&1)

    if [ $? -eq 0 ]; then
        # Извлекаем ответ из JSON
        local answer=$(echo "$response" | python3 -c "import sys, json; print(json.load(sys.stdin).get('answer', 'Нет ответа'))" 2>/dev/null)

        if [ -z "$answer" ] || [ "$answer" == "Нет ответа" ]; then
            # Если python не сработал, используем jq если доступен
            if command -v jq &> /dev/null; then
                answer=$(echo "$response" | jq -r '.answer // "Нет ответа"')
            else
                # Fallback - берем весь ответ
                answer="$response"
            fi
        fi

        log "=================================================="
        log "СВОДНАЯ ИНФОРМАЦИЯ О ПОГОДЕ"
        log "=================================================="
        log "$answer"
        log "=================================================="
        log ""

        # Отображаем уведомление (обрезаем до 200 символов для удобства)
        local notification_text=$(echo "$answer" | head -c 200)
        if [ ${#answer} -gt 200 ]; then
            notification_text="$notification_text..."
        fi

        show_notification "Сводка по погоде" "$notification_text"

        return 0
    else
        log "ОШИБКА при запросе сводной информации: $response"
        show_notification "Ошибка" "Не удалось получить сводку по погоде"
        return 1
    fi
}

# Основная функция
main() {
    log "=================================================="
    log "Запуск скрипта получения сводной информации"
    log "=================================================="

    # Проверяем, запущено ли приложение
    if ! is_port_listening $APP_PORT; then
        log "ОШИБКА: Приложение app не запущено на порту $APP_PORT"
        log "Сначала запустите приложение или используйте скрипт weather_monitoring.sh"
        show_notification "Ошибка" "Приложение app не запущено"
        exit 1
    fi

    log "Приложение app запущено. Получаем сводную информацию..."

    # Получаем сводную информацию
    if get_weather_summary; then
        log "Сводная информация успешно получена"
    else
        log "Не удалось получить сводную информацию"
        exit 1
    fi
}

# Запуск
main
