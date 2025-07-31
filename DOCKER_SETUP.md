# Настройка Docker Compose для Tinkoff Trading Bot

## Обзор

Docker Compose файл настроен для запуска PostgreSQL базы данных и pgAdmin для управления базой данных.

## Сервисы

### 1. PostgreSQL (основной файл)
- **Образ**: `postgres:15-alpine`
- **Порт**: `5432`
- **База данных**: `tbot_db`
- **Пользователь**: `postgres`
- **Пароль**: `password`

### 2. pgAdmin (отдельный файл)
- **Образ**: `dpage/pgadmin4:latest`
- **Порт**: `8081`
- **Email**: `admin@tbot.com`
- **Пароль**: `admin`

## Запуск

### 1. Запуск PostgreSQL

```bash
docker-compose up -d
```

### 2. Запуск pgAdmin (опционально)

```bash
docker-compose -f docker-compose.pgadmin.yml up -d
```

### 2. Проверка статуса

```bash
docker-compose ps
```

### 3. Просмотр логов

```bash
# Все сервисы
docker-compose logs

# Только PostgreSQL
docker-compose logs postgres

# Только pgAdmin
docker-compose logs pgadmin
```

### 4. Остановка сервисов

```bash
docker-compose down
```

### 5. Остановка с удалением данных

```bash
docker-compose down -v
```

## Подключение к базе данных

### Через приложение

Приложение автоматически подключится к базе данных по адресу:
- **URL**: `jdbc:postgresql://localhost:5432/tbot_db`
- **Username**: `postgres`
- **Password**: `password`

### Через pgAdmin

1. Откройте браузер и перейдите по адресу: `http://localhost:8081`
2. Войдите с учетными данными:
   - **Email**: `admin@tbot.com`
   - **Password**: `admin`
3. Добавьте новый сервер:
   - **Name**: `Tinkoff Bot DB`
   - **Host**: `postgres`
   - **Port**: `5432`
   - **Database**: `tbot_db`
   - **Username**: `postgres`
   - **Password**: `password`

### Через командную строку

```bash
# Подключение к контейнеру PostgreSQL
docker exec -it tbot_postgres psql -U postgres -d tbot_db

# Или подключение с хоста
psql -h localhost -p 5432 -U postgres -d tbot_db
```

## Структура базы данных

### Основные таблицы

- `instruments` - Финансовые инструменты
- `orders` - Торговые ордера
- `positions` - Позиции в портфеле
- `order_log` - Лог изменений ордеров

### Представления

- `portfolio_summary` - Сводка по портфелю
- `active_orders` - Активные ордера

### Функции

- `get_instrument_stats()` - Статистика по инструментам
- `get_order_history()` - История ордеров

## Полезные команды

### Проверка подключения к базе

```bash
# Проверка доступности PostgreSQL
docker exec tbot_postgres pg_isready -U postgres

# Проверка размера базы данных
docker exec tbot_postgres psql -U postgres -d tbot_db -c "SELECT pg_size_pretty(pg_database_size('tbot_db'));"
```

### Резервное копирование

```bash
# Создание бэкапа
docker exec tbot_postgres pg_dump -U postgres tbot_db > backup.sql

# Восстановление из бэкапа
docker exec -i tbot_postgres psql -U postgres tbot_db < backup.sql
```

### Очистка данных

```bash
# Остановка и удаление всех данных
docker-compose down -v

# Удаление только volumes
docker volume rm tbot_postgres_data
```

## Настройка для продакшена

### Изменение паролей

1. Создайте файл `.env`:

```env
POSTGRES_PASSWORD=your_secure_password
PGADMIN_PASSWORD=your_secure_pgadmin_password
```

2. Обновите `docker-compose.yml`:

```yaml
environment:
  POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
  PGADMIN_DEFAULT_PASSWORD: ${PGADMIN_PASSWORD}
```

### Настройка безопасности

1. Измените порты в продакшене
2. Используйте сильные пароли
3. Настройте SSL для PostgreSQL
4. Ограничьте доступ к pgAdmin

## Мониторинг

### Проверка использования ресурсов

```bash
# Использование CPU и памяти
docker stats tbot_postgres tbot_pgadmin

# Размер volumes
docker system df -v
```

### Логи и отладка

```bash
# Подробные логи PostgreSQL
docker-compose logs -f postgres

# Проверка конфигурации PostgreSQL
docker exec tbot_postgres cat /var/lib/postgresql/data/postgresql.conf
```

## Устранение неполадок

### Проблема: Порт 5432 занят

```bash
# Проверка занятых портов
netstat -an | grep 5432

# Изменение порта в docker-compose.yml
ports:
  - "5433:5432"  # Используйте другой порт
```

### Проблема: Не удается подключиться к базе

```bash
# Проверка статуса контейнера
docker-compose ps

# Перезапуск сервиса
docker-compose restart postgres

# Проверка логов
docker-compose logs postgres
```

### Проблема: Данные не сохраняются

```bash
# Проверка volumes
docker volume ls

# Проверка содержимого volume
docker run --rm -v tbot_postgres_data:/data alpine ls -la /data
```

## Дополнительные настройки

### Оптимизация PostgreSQL

Создайте файл `postgresql.conf` в корне проекта:

```conf
# Настройки памяти
shared_buffers = 256MB
effective_cache_size = 1GB
work_mem = 4MB
maintenance_work_mem = 64MB

# Настройки логирования
log_statement = 'all'
log_duration = on
log_min_duration_statement = 1000

# Настройки производительности
random_page_cost = 1.1
effective_io_concurrency = 200
```

И добавьте в `docker-compose.yml`:

```yaml
volumes:
  - ./postgresql.conf:/etc/postgresql/postgresql.conf
command: postgres -c config_file=/etc/postgresql/postgresql.conf
``` 