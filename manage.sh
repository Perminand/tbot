#!/bin/bash

# Скрипт для управления Tinkoff Trading Bot
# Использование: ./manage.sh [start|stop|restart|status|logs|backup|update]

set -e

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Конфигурация
COMPOSE_FILE="docker-compose.prod.yml"
APP_NAME="Tinkoff Trading Bot"

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

# Проверка Docker
check_docker() {
    if ! command -v docker &> /dev/null; then
        log_error "Docker не установлен"
        exit 1
    fi
    
    if ! command -v docker-compose &> /dev/null; then
        log_error "Docker Compose не установлен"
        exit 1
    fi
}

# Проверка файлов конфигурации
check_config() {
    if [[ ! -f "$COMPOSE_FILE" ]]; then
        log_error "Файл $COMPOSE_FILE не найден"
        exit 1
    fi
    
    if [[ ! -f ".env" ]]; then
        log_error "Файл .env не найден"
        exit 1
    fi
}

# Запуск приложения
start_app() {
    log_info "Запуск $APP_NAME..."
    check_docker
    check_config
    
    docker-compose -f $COMPOSE_FILE up -d
    log_success "$APP_NAME запущен"
    
    # Показываем статус
    sleep 5
    show_status
}

# Остановка приложения
stop_app() {
    log_info "Остановка $APP_NAME..."
    check_docker
    
    docker-compose -f $COMPOSE_FILE down
    log_success "$APP_NAME остановлен"
}

# Перезапуск приложения
restart_app() {
    log_info "Перезапуск $APP_NAME..."
    stop_app
    sleep 2
    start_app
}

# Показать статус
show_status() {
    log_info "Статус $APP_NAME:"
    echo "=================================="
    
    if docker-compose -f $COMPOSE_FILE ps | grep -q "Up"; then
        log_success "Приложение запущено"
        echo ""
        docker-compose -f $COMPOSE_FILE ps
        echo ""
        
        # Проверка доступности
        if curl -f http://localhost:8081/actuator/health &> /dev/null; then
            log_success "Веб-интерфейс доступен: http://localhost:8081"
        else
            log_warning "Веб-интерфейс еще не готов"
        fi
    else
        log_warning "Приложение не запущено"
    fi
}

# Показать логи
show_logs() {
    log_info "Логи $APP_NAME:"
    echo "=================================="
    
    if [[ "$1" == "app" ]]; then
        docker-compose -f $COMPOSE_FILE logs -f tbot-app
    elif [[ "$1" == "db" ]]; then
        docker-compose -f $COMPOSE_FILE logs -f postgres
    else
        docker-compose -f $COMPOSE_FILE logs -f
    fi
}

# Создание резервной копии
create_backup() {
    log_info "Создание резервной копии базы данных..."
    
    if docker ps | grep -q "tbot_postgres"; then
        BACKUP_FILE="backup_$(date +%Y%m%d_%H%M%S).sql"
        docker exec tbot_postgres pg_dump -U postgres tbot_db > $BACKUP_FILE
        
        if [[ -f "$BACKUP_FILE" ]]; then
            log_success "Резервная копия создана: $BACKUP_FILE"
            echo "Размер файла: $(du -h $BACKUP_FILE | cut -f1)"
        else
            log_error "Ошибка создания резервной копии"
        fi
    else
        log_error "База данных не запущена"
    fi
}

# Обновление приложения
update_app() {
    log_info "Обновление $APP_NAME..."
    
    # Создание резервной копии
    create_backup
    
    # Остановка приложения
    stop_app
    
    # Получение обновлений (если используется Git)
    if [[ -d ".git" ]]; then
        log_info "Получение обновлений из Git..."
        git pull origin main
    fi
    
    # Пересборка и запуск
    log_info "Пересборка приложения..."
    docker-compose -f $COMPOSE_FILE up -d --build
    
    log_success "$APP_NAME обновлен"
    
    # Показываем статус
    sleep 10
    show_status
}

# Очистка Docker ресурсов
cleanup() {
    log_info "Очистка неиспользуемых Docker ресурсов..."
    
    docker system prune -f
    docker volume prune -f
    
    log_success "Очистка завершена"
}

# Мониторинг ресурсов
monitor() {
    log_info "Мониторинг ресурсов:"
    echo "=================================="
    
    echo "Использование ресурсов контейнерами:"
    docker stats --no-stream
    
    echo ""
    echo "Использование диска:"
    df -h
    
    echo ""
    echo "Использование памяти:"
    free -h
}

# Показать справку
show_help() {
    echo "Скрипт управления $APP_NAME"
    echo ""
    echo "Использование: $0 [команда]"
    echo ""
    echo "Команды:"
    echo "  start     - Запустить приложение"
    echo "  stop      - Остановить приложение"
    echo "  restart   - Перезапустить приложение"
    echo "  status    - Показать статус"
    echo "  logs      - Показать логи (все)"
    echo "  logs app  - Показать логи приложения"
    echo "  logs db   - Показать логи базы данных"
    echo "  backup    - Создать резервную копию"
    echo "  update    - Обновить приложение"
    echo "  cleanup   - Очистить Docker ресурсы"
    echo "  monitor   - Мониторинг ресурсов"
    echo "  help      - Показать эту справку"
    echo ""
    echo "Примеры:"
    echo "  $0 start"
    echo "  $0 logs app"
    echo "  $0 status"
}

# Основная логика
case "${1:-help}" in
    start)
        start_app
        ;;
    stop)
        stop_app
        ;;
    restart)
        restart_app
        ;;
    status)
        show_status
        ;;
    logs)
        show_logs "$2"
        ;;
    backup)
        create_backup
        ;;
    update)
        update_app
        ;;
    cleanup)
        cleanup
        ;;
    monitor)
        monitor
        ;;
    help|--help|-h)
        show_help
        ;;
    *)
        log_error "Неизвестная команда: $1"
        echo ""
        show_help
        exit 1
        ;;
esac
