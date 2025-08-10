# 🚀 Быстрый старт Tinkoff Trading Bot

## Предварительные требования

- **Docker** (версия 20.10+)
- **Docker Compose** (версия 2.0+)

## Быстрый запуск

### 1. Настройка переменных окружения

Создайте файл `.env` на основе шаблона:

```bash
# Windows
copy env-template.txt .env

# Linux/Mac
cp env-template.txt .env
```

Заполните файл `.env` своими значениями:

```env
# База данных
POSTGRES_DB=tbot_db
POSTGRES_USER=postgres
POSTGRES_PASSWORD=your_secure_password

# Tinkoff API токены
TINKOFF_SANDBOX_TOKEN=your_sandbox_token
TINKOFF_PRODUCTION_TOKEN=your_production_token

# Настройки приложения
SERVER_PORT=8081
DB_URL=jdbc:postgresql://postgres:5432/tbot_db
DB_USER=postgres
DB_PASSWORD=your_secure_password
TINKOFF_DEFAULT_MODE=sandbox
```

### 2. Запуск приложения

#### Windows:
```powershell
.\start.ps1
```

#### Linux/Mac:
```bash
chmod +x start.sh
./start.sh
```

#### Или вручную:
```bash
docker-compose up -d --build
```

### 3. Проверка работы

Приложение будет доступно по адресу: **http://localhost:8081**

## Управление приложением

### Основные команды:
```bash
# Запуск
docker-compose up -d

# Остановка
docker-compose down

# Логи
docker-compose logs -f

# Статус
docker-compose ps

# Перезапуск
docker-compose restart
```

### Просмотр логов:
```bash
# Все логи
docker-compose logs -f

# Логи приложения
docker-compose logs -f tbot-app

# Логи базы данных
docker-compose logs -f postgres
```

## Получение токенов Tinkoff API

1. Зарегистрируйтесь на [Tinkoff Invest](https://www.tinkoff.ru/invest/)
2. Перейдите в "Настройки" → "API"
3. Создайте токены для песочницы и продакшена
4. Добавьте токены в файл `.env`

## Устранение неполадок

### Приложение не запускается:
```bash
# Проверка логов
docker-compose logs

# Проверка статуса
docker-compose ps

# Перезапуск
docker-compose down
docker-compose up -d --build
```

### Проблемы с портами:
```bash
# Проверка занятых портов
netstat -tulpn | grep :8081

# Остановка процесса на порту
sudo kill -9 <PID>
```

### Проблемы с базой данных:
```bash
# Проверка статуса PostgreSQL
docker-compose logs postgres

# Подключение к базе
docker exec -it tbot_postgres psql -U postgres -d tbot_db
```

## Структура проекта

```
tbot/
├── src/                    # Исходный код
├── docker-compose.yml      # Конфигурация Docker
├── Dockerfile             # Образ приложения
├── env-template.txt       # Шаблон .env файла
├── start.sh              # Скрипт запуска (Linux)
├── start.ps1             # Скрипт запуска (Windows)
└── init.sql              # Инициализация БД
```

## Возможности

- ✅ **Веб-интерфейс** для управления торговым ботом
- ✅ **Интеграция с Tinkoff Invest API**
- ✅ **База данных PostgreSQL**
- ✅ **Автоматические проверки здоровья**
- ✅ **Логирование и мониторинг**
- ✅ **Поддержка песочницы и продакшена**

## Безопасность

- 🔒 Используйте **песочницу** для тестирования
- 🔒 Не публикуйте токены в публичных репозиториях
- 🔒 Регулярно обновляйте пароли
- 🔒 Используйте HTTPS в продакшене

## Поддержка

При возникновении проблем:
1. Проверьте логи: `docker-compose logs -f`
2. Убедитесь, что все порты свободны
3. Проверьте переменные окружения в `.env`
4. Убедитесь, что токены Tinkoff API корректны 