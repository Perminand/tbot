# Руководство по развертыванию на удаленном сервере

## Обзор

Это руководство поможет вам развернуть Tinkoff Trading Bot на удаленном сервере с использованием Docker и Docker Compose.

## Предварительные требования

### На сервере должны быть установлены:
- **Docker** (версия 20.10+)
- **Docker Compose** (версия 2.0+)
- **Git** (для клонирования репозитория)
- **Java 21** (для сборки проекта)

### Проверка установки:
```bash
docker --version
docker-compose --version
git --version
java --version
```

## Пошаговое развертывание

### 1. Подготовка сервера

#### Обновление системы (Ubuntu/Debian):
```bash
sudo apt update && sudo apt upgrade -y
```

#### Установка Docker (если не установлен):
```bash
# Установка Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# Добавление пользователя в группу docker
sudo usermod -aG docker $USER

# Перезагрузка или перелогин
sudo systemctl enable docker
sudo systemctl start docker
```

#### Установка Docker Compose:
```bash
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose
```

### 2. Клонирование проекта

```bash
# Клонирование репозитория
git clone <URL_ВАШЕГО_РЕПОЗИТОРИЯ> tbot
cd tbot

# Или если у вас уже есть код, загрузите его на сервер
```

### 3. Настройка переменных окружения

Создайте файл `.env` в корне проекта:

```bash
# Создание файла .env
cat > .env << EOF
# База данных
POSTGRES_DB=tbot_db
POSTGRES_USER=postgres
POSTGRES_PASSWORD=your_secure_password_here

# Tinkoff API токены
TINKOFF_SANDBOX_TOKEN=your_sandbox_token_here
TINKOFF_PRODUCTION_TOKEN=your_production_token_here

# Настройки приложения
SERVER_PORT=8081
DB_URL=jdbc:postgresql://postgres:5432/tbot_db
DB_USER=postgres
DB_PASSWORD=your_secure_password_here

# Дополнительные настройки
TINKOFF_DEFAULT_MODE=sandbox
EOF
```

### 4. Сборка проекта

```bash
# Установка Maven (если не установлен)
sudo apt install maven -y

# Сборка проекта
mvn clean package -DskipTests

# Проверка, что JAR файл создан
ls -la target/*.jar
```

### 5. Настройка Docker Compose

Обновите `docker-compose.yml` для продакшена:

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:15-alpine
    container_name: tbot_postgres
    restart: unless-stopped
    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    ports:
      - "5432:5432"  # Изменено с 5433 на 5432 для продакшена
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./init.sql:/docker-entrypoint-initdb.d/init.sql
    networks:
      - tbot_network
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER} -d ${POSTGRES_DB}"]
      interval: 10s
      timeout: 5s
      retries: 5

  tbot-app:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: tbot_app
    depends_on:
      postgres:
        condition: service_healthy
    environment:
      DB_URL: ${DB_URL}
      DB_USER: ${DB_USER}
      DB_PASSWORD: ${DB_PASSWORD}
      TINKOFF_SANDBOX_TOKEN: ${TINKOFF_SANDBOX_TOKEN}
      TINKOFF_PRODUCTION_TOKEN: ${TINKOFF_PRODUCTION_TOKEN}
      TINKOFF_DEFAULT_MODE: ${TINKOFF_DEFAULT_MODE}
      SERVER_PORT: ${SERVER_PORT}
    ports:
      - "${SERVER_PORT}:${SERVER_PORT}"
    networks:
      - tbot_network
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:${SERVER_PORT}/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

volumes:
  postgres_data:
    driver: local

networks:
  tbot_network:
    driver: bridge
```

### 6. Запуск приложения

```bash
# Запуск в фоновом режиме
docker-compose up -d

# Проверка статуса контейнеров
docker-compose ps

# Просмотр логов
docker-compose logs -f
```

### 7. Проверка работоспособности

```bash
# Проверка доступности приложения
curl http://localhost:8081/actuator/health

# Проверка базы данных
docker exec -it tbot_postgres psql -U postgres -d tbot_db -c "\dt"
```

## Настройка веб-сервера (опционально)

### Nginx как reverse proxy

Установите Nginx:

```bash
sudo apt install nginx -y
```

Создайте конфигурацию:

```bash
sudo nano /etc/nginx/sites-available/tbot
```

Содержимое конфигурации:

```nginx
server {
    listen 80;
    server_name your-domain.com;  # Замените на ваш домен

    location / {
        proxy_pass http://localhost:8081;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

Активируйте конфигурацию:

```bash
sudo ln -s /etc/nginx/sites-available/tbot /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl restart nginx
```

### SSL сертификат с Let's Encrypt

```bash
# Установка Certbot
sudo apt install certbot python3-certbot-nginx -y

# Получение SSL сертификата
sudo certbot --nginx -d your-domain.com

# Автоматическое обновление
sudo crontab -e
# Добавьте строку:
# 0 12 * * * /usr/bin/certbot renew --quiet
```

## Мониторинг и логирование

### Просмотр логов

```bash
# Логи приложения
docker-compose logs -f tbot-app

# Логи базы данных
docker-compose logs -f postgres

# Все логи
docker-compose logs -f
```

### Мониторинг ресурсов

```bash
# Использование ресурсов контейнерами
docker stats

# Дисковое пространство
df -h

# Использование памяти
free -h
```

## Резервное копирование

### База данных

```bash
# Создание бэкапа
docker exec tbot_postgres pg_dump -U postgres tbot_db > backup_$(date +%Y%m%d_%H%M%S).sql

# Восстановление из бэкапа
docker exec -i tbot_postgres psql -U postgres tbot_db < backup_file.sql
```

### Автоматическое резервное копирование

Создайте скрипт `backup.sh`:

```bash
#!/bin/bash
BACKUP_DIR="/backups"
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="tbot_backup_$DATE.sql"

mkdir -p $BACKUP_DIR
docker exec tbot_postgres pg_dump -U postgres tbot_db > $BACKUP_DIR/$BACKUP_FILE

# Удаление старых бэкапов (старше 7 дней)
find $BACKUP_DIR -name "tbot_backup_*.sql" -mtime +7 -delete
```

Добавьте в crontab:

```bash
# Ежедневный бэкап в 2:00
0 2 * * * /path/to/backup.sh
```

## Обновление приложения

### Процесс обновления

```bash
# Остановка приложения
docker-compose down

# Получение обновлений
git pull origin main

# Пересборка и запуск
mvn clean package -DskipTests
docker-compose up -d --build

# Проверка статуса
docker-compose ps
```

## Устранение неполадок

### Частые проблемы

#### 1. Порт уже занят
```bash
# Проверка занятых портов
sudo netstat -tulpn | grep :8081

# Остановка процесса
sudo kill -9 <PID>
```

#### 2. Проблемы с базой данных
```bash
# Проверка статуса PostgreSQL
docker-compose logs postgres

# Перезапуск базы данных
docker-compose restart postgres
```

#### 3. Проблемы с памятью
```bash
# Очистка неиспользуемых Docker ресурсов
docker system prune -a

# Проверка использования диска
docker system df
```

#### 4. Проблемы с сетью
```bash
# Проверка сетевых настроек
docker network ls
docker network inspect tbot_tbot_network
```

### Полезные команды

```bash
# Перезапуск всех сервисов
docker-compose restart

# Просмотр переменных окружения
docker-compose exec tbot-app env

# Подключение к базе данных
docker-compose exec postgres psql -U postgres -d tbot_db

# Проверка здоровья приложения
curl http://localhost:8081/actuator/health
```

## Безопасность

### Рекомендации по безопасности

1. **Измените пароли по умолчанию**
2. **Используйте HTTPS**
3. **Настройте файрвол**
4. **Регулярно обновляйте систему**
5. **Мониторьте логи**

### Настройка файрвола (UFW)

```bash
# Установка UFW
sudo apt install ufw -y

# Настройка правил
sudo ufw allow ssh
sudo ufw allow 80
sudo ufw allow 443
sudo ufw allow 8081  # Только если не используете Nginx

# Включение файрвола
sudo ufw enable
```

## Заключение

После выполнения всех шагов ваше приложение будет доступно по адресу:
- **Локально**: http://localhost:8081
- **Через домен**: https://your-domain.com (если настроен Nginx + SSL)

### Контакты для поддержки

При возникновении проблем:
1. Проверьте логи: `docker-compose logs -f`
2. Убедитесь, что все порты свободны
3. Проверьте переменные окружения
4. Убедитесь, что токены Tinkoff API корректны
