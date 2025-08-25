#!/bin/bash

# Быстрый старт и исправление проблемы с базой данных
# Использование: ./quick-start.sh

echo "🚀 Быстрый старт и исправление базы данных"

# Проверяем, что мы в правильной директории
if [ ! -f "docker-compose.production.yml" ]; then
    echo "❌ Ошибка: docker-compose.production.yml не найден"
    echo "Перейдите в директорию с проектом"
    exit 1
fi

echo "📋 Проверяем статус контейнеров..."
docker compose -f docker-compose.production.yml ps

echo "🛑 Останавливаем контейнеры..."
docker compose -f docker-compose.production.yml down

echo "🗑️ Удаляем том PostgreSQL..."
docker volume rm tbot_postgres_data 2>/dev/null || echo "Том не найден, создаем новый"

echo "🚀 Запускаем контейнеры..."
docker compose -f docker-compose.production.yml up -d

echo "⏳ Ожидаем запуска PostgreSQL (30 секунд)..."
sleep 30

echo "🔍 Проверяем базу данных..."
if docker exec tbot_postgres psql -U postgres -d tbot_db -c "SELECT 1;" >/dev/null 2>&1; then
    echo "✅ База данных tbot_db успешно создана!"
else
    echo "❌ База данных не создана, выполняем дополнительную инициализацию..."
    if [ -f "fix-now.sh" ]; then
        chmod +x fix-now.sh
        ./fix-now.sh
    else
        echo "⚠️ Скрипт fix-now.sh не найден, проверьте вручную"
    fi
fi

echo "🔍 Проверяем приложение..."
sleep 10
if curl -f http://localhost:8080/actuator/health >/dev/null 2>&1; then
    echo "✅ Приложение успешно запущено!"
    echo "🌐 Доступно по адресу: http://localhost:8080"
else
    echo "⚠️ Приложение не отвечает, проверьте логи:"
    echo "docker compose -f docker-compose.production.yml logs -f"
fi

echo ""
echo "📊 Статус контейнеров:"
docker compose -f docker-compose.production.yml ps

echo ""
echo "🎉 Готово! Используйте следующие команды:"
echo "  Логи: docker compose -f docker-compose.production.yml logs -f"
echo "  База данных: docker exec -it tbot_postgres psql -U postgres -d tbot_db"
echo "  Остановка: docker compose -f docker-compose.production.yml down"


