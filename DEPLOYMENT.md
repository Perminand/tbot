# Развертывание Tinkoff Trading Bot

## Требования

- Docker 20.10+
- Docker Compose 2.0+
- Минимум 2GB RAM
- 10GB свободного места на диске

## Быстрое развертывание

### 1. Клонирование репозитория
```bash
git clone <your-repo-url>
cd tbot
```

### 2. Настройка переменных окружения
Создайте файл `.env` в корне проекта:
```bash
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
```

### 3. Запуск с помощью скрипта
```bash
chmod +x deploy.sh
./deploy.sh staging  # или production
```

### 4. Ручной запуск
```bash
# Сборка и запуск
docker-compose up --build -d

# Проверка статуса
docker-compose ps

# Просмотр логов
docker-compose logs -f tbot-app
```

## Доступные сервисы

После успешного развертывания будут доступны:

- **Веб-интерфейс приложения**: http://localhost:8081
- **PgAdmin (управление БД)**: http://localhost:5050
  - Email: admin@tbot.com
  - Password: admin

## Управление контейнерами

### Остановка
```bash
docker-compose down
```

### Перезапуск
```bash
docker-compose restart
```

### Обновление
```bash
docker-compose down
docker-compose pull
docker-compose up --build -d
```

### Просмотр логов
```bash
# Все сервисы
docker-compose logs

# Только приложение
docker-compose logs tbot-app

# Следить за логами в реальном времени
docker-compose logs -f tbot-app
```

## Мониторинг

### Проверка здоровья приложения
```bash
curl http://localhost:8081/actuator/health
```

### Статистика контейнеров
```bash
docker stats
```

### Использование ресурсов
```bash
docker system df
```

## Безопасность

### Рекомендации для продакшена

1. **Измените пароли по умолчанию**:
   - Пароль PostgreSQL в `.env`
   - Пароль PgAdmin в `docker-compose.yml`

2. **Настройте SSL/TLS**:
   - Используйте reverse proxy (nginx) с SSL
   - Настройте HTTPS для веб-интерфейса

3. **Ограничьте доступ**:
   - Настройте firewall
   - Используйте VPN для доступа к PgAdmin

4. **Регулярные бэкапы**:
   ```bash
   # Бэкап базы данных
   docker exec tbot_postgres pg_dump -U postgres tbot_db > backup.sql
   ```

## Устранение неполадок

### Порт недоступен

Если порт 8081 (или другой) недоступен, попробуйте:

1. **Проверьте, не занят ли порт:**
   ```bash
   # Linux/Mac
   netstat -tuln | grep :8081
   
   # Windows
   netstat -an | findstr :8081
   ```

2. **Используйте другой порт:**
   ```bash
   # Измените в docker-compose.yml
   ports:
     - "8080:8080"  # Вместо 8081
   ```

3. **Откройте порт в firewall:**
   ```bash
   # Ubuntu/Debian
   sudo ufw allow 8081/tcp
   
   # CentOS/RHEL
   sudo firewall-cmd --permanent --add-port=8081/tcp
   sudo firewall-cmd --reload
   ```

4. **Используйте простую конфигурацию:**
   ```bash
   docker-compose -f docker-compose.simple.yml up -d
   ```

### Приложение не запускается
```bash
# Проверьте логи
docker-compose logs tbot-app

# Проверьте статус контейнеров
docker-compose ps

# Перезапустите с пересборкой
docker-compose down
docker-compose up --build
```

### Проблемы с базой данных
```bash
# Проверьте подключение к БД
docker exec -it tbot_postgres psql -U postgres -d tbot_db

# Проверьте логи PostgreSQL
docker-compose logs postgres
```

### Недостаточно памяти
```bash
# Уменьшите размер heap в .env
JAVA_OPTS=-Xmx512m -Xms256m
```

## Масштабирование

### Горизонтальное масштабирование
```bash
# Запуск нескольких экземпляров приложения
docker-compose up --scale tbot-app=3 -d
```

### Вертикальное масштабирование
Измените `JAVA_OPTS` в `.env`:
```bash
JAVA_OPTS=-Xmx2g -Xms1g
```

## Обновление приложения

### Автоматическое обновление
```bash
# Остановка
docker-compose down

# Получение обновлений
git pull

# Пересборка и запуск
docker-compose up --build -d
```

### Откат к предыдущей версии
```bash
# Остановка
docker-compose down

# Откат к предыдущему коммиту
git checkout HEAD~1

# Пересборка и запуск
docker-compose up --build -d
```
