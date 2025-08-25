#!/bin/bash

# Скрипт для быстрого исправления проблемы с базой данных
# Использование: ./fix-database.sh

set -e

echo "🔧 Исправление проблемы с базой данных..."

# Проверяем, запущен ли PostgreSQL контейнер
if ! docker ps | grep -q tbot_postgres; then
    echo "❌ Контейнер PostgreSQL не запущен. Запустите docker-compose сначала:"
    echo "   docker-compose -f docker-compose.production.yml up -d postgres"
    exit 1
fi

echo "✅ Контейнер PostgreSQL запущен"

# Ждем, пока PostgreSQL будет готов
echo "⏳ Ожидание готовности PostgreSQL..."
until docker exec tbot_postgres pg_isready -U postgres > /dev/null 2>&1; do
    echo "   PostgreSQL еще не готов, ждем..."
    sleep 5
done

echo "✅ PostgreSQL готов!"

# Создаем базу данных если она не существует
echo "🔍 Проверка существования базы данных tbot_db..."
if ! docker exec tbot_postgres psql -U postgres -lqt | cut -d \| -f 1 | grep -qw tbot_db; then
    echo "📝 Создание базы данных tbot_db..."
    docker exec tbot_postgres createdb -U postgres tbot_db
    echo "✅ База данных tbot_db создана успешно!"
else
    echo "✅ База данных tbot_db уже существует."
fi

# Выполняем инициализационный скрипт
echo "📋 Выполнение инициализационного скрипта..."
if [ -f "init-db.sql" ]; then
    docker exec -i tbot_postgres psql -U postgres -d tbot_db < init-db.sql
    echo "✅ Инициализационный скрипт выполнен успешно!"
else
    echo "⚠️  Файл init-db.sql не найден, пропускаем инициализацию"
fi

echo ""
echo "🎉 Проблема с базой данных исправлена!"
echo ""
echo "Теперь можно перезапустить приложение:"
echo "   docker-compose -f docker-compose.production.yml restart tbot-app"
echo ""
echo "Или перезапустить все сервисы:"
echo "   docker-compose -f docker-compose.production.yml restart"
