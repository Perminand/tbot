#!/bin/bash

# Скрипт диагностики проблем с портами и сетью
# Использование: ./diagnose.sh

set -e

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log() {
    echo -e "${GREEN}[$(date +'%H:%M:%S')] $1${NC}"
}

warn() {
    echo -e "${YELLOW}[$(date +'%H:%M:%S')] WARNING: $1${NC}"
}

error() {
    echo -e "${RED}[$(date +'%H:%M:%S')] ERROR: $1${NC}"
}

info() {
    echo -e "${BLUE}[$(date +'%H:%M:%S')] INFO: $1${NC}"
}

echo "🔍 Диагностика Tinkoff Trading Bot"
echo "=================================="

# 1. Проверка Docker
log "1. Проверяем Docker..."
if command -v docker &> /dev/null; then
    info "Docker установлен: $(docker --version)"
else
    error "Docker не установлен"
    exit 1
fi

if command -v docker-compose &> /dev/null; then
    info "Docker Compose установлен: $(docker-compose --version)"
else
    error "Docker Compose не установлен"
    exit 1
fi

# 2. Проверка контейнеров
log "2. Проверяем контейнеры..."
if docker ps | grep -q "tbot"; then
    info "Контейнеры tbot запущены:"
    docker ps | grep tbot
else
    warn "Контейнеры tbot не запущены"
fi

# 3. Проверка портов
log "3. Проверяем порты..."
ports=(80 443 8080 8081 5432 5433)
for port in "${ports[@]}"; do
    if netstat -tuln 2>/dev/null | grep -q ":$port "; then
        info "Порт $port занят"
    else
        warn "Порт $port свободен"
    fi
done

# 4. Проверка Docker сетей
log "4. Проверяем Docker сети..."
if docker network ls | grep -q "tbot_network"; then
    info "Сеть tbot_network существует"
else
    warn "Сеть tbot_network не найдена"
fi

# 5. Проверка firewall
log "5. Проверяем firewall..."
if command -v ufw &> /dev/null; then
    if ufw status | grep -q "Status: active"; then
        info "UFW активен"
        ufw status | grep -E "(80|443|8080|8081)"
    else
        warn "UFW неактивен"
    fi
elif command -v firewall-cmd &> /dev/null; then
    info "Firewalld активен"
    firewall-cmd --list-ports
else
    warn "Firewall не обнаружен"
fi

# 6. Проверка доступности приложения
log "6. Проверяем доступность приложения..."
endpoints=("http://localhost:80" "http://localhost:8080" "http://localhost:8081")
for endpoint in "${endpoints[@]}"; do
    if curl -f -s "$endpoint" > /dev/null 2>&1; then
        info "✅ $endpoint доступен"
    else
        warn "❌ $endpoint недоступен"
    fi
done

# 7. Проверка логов
log "7. Проверяем логи приложения..."
if docker ps | grep -q "tbot_app"; then
    info "Последние логи приложения:"
    docker logs --tail=5 tbot_app 2>/dev/null || warn "Не удалось получить логи"
else
    warn "Контейнер приложения не запущен"
fi

# 8. Рекомендации
log "8. Рекомендации..."
echo ""
if ! docker ps | grep -q "tbot"; then
    echo "🚀 Запустите приложение:"
    echo "   docker-compose -f docker-compose.simple.yml up -d"
    echo ""
fi

if ! netstat -tuln 2>/dev/null | grep -q ":8080 "; then
    echo "🔧 Попробуйте использовать порт 8080:"
    echo "   Измените в docker-compose.yml:"
    echo "   ports:"
    echo "     - \"8080:8080\""
    echo ""
fi

echo "📋 Полезные команды:"
echo "   docker-compose logs -f tbot-app    # Логи приложения"
echo "   docker-compose ps                  # Статус контейнеров"
echo "   docker-compose down                # Остановить контейнеры"
echo "   docker-compose up --build -d       # Пересобрать и запустить"
echo ""

log "Диагностика завершена!"


