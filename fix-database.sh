#!/bin/bash

# Скрипт для принудительной инициализации базы данных tbot_db
# Выполняется на продакшн сервере

set -euo pipefail

echo "=== Инициализация базы данных tbot_db ==="

# Проверяем, что мы в правильной директории
if [ ! -f "docker-compose.production.yml" ]; then
    echo "Ошибка: docker-compose.production.yml не найден в текущей директории"
    exit 1
fi

# Останавливаем контейнеры
echo "Останавливаем контейнеры..."
docker compose -f docker-compose.production.yml down

# Удаляем том с данными PostgreSQL для принудительной инициализации
echo "Удаляем том с данными PostgreSQL..."
docker volume rm tbot_postgres_data 2>/dev/null || echo "Том не найден, создаем новый"

# Запускаем только PostgreSQL для инициализации
echo "Запускаем PostgreSQL для инициализации..."
docker compose -f docker-compose.production.yml up -d postgres

# Ждем, пока PostgreSQL будет готов
echo "Ожидаем готовности PostgreSQL..."
sleep 10

# Проверяем, что база данных создана
echo "Проверяем создание базы данных..."
docker exec tbot_postgres psql -U postgres -d tbot_db -c "SELECT version();" || {
    echo "Ошибка: База данных tbot_db не создана или недоступна"
    exit 1
}

echo "База данных tbot_db успешно создана!"

# Проверяем таблицы
echo "Проверяем создание таблиц..."
docker exec tbot_postgres psql -U postgres -d tbot_db -c "\dt" || {
    echo "Предупреждение: Таблицы не найдены, они будут созданы при запуске приложения"
}

# Запускаем все сервисы
echo "Запускаем все сервисы..."
docker compose -f docker-compose.production.yml up -d

echo "=== Инициализация завершена ==="
echo "Проверьте логи: docker compose -f docker-compose.production.yml logs -f"


