#!/bin/bash

# Простой скрипт для запуска Tinkoff Trading Bot
# Использование: ./start.sh

set -e

echo "🚀 Запуск Tinkoff Trading Bot..."

# Проверка наличия .env файла
if [[ ! -f ".env" ]]; then
    echo "❌ Файл .env не найден!"
    echo "Создайте файл .env с настройками:"
    echo ""
    echo "Скопируйте файл env-template.txt в .env и заполните своими значениями:"
    echo "cp env-template.txt .env"
    echo ""
    echo "Или создайте .env файл вручную с настройками:"
    echo "POSTGRES_DB=tbot_db"
    echo "POSTGRES_USER=postgres"
    echo "POSTGRES_PASSWORD=your_secure_password"
    echo "TINKOFF_SANDBOX_TOKEN=your_sandbox_token"
    echo "TINKOFF_PRODUCTION_TOKEN=your_production_token"
    echo "SERVER_PORT=8081"
    echo "DB_URL=jdbc:postgresql://postgres:5432/tbot_db"
    echo "DB_USER=postgres"
    echo "DB_PASSWORD=your_secure_password"
    echo "TINKOFF_DEFAULT_MODE=sandbox"
    exit 1
fi

# Проверка Docker
if ! command -v docker &> /dev/null; then
    echo "❌ Docker не установлен"
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    echo "❌ Docker Compose не установлен"
    exit 1
fi

echo "✅ Зависимости проверены"

# Остановка существующих контейнеров
echo "🛑 Остановка существующих контейнеров..."
docker-compose down 2>/dev/null || true

# Запуск приложения
echo "🏗️  Сборка и запуск приложения..."
docker-compose up -d --build

echo "⏳ Ожидание запуска приложения..."
sleep 30

# Проверка статуса
echo "📊 Статус контейнеров:"
docker-compose ps

echo ""
echo "🎉 Приложение запущено!"
echo "🌐 Веб-интерфейс: http://localhost:8081"
echo ""
echo "📋 Полезные команды:"
echo "  Логи: docker-compose logs -f"
echo "  Остановка: docker-compose down"
echo "  Перезапуск: docker-compose restart"
