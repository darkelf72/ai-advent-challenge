#!/bin/bash
# Скрипт для запроса погоды в крупнейших городах России
# Автор: AI Assistant
# Дата: 2025-12-18

set -e

# Константы
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../../../.." && pwd)"
LOG_DIR="$(cd "$SCRIPT_DIR/.." && pwd)/logs"
LOG_FILE="$LOG_DIR/weather_monitoring.log"
DB_FILE="$PROJECT_ROOT/mcp-server/mcp_data.db"
APP_PORT=9999
MCP_PORT=8082

# Создаем директорию для логов, если её нет
mkdir -p "$LOG_DIR"

# Города (используем обычные массивы с одинаковыми индексами)
CITY_NAMES=("Москва" "Санкт-Петербург" "Новосибирск" "Екатеринбург" "Казань" "Нижний Новгород")
CITY_LATITUDES=(55.7558 59.9311 55.0084 56.8389 55.8304 56.2965)
CITY_LONGITUDES=(37.6173 30.3609 82.9357 60.6057 49.0661 43.9361)

# Функция для логирования
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "$LOG_FILE"
}

# Функция для сохранения данных в базу данных
save_to_db() {
    local content="$1"

    # Экранируем одинарные кавычки в content
    local escaped_content="${content//\'/\'\'}"

    # Вставляем данные в таблицу weather
    # Используем set +e временно, чтобы не прерывать скрипт при ошибке
    set +e
    local error_output=$(sqlite3 "$DB_FILE" "INSERT INTO weather (content, createdAt) VALUES ('$escaped_content', datetime('now'));" 2>&1)
    local exit_code=$?
    set -e

    if [ $exit_code -eq 0 ]; then
        log "Данные успешно сохранены в базу данных"
    else
        log "ОШИБКА: Не удалось сохранить данные в базу данных. Код ошибки: $exit_code"
        log "Детали ошибки: $error_output"
        log "Путь к БД: $DB_FILE"
    fi
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

# Функция для запроса погоды
request_weather() {
    local city_name=$1
    local latitude=$2
    local longitude=$3

    log "Запрос погоды для города: $city_name (lat: $latitude, lon: $longitude)"

    local message="Какая сейчас погода в городе $city_name?"

    local response=$(curl -s -X POST "http://localhost:$APP_PORT/api/send" \
        -H "Content-Type: application/json" \
        -d "{\"message\": \"$message\"}" 2>&1)

    if [ $? -eq 0 ]; then
        log "Ответ для $city_name: $response"
        # Сохраняем ответ в базу данных
        save_to_db "$response"
        echo ""  >> "$LOG_FILE"
    else
        log "ОШИБКА при запросе погоды для $city_name: $response"
    fi
}

# Основная функция
main() {
    log "=================================================="
    log "Запуск скрипта получения погоды"
    log "=================================================="

    # Проверяем и запускаем MCP сервер
    if ! is_port_listening $MCP_PORT; then
        log "MCP сервер не запущен"
        if ! start_mcp_server; then
            log "КРИТИЧЕСКАЯ ОШИБКА: Не удалось запустить MCP сервер"
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
            exit 1
        fi
    else
        log "Приложение app уже запущено"
    fi

    log "Все сервисы запущены. Начинаем запрос погоды..."
    log ""

    log "--------------------------------------------------"
    log "Запрос погоды по всем городам"
    log "--------------------------------------------------"

    # Запрашиваем погоду для каждого города
    local total_cities=${#CITY_NAMES[@]}
    for ((i=0; i<$total_cities; i++)); do
        request_weather "${CITY_NAMES[$i]}" "${CITY_LATITUDES[$i]}" "${CITY_LONGITUDES[$i]}"
        sleep 2  # Небольшая пауза между запросами к одному и тому же серверу
    done

    log "=================================================="
    log "Запрос погоды завершен по всем городам"
    log "=================================================="
}

# Обработка сигналов для корректного завершения
trap 'log "Получен сигнал завершения. Останавливаем скрипт..."; exit 0' SIGINT SIGTERM

# Запуск
main
