#!/bin/bash

# Скрипт для инициализации базы данных PostgreSQL
# Этот скрипт создает базу данных tbot_db если она не существует

set -e

echo "Инициализация базы данных PostgreSQL..."

# Проверяем, запущен ли PostgreSQL контейнер
if ! docker ps | grep -q tbot_postgres; then
    echo "Ошибка: Контейнер PostgreSQL не запущен. Запустите docker-compose сначала."
    exit 1
fi

# Ждем, пока PostgreSQL будет готов
echo "Ожидание готовности PostgreSQL..."
until docker exec tbot_postgres pg_isready -U postgres; do
    echo "PostgreSQL еще не готов, ждем..."
    sleep 2
done

echo "PostgreSQL готов!"

# Создаем базу данных если она не существует
echo "Проверка существования базы данных tbot_db..."
if ! docker exec tbot_postgres psql -U postgres -lqt | cut -d \| -f 1 | grep -qw tbot_db; then
    echo "Создание базы данных tbot_db..."
    docker exec tbot_postgres createdb -U postgres tbot_db
    echo "База данных tbot_db создана успешно!"
else
    echo "База данных tbot_db уже существует."
fi

# Выполняем инициализационный скрипт
echo "Выполнение инициализационного скрипта..."
docker exec -i tbot_postgres psql -U postgres -d tbot_db < init-db.sql

echo "Инициализация базы данных завершена успешно!"
