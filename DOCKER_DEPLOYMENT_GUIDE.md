# Руководство по развертыванию через Docker

## Исправления для продакшена

### 1. **Исправлены порты:**
- Dockerfile: `EXPOSE 8080` (было 8081)
- docker-compose.yml: порты 8080:8080 (было 8081:8081)
- PostgreSQL: порт 5432 (было 5433)

### 2. **Улучшена production конфигурация:**
- Добавлены переменные окружения с дефолтными значениями
- Улучшен healthcheck (использует wget вместо curl)
- Добавлено подключение init_trading_settings.sql

### 3. **Созданы скрипты развертывания:**
- `deploy-production.sh` - полное развертывание с проверками
- `quick-start.sh` - быстрый запуск для разработки

## Быстрый запуск (для разработки)

### Windows:
```bash
# Остановить существующие контейнеры
docker-compose down

# Собрать и запустить
docker-compose up --build -d

# Проверить статус
docker-compose ps

# Посмотреть логи
docker-compose logs -f tbot-app
```

### Linux/Mac:
```bash
# Сделать скрипт исполняемым
chmod +x quick-start.sh

# Запустить
./quick-start.sh
```

## Полное развертывание на продакшене

### 1. Подготовка сервера:
```bash
# Установить Docker и Docker Compose
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
sudo usermod -aG docker $USER

# Установить Docker Compose
sudo curl -L "https://github.com/docker/compose/releases/download/v2.20.0/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose
```

### 2. Клонирование проекта:
```bash
git clone <your-repo-url>
cd tbot
```

### 3. Настройка переменных окружения:
```bash
# Создать .env файл
cp .env.example .env

# Отредактировать .env файл
nano .env
```

**Содержимое .env:**
```env
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
```

### 4. Запуск развертывания:
```bash
# Сделать скрипт исполняемым
chmod +x deploy-production.sh

# Запустить развертывание
./deploy-production.sh
```

## Проверка работы

### 1. Проверка контейнеров:
```bash
docker ps
```

Должны быть запущены:
- `tbot_postgres` (PostgreSQL)
- `tbot_app` (Spring Boot приложение)

### 2. Проверка доступности:
```bash
# Проверка health endpoint
curl http://localhost:8080/actuator/health

# Проверка основного приложения
curl http://localhost:8080/
```

### 3. Просмотр логов:
```bash
# Логи приложения
docker logs tbot_app

# Логи базы данных
docker logs tbot_postgres

# Логи в реальном времени
docker logs -f tbot_app
```

## Управление приложением

### Остановка:
```bash
docker-compose -f docker-compose.production.yml down
```

### Перезапуск:
```bash
docker-compose -f docker-compose.production.yml restart
```

### Обновление:
```bash
# Остановить
docker-compose -f docker-compose.production.yml down

# Пересобрать образ
docker build -t tbot-app:latest .

# Запустить заново
docker-compose -f docker-compose.production.yml up -d
```

## Диагностика проблем

### 1. Контейнеры не запускаются:
```bash
# Проверить логи
docker-compose logs

# Проверить статус
docker-compose ps -a
```

### 2. Приложение не отвечает:
```bash
# Проверить порты
netstat -tuln | grep 8080

# Проверить health endpoint
curl -v http://localhost:8080/actuator/health
```

### 3. Проблемы с базой данных:
```bash
# Подключиться к базе
docker exec -it tbot_postgres psql -U postgres -d tbot_db

# Проверить таблицы
\dt

# Проверить настройки
SELECT * FROM trading_settings;
```

### 4. Проблемы с памятью:
```bash
# Проверить использование памяти
docker stats

# Увеличить лимиты в docker-compose.production.yml
JAVA_OPTS: -Xmx2g -Xms1g
```

## Настройка SSL (опционально)

### 1. Создать SSL сертификаты:
```bash
mkdir ssl
# Поместить сертификаты в папку ssl/
```

### 2. Запустить с nginx:
```bash
docker-compose -f docker-compose.production.yml --profile nginx up -d
```

## Мониторинг

### 1. Системные метрики:
```bash
# Использование ресурсов
docker stats

# Логи приложения
docker logs -f tbot_app | grep -E "(ERROR|WARN|TRADE)"
```

### 2. API метрики:
```bash
# Health check
curl http://localhost:8080/actuator/health

# Метрики приложения
curl http://localhost:8080/actuator/metrics
```

## Безопасность

### 1. Изменить дефолтные пароли:
- В файле .env изменить `APP_PASSWORD`
- В файле .env изменить `DB_PASSWORD`

### 2. Настроить firewall:
```bash
# Открыть только необходимые порты
sudo ufw allow 22/tcp   # SSH
sudo ufw allow 80/tcp   # HTTP
sudo ufw allow 443/tcp  # HTTPS
sudo ufw enable
```

### 3. Регулярные обновления:
```bash
# Обновить образы
docker pull postgres:15-alpine
docker pull nginx:alpine

# Пересобрать приложение
docker build -t tbot-app:latest .
```

## Резервное копирование

### 1. База данных:
```bash
# Создать бэкап
docker exec tbot_postgres pg_dump -U postgres tbot_db > backup.sql

# Восстановить
docker exec -i tbot_postgres psql -U postgres tbot_db < backup.sql
```

### 2. Конфигурация:
```bash
# Сохранить .env файл
cp .env backup.env

# Сохранить docker-compose файлы
cp docker-compose.production.yml backup/
```
