#!/bin/bash

# Скрипт для остановки приложений app и mcp-server
# Автор: AI Assistant
# Дата: 2025-12-18

set -e

# Константы
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_FILE="$SCRIPT_DIR/stop_services.log"
APP_PORT=9999
MCP_PORT=8082

# Функция для логирования
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "$LOG_FILE"
}

# Функция для остановки процесса на порту
stop_process_on_port() {
    local port=$1
    local service_name=$2

    log "Проверка процесса на порту $port ($service_name)..."

    # Находим PID процесса, слушающего порт
    local pid=$(lsof -ti :$port -sTCP:LISTEN 2>/dev/null)

    if [ -z "$pid" ]; then
        log "Процесс на порту $port не найден"
        return 0
    fi

    log "Найден процесс с PID: $pid на порту $port"
    log "Останавливаем процесс..."

    # Пытаемся остановить процесс мягко (SIGTERM)
    kill $pid 2>/dev/null

    # Ждем до 10 секунд, чтобы процесс завершился
    local count=0
    while [ $count -lt 10 ]; do
        if ! lsof -ti :$port -sTCP:LISTEN >/dev/null 2>&1; then
            log "Процесс $service_name успешно остановлен"
            return 0
        fi
        sleep 1
        count=$((count + 1))
    done

    # Если процесс все еще работает, принудительно убиваем его (SIGKILL)
    if lsof -ti :$port -sTCP:LISTEN >/dev/null 2>&1; then
        log "Процесс не остановился, принудительное завершение..."
        kill -9 $pid 2>/dev/null
        sleep 1

        if ! lsof -ti :$port -sTCP:LISTEN >/dev/null 2>&1; then
            log "Процесс $service_name принудительно завершен"
            return 0
        else
            log "ОШИБКА: Не удалось остановить процесс $service_name"
            return 1
        fi
    fi
}

# Основная функция
main() {
    log "=================================================="
    log "Запуск скрипта остановки сервисов"
    log "=================================================="

    # Останавливаем приложение app
    stop_process_on_port $APP_PORT "app"

    # Останавливаем MCP сервер
    stop_process_on_port $MCP_PORT "mcp-server"

    log "=================================================="
    log "Все сервисы остановлены"
    log "=================================================="
}

# Запуск
main
