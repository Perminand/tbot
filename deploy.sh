#!/bin/bash

# Скрипт для развертывания Tinkoff Trading Bot на удаленном сервере
# Использование: ./deploy.sh [production|staging]

set -e

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
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

# Проверка аргументов
ENVIRONMENT=${1:-staging}
if [[ "$ENVIRONMENT" != "production" && "$ENVIRONMENT" != "staging" ]]; then
    error "Неверное окружение. Используйте 'production' или 'staging'"
    exit 1
fi

log "Начинаем развертывание в окружении: $ENVIRONMENT"

# Проверка наличия Docker и Docker Compose
if ! command -v docker &> /dev/null; then
    error "Docker не установлен"
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    error "Docker Compose не установлен"
    exit 1
fi

# Создание .env файла если не существует
if [[ ! -f .env ]]; then
    log "Создаем файл .env с переменными окружения"
    cat > .env << EOF
# Tinkoff API Tokens
TINKOFF_SANDBOX_TOKEN=your_sandbox_token_here
TINKOFF_PRODUCTION_TOKEN=your_production_token_here

# Database Configuration
DB_URL=jdbc:postgresql://postgres:5432/tbot_db
DB_USER=postgres
DB_PASSWORD=your_secure_password_here

# Application Configuration
SPRING_PROFILES_ACTIVE=docker
JAVA_OPTS=-Xmx1g -Xms512m
EOF
    warn "Файл .env создан. Пожалуйста, отредактируйте его с вашими реальными токенами и паролями"
fi

# Остановка существующих контейнеров
log "Останавливаем существующие контейнеры..."
docker-compose down --remove-orphans

# Удаление старых образов (опционально)
read -p "Удалить старые образы? (y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    log "Удаляем старые образы..."
    docker system prune -f
fi

# Сборка и запуск
log "Собираем и запускаем приложение..."
docker-compose up --build -d

# Ожидание запуска сервисов
log "Ожидаем запуска сервисов..."
sleep 30

# Проверка статуса
log "Проверяем статус сервисов..."
docker-compose ps

# Проверка логов
log "Проверяем логи приложения..."
docker-compose logs --tail=20 tbot-app

# Проверка доступности приложения
log "Проверяем доступность приложения..."
if curl -f http://localhost:8081/actuator/health 2>/dev/null; then
    log "Приложение успешно запущено и доступно!"
    log "Веб-интерфейс: http://localhost:8081"
    log "PgAdmin: http://localhost:5050 (admin@tbot.com / admin)"
else
    warn "Приложение может быть еще не готово. Проверьте логи:"
    docker-compose logs tbot-app
fi

log "Развертывание завершено!"
