#!/bin/bash

# Быстрый запуск Tinkoff Trading Bot
# Использование: ./quick-start.sh

set -e

echo "🚀 Быстрый запуск Tinkoff Trading Bot..."

# Проверяем наличие Docker
if ! command -v docker &> /dev/null; then
    echo "❌ Docker не установлен"
    exit 1
fi

# Проверяем наличие Docker Compose
if ! command -v docker-compose &> /dev/null; then
    echo "❌ Docker Compose не установлен"
    exit 1
fi

# Останавливаем существующие контейнеры
echo "🛑 Останавливаем существующие контейнеры..."
docker-compose down --remove-orphans 2>/dev/null || true

# Собираем образ
echo "🔨 Собираем Docker образ..."
docker build -t tbot-app:latest .

# Запускаем приложение
echo "▶️ Запускаем приложение..."
docker-compose up -d

# Ждем запуска
echo "⏳ Ждем запуска приложения..."
sleep 30

# Проверяем статус
echo "📊 Проверяем статус..."
docker-compose ps

# Проверяем доступность
echo "🔍 Проверяем доступность приложения..."
if curl -f http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "✅ Приложение успешно запущено!"
    echo "🌐 Доступно по адресу: http://localhost:8080"
    echo "📝 Логи: docker-compose logs -f tbot-app"
    echo "🛑 Остановка: docker-compose down"
else
    echo "❌ Приложение не отвечает"
    echo "📝 Проверьте логи: docker-compose logs tbot-app"
    exit 1
fi
