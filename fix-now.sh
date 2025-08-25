#!/bin/bash

# Быстрое исправление проблемы с базой данных
# Выполняется на продакшн сервере

echo "=== Быстрое исправление базы данных ==="

# Останавливаем контейнеры
docker compose -f docker-compose.production.yml down

# Удаляем том PostgreSQL
docker volume rm tbot_postgres_data 2>/dev/null || echo "Том не найден"

# Запускаем заново
docker compose -f docker-compose.production.yml up -d

echo "=== Готово! Проверьте логи: docker compose -f docker-compose.production.yml logs -f ==="


