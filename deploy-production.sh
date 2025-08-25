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
    elif command -v firewall-cmd &> /dev/null; then
        log "Firewalld активен, открываем порты..."
        firewall-cmd --permanent --add-port=80/tcp
        firewall-cmd --permanent --add-port=443/tcp
        firewall-cmd --permanent --add-port=8080/tcp
        firewall-cmd --reload
    else
        warn "Firewall не обнаружен, убедитесь что порты открыты вручную"
    fi
}

# Создание .env файла
create_env_file() {
    if [[ ! -f .env ]]; then
        log "Создаем файл .env с переменными окружения"
        cat > .env << EOF
# Tinkoff API Tokens (ОБЯЗАТЕЛЬНО ИЗМЕНИТЕ!)
TINKOFF_SANDBOX_TOKEN=your_sandbox_token_here
TINKOFF_PRODUCTION_TOKEN=your_production_token_here

# Database Configuration
DB_PASSWORD=your_secure_password_here

# Application Configuration
SPRING_PROFILES_ACTIVE=production
JAVA_OPTS=-Xmx1g -Xms512m
EOF
        error "Файл .env создан. ОБЯЗАТЕЛЬНО отредактируйте его с вашими реальными токенами и паролями!"
        exit 1
    else
        log "Файл .env уже существует"
    fi
}

# Остановка существующих контейнеров
stop_existing_containers() {
    log "Останавливаем существующие контейнеры..."
    docker-compose -f docker-compose.production.yml down --remove-orphans || true
}

# Очистка старых образов
cleanup_images() {
    read -p "Удалить старые образы для экономии места? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        log "Удаляем старые образы..."
        docker system prune -f
    fi
}

# Инициализация базы данных
init_database() {
    log "Инициализируем базу данных..."
    
    # Ждем, пока PostgreSQL будет готов
    log "Ожидание готовности PostgreSQL..."
    local max_attempts=30
    local attempt=1
    
    while [ $attempt -le $max_attempts ]; do
        if docker exec tbot_postgres pg_isready -U postgres > /dev/null 2>&1; then
            log "PostgreSQL готов!"
            break
        fi
        
        if [ $attempt -eq $max_attempts ]; then
            error "PostgreSQL не готов после $max_attempts попыток"
            return 1
        fi
        
        log "Попытка $attempt/$max_attempts: PostgreSQL еще не готов, ждем..."
        sleep 10
        attempt=$((attempt + 1))
    done
    
    # Создаем базу данных если она не существует
    log "Проверка существования базы данных tbot_db..."
    if ! docker exec tbot_postgres psql -U postgres -lqt | cut -d \| -f 1 | grep -qw tbot_db; then
        log "Создание базы данных tbot_db..."
        docker exec tbot_postgres createdb -U postgres tbot_db
        log "База данных tbot_db создана успешно!"
    else
        log "База данных tbot_db уже существует."
    fi
    
    # Выполняем инициализационный скрипт
    log "Выполнение инициализационного скрипта..."
    if [ -f "init-db.sql" ]; then
        docker exec -i tbot_postgres psql -U postgres -d tbot_db < init-db.sql
        log "Инициализационный скрипт выполнен успешно!"
    else
        warn "Файл init-db.sql не найден, пропускаем инициализацию"
    fi
}

# Сборка и запуск
build_and_start() {
    log "Собираем и запускаем приложение..."
    docker-compose -f docker-compose.production.yml up --build -d
    
    log "Ожидаем запуска сервисов..."
    sleep 30
    
    # Инициализируем базу данных
    init_database
}

# Проверка статуса
check_status() {
    log "Проверяем статус сервисов..."
    docker-compose -f docker-compose.production.yml ps
    
    log "Проверяем логи приложения..."
    docker-compose -f docker-compose.production.yml logs --tail=20 tbot-app
}

# Проверка доступности
check_availability() {
    log "Проверяем доступность приложения..."
    
    local endpoints=("http://localhost:80" "http://localhost:8080" "http://localhost/health")
    local available=false
    
    for endpoint in "${endpoints[@]}"; do
        if curl -f -s "$endpoint" > /dev/null 2>&1; then
            log "✅ Приложение доступно по адресу: $endpoint"
            available=true
            break
        fi
    done
    
    if [ "$available" = false ]; then
        error "Приложение недоступно. Проверьте логи:"
        docker-compose -f docker-compose.production.yml logs tbot-app
        return 1
    fi
}

# Диагностика проблем
diagnose_issues() {
    log "Выполняем диагностику..."
    
    # Проверка контейнеров
    if ! docker-compose -f docker-compose.production.yml ps | grep -q "Up"; then
        error "Контейнеры не запущены"
        docker-compose -f docker-compose.production.yml logs
        return 1
    fi
    
    # Проверка сети
    if ! docker network ls | grep -q "tbot_network"; then
        error "Docker сеть не создана"
        return 1
    fi
    
    # Проверка портов
    if ! netstat -tuln | grep -q ":80 "; then
        warn "Порт 80 не слушается"
    fi
    
    if ! netstat -tuln | grep -q ":8080 "; then
        warn "Порт 8080 не слушается"
    fi
    
    log "Диагностика завершена"
}

# Основная функция
main() {
    log "Начинаем развертывание Tinkoff Trading Bot на удаленном сервере"
    
    check_requirements
    check_ports
    setup_firewall
    create_env_file
    stop_existing_containers
    cleanup_images
    build_and_start
    check_status
    
    if check_availability; then
        log "🎉 Развертывание успешно завершено!"
        log "Веб-интерфейс доступен по адресам:"
        log "  - HTTP: http://your-server-ip"
        log "  - HTTP: http://your-server-ip:8080"
        log "  - HTTPS: https://your-server-ip (если настроен SSL)"
        log ""
        log "Для просмотра логов используйте:"
        log "  docker-compose -f docker-compose.production.yml logs -f tbot-app"
    else
        error "❌ Развертывание завершилось с ошибками"
        diagnose_issues
        exit 1
    fi
}

# Запуск основной функции
main "$@"


