#!/bin/bash

# Скрипт для автоматического развертывания Tinkoff Trading Bot
# Использование: ./deploy.sh [production|staging]

set -e  # Остановка при ошибке

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Функции для логирования
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Проверка аргументов
ENVIRONMENT=${1:-production}
COMPOSE_FILE="docker-compose.prod.yml"

if [[ "$ENVIRONMENT" != "production" && "$ENVIRONMENT" != "staging" ]]; then
    log_error "Неверное окружение. Используйте 'production' или 'staging'"
    exit 1
fi

log_info "Начинаем развертывание в окружении: $ENVIRONMENT"

# Проверка наличия необходимых файлов
check_files() {
    log_info "Проверка необходимых файлов..."
    
    if [[ ! -f "pom.xml" ]]; then
        log_error "Файл pom.xml не найден"
        exit 1
    fi
    
    if [[ ! -f "$COMPOSE_FILE" ]]; then
        log_error "Файл $COMPOSE_FILE не найден"
        exit 1
    fi
    
    if [[ ! -f ".env" ]]; then
        log_warning "Файл .env не найден. Создаем шаблон..."
        create_env_template
    fi
    
    log_success "Все необходимые файлы найдены"
}

# Создание шаблона .env файла
create_env_template() {
    cat > .env << EOF
# База данных
POSTGRES_DB=tbot_db
POSTGRES_USER=postgres
POSTGRES_PASSWORD=change_this_password

# Tinkoff API токены
TINKOFF_SANDBOX_TOKEN=your_sandbox_token_here
TINKOFF_PRODUCTION_TOKEN=your_production_token_here

# Настройки приложения
SERVER_PORT=8081
DB_URL=jdbc:postgresql://postgres:5432/tbot_db
DB_USER=postgres
DB_PASSWORD=change_this_password

# Дополнительные настройки
TINKOFF_DEFAULT_MODE=sandbox
EOF
    log_warning "Создан шаблон .env файла. Отредактируйте его перед запуском!"
}

# Проверка Docker
check_docker() {
    log_info "Проверка Docker..."
    
    if ! command -v docker &> /dev/null; then
        log_error "Docker не установлен"
        exit 1
    fi
    
    if ! command -v docker-compose &> /dev/null; then
        log_error "Docker Compose не установлен"
        exit 1
    fi
    
    if ! docker info &> /dev/null; then
        log_error "Docker не запущен или нет прав доступа"
        exit 1
    fi
    
    log_success "Docker готов к работе"
}

# Сборка проекта
build_project() {
    log_info "Сборка проекта..."
    
    if ! command -v mvn &> /dev/null; then
        log_error "Maven не установлен"
        exit 1
    fi
    
    mvn clean package -DskipTests
    
    if [[ ! -f "target/*.jar" ]]; then
        log_error "JAR файл не создан"
        exit 1
    fi
    
    log_success "Проект успешно собран"
}

# Остановка существующих контейнеров
stop_containers() {
    log_info "Остановка существующих контейнеров..."
    
    if docker-compose -f $COMPOSE_FILE ps | grep -q "Up"; then
        docker-compose -f $COMPOSE_FILE down
        log_success "Контейнеры остановлены"
    else
        log_info "Нет запущенных контейнеров"
    fi
}

# Создание резервной копии базы данных
backup_database() {
    log_info "Создание резервной копии базы данных..."
    
    if docker ps | grep -q "tbot_postgres"; then
        BACKUP_FILE="backup_$(date +%Y%m%d_%H%M%S).sql"
        docker exec tbot_postgres pg_dump -U postgres tbot_db > $BACKUP_FILE 2>/dev/null || true
        if [[ -f "$BACKUP_FILE" ]]; then
            log_success "Резервная копия создана: $BACKUP_FILE"
        fi
    fi
}

# Запуск приложения
start_application() {
    log_info "Запуск приложения..."
    
    docker-compose -f $COMPOSE_FILE up -d --build
    
    log_success "Приложение запущено"
}

# Проверка здоровья приложения
check_health() {
    log_info "Проверка здоровья приложения..."
    
    # Ждем запуска приложения
    sleep 30
    
    # Проверяем статус контейнеров
    if docker-compose -f $COMPOSE_FILE ps | grep -q "Up"; then
        log_success "Все контейнеры запущены"
    else
        log_error "Не все контейнеры запущены"
        docker-compose -f $COMPOSE_FILE logs
        exit 1
    fi
    
    # Проверяем доступность приложения
    if curl -f http://localhost:8081/actuator/health &> /dev/null; then
        log_success "Приложение доступно"
    else
        log_warning "Приложение еще не готово, проверьте логи"
        docker-compose -f $COMPOSE_FILE logs tbot-app
    fi
}

# Показ информации о развертывании
show_info() {
    log_info "Информация о развертывании:"
    echo "=================================="
    echo "Окружение: $ENVIRONMENT"
    echo "Порт приложения: 8081"
    echo "База данных: PostgreSQL (порт 5432)"
    echo "Веб-интерфейс: http://localhost:8081"
    echo ""
    echo "Полезные команды:"
    echo "  Просмотр логов: docker-compose -f $COMPOSE_FILE logs -f"
    echo "  Остановка: docker-compose -f $COMPOSE_FILE down"
    echo "  Перезапуск: docker-compose -f $COMPOSE_FILE restart"
    echo "  Статус: docker-compose -f $COMPOSE_FILE ps"
    echo ""
}

# Основная функция
main() {
    log_info "Начинаем процесс развертывания..."
    
    check_files
    check_docker
    build_project
    backup_database
    stop_containers
    start_application
    check_health
    show_info
    
    log_success "Развертывание завершено успешно!"
}

# Запуск основной функции
main "$@"
