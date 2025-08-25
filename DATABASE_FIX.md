# Исправление проблемы с базой данных

## Проблема
При деплое на удаленном сервере возникает ошибка:
```
tbot_postgres | FATAL: database "tbot_db" does not exist
```

## Причина
PostgreSQL контейнер не создает базу данных `tbot_db` автоматически, несмотря на настройку `POSTGRES_DB: tbot_db`.

## Решение

### Вариант 1: Быстрое исправление (рекомендуется)

1. Запустите скрипт исправления:
```bash
./fix-database.sh
```

2. Перезапустите приложение:
```bash
docker-compose -f docker-compose.production.yml restart tbot-app
```

### Вариант 2: Ручное исправление

1. Подключитесь к PostgreSQL контейнеру:
```bash
docker exec -it tbot_postgres psql -U postgres
```

2. Создайте базу данных:
```sql
CREATE DATABASE tbot_db;
```

3. Выйдите из psql:
```sql
\q
```

4. Выполните инициализационный скрипт:
```bash
docker exec -i tbot_postgres psql -U postgres -d tbot_db < init-db.sql
```

5. Перезапустите приложение:
```bash
docker-compose -f docker-compose.production.yml restart tbot-app
```

### Вариант 3: Полный перезапуск

1. Остановите все контейнеры:
```bash
docker-compose -f docker-compose.production.yml down
```

2. Удалите том с данными PostgreSQL (ВНИМАНИЕ: это удалит все данные!):
```bash
docker volume rm tbot_postgres_data
```

3. Запустите заново:
```bash
docker-compose -f docker-compose.production.yml up -d
```

4. Выполните скрипт исправления:
```bash
./fix-database.sh
```

## Профилактика

Для предотвращения проблемы в будущем:

1. Используйте обновленный `docker-compose.production.yml` (уже исправлен)
2. Всегда запускайте `./fix-database.sh` после первого деплоя
3. Проверяйте логи PostgreSQL: `docker-compose -f docker-compose.production.yml logs postgres`

## Проверка статуса

Проверить, что база данных создана:
```bash
docker exec tbot_postgres psql -U postgres -l
```

Проверить подключение к базе данных:
```bash
docker exec tbot_postgres psql -U postgres -d tbot_db -c "SELECT version();"
```

## Логи и диагностика

Просмотр логов PostgreSQL:
```bash
docker-compose -f docker-compose.production.yml logs postgres
```

Просмотр логов приложения:
```bash
docker-compose -f docker-compose.production.yml logs tbot-app
```

## Файлы

- `init-db.sql` - Инициализационный скрипт базы данных
- `fix-database.sh` - Скрипт автоматического исправления
- `docker-compose.production.yml` - Обновленная конфигурация Docker Compose
