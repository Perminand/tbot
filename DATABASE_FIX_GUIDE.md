# Исправление проблем с базой данных

## Проблема
Приложение не запускается на удаленном сервере из-за ошибки:
```
FATAL: database "tbot_db" does not exist
```

## Причина
PostgreSQL контейнер не создает базу данных `tbot_db` при инициализации, несмотря на настройку `POSTGRES_DB=tbot_db`.

## Решение

### Вариант 1: Автоматическое исправление (рекомендуется)

```bash
# Сделать скрипт исполняемым
chmod +x fix-database.sh

# Запустить исправление
./fix-database.sh
```

### Вариант 2: Ручное исправление

#### 1. Остановить контейнеры
```bash
docker-compose down
```

#### 2. Удалить старые данные PostgreSQL
```bash
docker volume rm tbot_postgres_data
```

#### 3. Запустить только PostgreSQL
```bash
docker-compose up -d postgres
```

#### 4. Подождать запуска PostgreSQL
```bash
sleep 30
```

#### 5. Проверить создание базы данных
```bash
docker exec tbot_postgres psql -U postgres -lqt | grep tbot_db
```

#### 6. Если база не создана, создать вручную
```bash
docker exec tbot_postgres psql -U postgres -c "CREATE DATABASE tbot_db;"
```

#### 7. Запустить приложение
```bash
docker-compose up -d
```

### Вариант 3: Использование production конфигурации

```bash
# Остановить контейнеры
docker-compose down

# Удалить volume
docker volume rm tbot_postgres_data

# Запустить с production конфигурацией
docker-compose -f docker-compose.production.yml up -d
```

## Проверка исправления

### 1. Проверить контейнеры
```bash
docker ps
```

Должны быть запущены:
- `tbot_postgres` (PostgreSQL)
- `tbot_app` (Spring Boot приложение)

### 2. Проверить базу данных
```bash
# Подключиться к PostgreSQL
docker exec -it tbot_postgres psql -U postgres -d tbot_db

# Проверить таблицы
\dt

# Проверить настройки
SELECT * FROM trading_settings;

# Выйти
\q
```

### 3. Проверить приложение
```bash
# Health check
curl http://localhost:8080/actuator/health

# Основное приложение
curl http://localhost:8080/
```

## Диагностика

### Проверить логи PostgreSQL
```bash
docker logs tbot_postgres
```

### Проверить логи приложения
```bash
docker logs tbot_app
```

### Проверить переменные окружения
```bash
docker exec tbot_postgres env | grep POSTGRES
```

## Профилактика

### 1. Использовать правильные имена файлов
В docker-compose.yml файлы должны быть названы с префиксами:
```yaml
volumes:
  - ./init.sql:/docker-entrypoint-initdb.d/01-init.sql
  - ./init_trading_settings.sql:/docker-entrypoint-initdb.d/02-trading-settings.sql
```

### 2. Проверять healthcheck
```yaml
healthcheck:
  test: ["CMD-SHELL", "pg_isready -U postgres -d tbot_db"]
  interval: 10s
  timeout: 5s
  retries: 5
```

### 3. Использовать depends_on с condition
```yaml
depends_on:
  postgres:
    condition: service_healthy
```

## Частые проблемы

### 1. База данных не создается
**Решение:** Удалить volume и перезапустить PostgreSQL

### 2. Таблицы не создаются
**Решение:** Проверить права доступа и выполнить скрипты вручную

### 3. Пароль не подходит
**Решение:** Проверить переменную `POSTGRES_PASSWORD` в docker-compose.yml

### 4. Порт занят
**Решение:** Изменить порт или остановить другие сервисы

## Команды для отладки

```bash
# Проверить все volumes
docker volume ls

# Проверить все контейнеры
docker ps -a

# Проверить логи всех контейнеров
docker-compose logs

# Проверить сеть
docker network ls

# Проверить образы
docker images
```
