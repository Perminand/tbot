#!/bin/bash

# Скрипт для развертывания Tinkoff Trading Bot на удаленном сервере
# Использование: ./deploy-production.sh

set -e

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Функция для логирования
log() {
    echo -e "${GREEN}[$(date +'%Y-%m-%d %H:%M:%S')] $1${NC}"
}

warn() {
    echo -e "${YELLOW}[$(date +'%Y-%m-%d %H:%M:%S')] WARNING: $1${NC}"
}

error() {
    echo -e "${RED}[$(date +'%Y-%m-%d %H:%M:%S')] ERROR: $1${NC}"
}

info() {
    echo -e "${BLUE}[$(date +'%Y-%m-%d %H:%M:%S')] INFO: $1${NC}"
}

# Проверка системных требований
check_requirements() {
    log "Проверяем системные требования..."
    
    # Проверка Docker
    if ! command -v docker &> /dev/null; then
        error "Docker не установлен"
        exit 1
    fi
    
    # Проверка Docker Compose
    if ! command -v docker-compose &> /dev/null; then
        error "Docker Compose не установлен"
        exit 1
    fi
    
    # Проверка свободного места
    FREE_SPACE=$(df / | awk 'NR==2 {print $4}')
    if [ "$FREE_SPACE" -lt 5242880 ]; then  # 5GB в KB
        warn "Мало свободного места на диске (меньше 5GB)"
    fi
    
    # Проверка памяти
    TOTAL_MEM=$(free -m | awk 'NR==2{printf "%.0f", $2}')
    if [ "$TOTAL_MEM" -lt 2048 ]; then
        warn "Мало оперативной памяти (меньше 2GB)"
    fi
    
    log "Системные требования выполнены"
}

# Проверка портов
check_ports() {
    log "Проверяем доступность портов..."
    
    local ports=(80 443 8080 5432)
    local occupied_ports=()
    
    for port in "${ports[@]}"; do
        if netstat -tuln | grep -q ":$port "; then
            occupied_ports+=($port)
        fi
    done
    
    if [ ${#occupied_ports[@]} -gt 0 ]; then
        warn "Следующие порты уже заняты: ${occupied_ports[*]}"
        read -p "Продолжить развертывание? (y/N): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 1
        fi
    else
        log "Все необходимые порты свободны"
    fi
}

# Настройка firewall
setup_firewall() {
    log "Настраиваем firewall..."
    
    # Проверяем наличие ufw
    if command -v ufw &> /dev/null; then
        if ufw status | grep -q "Status: active"; then
            log "UFW активен, открываем порты..."
            ufw allow 80/tcp
            ufw allow 443/tcp
            ufw allow 8080/tcp
            ufw allow 22/tcp  # SSH
        fi
    fi
}

# Создание .env файла
create_env_file() {
    log "Создаем файл .env..."
    
    if [ ! -f .env ]; then
        cat > .env << EOF
# Database Configuration
DB_PASSWORD=your_secure_password_here

# Tinkoff API Configuration
TINKOFF_SANDBOX_TOKEN=your_sandbox_token_here
TINKOFF_PRODUCTION_TOKEN=your_production_token_here

# Application Configuration
SPRING_PROFILES_ACTIVE=production
JAVA_OPTS=-Xmx1g -Xms512m

# Security Configuration
APP_USERNAME=admin
APP_PASSWORD=admin

# Logging Configuration
LOGGING_LEVEL_ROOT=INFO
LOGGING_LEVEL_RU_PERMINOV=DEBUG

# Docker Image
IMAGE=tbot-app:latest
EOF
        warn "Создан файл .env с дефолтными значениями. Отредактируйте его перед запуском!"
    else
        log "Файл .env уже существует"
    fi
}

# Сборка образа
build_image() {
    log "Собираем Docker образ..."
    
    # Проверяем наличие Dockerfile
    if [ ! -f Dockerfile ]; then
        error "Dockerfile не найден"
        exit 1
    fi
    
    # Собираем образ
    docker build -t tbot-app:latest .
    
    if [ $? -eq 0 ]; then
        log "Образ успешно собран"
    else
        error "Ошибка при сборке образа"
        exit 1
    fi
}

# Остановка существующих контейнеров
stop_existing_containers() {
    log "Останавливаем существующие контейнеры..."
    
    # Останавливаем контейнеры если они запущены
    if docker ps -q --filter "name=tbot_app" | grep -q .; then
        docker stop tbot_app
        log "Контейнер tbot_app остановлен"
    fi
    
    if docker ps -q --filter "name=tbot_postgres" | grep -q .; then
        docker stop tbot_postgres
        log "Контейнер tbot_postgres остановлен"
    fi
    
    # Удаляем контейнеры
    if docker ps -aq --filter "name=tbot_app" | grep -q .; then
        docker rm tbot_app
        log "Контейнер tbot_app удален"
    fi
    
    if docker ps -aq --filter "name=tbot_postgres" | grep -q .; then
        docker rm tbot_postgres
        log "Контейнер tbot_postgres удален"
    fi
}

# Запуск приложения
start_application() {
    log "Запускаем приложение..."
    
    # Запускаем с production конфигурацией
    docker-compose -f docker-compose.production.yml up -d
    
    if [ $? -eq 0 ]; then
        log "Приложение запущено"
    else
        error "Ошибка при запуске приложения"
        exit 1
    fi
}

# Проверка здоровья приложения
check_health() {
    log "Проверяем здоровье приложения..."
    
    # Ждем запуска приложения
    sleep 30
    
    # Проверяем статус контейнеров
    if docker ps --filter "name=tbot_app" --filter "status=running" | grep -q tbot_app; then
        log "Контейнер tbot_app запущен"
    else
        error "Контейнер tbot_app не запущен"
        docker logs tbot_app
        exit 1
    fi
    
    if docker ps --filter "name=tbot_postgres" --filter "status=running" | grep -q tbot_postgres; then
        log "Контейнер tbot_postgres запущен"
    else
        error "Контейнер tbot_postgres не запущен"
        docker logs tbot_postgres
        exit 1
    fi
    
    # Проверяем доступность приложения
    local max_attempts=30
    local attempt=1
    
    while [ $attempt -le $max_attempts ]; do
        if curl -f http://localhost:8080/actuator/health > /dev/null 2>&1; then
            log "Приложение доступно по адресу http://localhost:8080"
            break
        else
            if [ $attempt -eq $max_attempts ]; then
                error "Приложение не отвечает после $max_attempts попыток"
                docker logs tbot_app
                exit 1
            fi
            warn "Попытка $attempt/$max_attempts: приложение еще не готово..."
            sleep 10
            attempt=$((attempt + 1))
        fi
    done
}

# Основная функция
main() {
    log "Начинаем развертывание Tinkoff Trading Bot..."
    
    check_requirements
    check_ports
    setup_firewall
    create_env_file
    build_image
    stop_existing_containers
    start_application
    check_health
    
    log "Развертывание завершено успешно!"
    log "Приложение доступно по адресу: http://localhost:8080"
    log "Для просмотра логов используйте: docker logs tbot_app"
    log "Для остановки: docker-compose -f docker-compose.production.yml down"
}

# Запуск основной функции
main "$@"


