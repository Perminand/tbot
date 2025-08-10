# Быстрый старт Tinkoff Trading Bot

## Предварительные требования

1. **Java 21** - [Скачать](https://adoptium.net/)
2. **Docker Desktop** - [Скачать](https://www.docker.com/products/docker-desktop/)
3. **Maven** - [Скачать](https://maven.apache.org/download.cgi/)

## Быстрый запуск

### 1. Запуск Docker Desktop

1. Откройте Docker Desktop
2. Дождитесь полной загрузки (значок Docker в трее должен быть зеленым)

### 2. Запуск базы данных

```bash
# Запуск PostgreSQL
docker-compose up -d

# Проверка статуса
docker-compose ps
```

### 3. Настройка токена API

Отредактируйте `src/main/resources/application.yml`:

```yaml
tinkoff:
  api:
    token: ваш_токен_здесь
    use-sandbox: true  # true для тестирования
```

### 4. Запуск приложения

```bash
# Компиляция и запуск
mvn spring-boot:run
```

Приложение будет доступно по адресу: http://localhost:8080

## Проверка работы

### Тест API

```bash
# Получение списка инструментов
curl http://localhost:8080/api/tinkoff/instruments

# Получение аккаунтов
curl http://localhost:8080/api/tinkoff/accounts
```

### Проверка базы данных

```bash
# Подключение к PostgreSQL
docker exec -it tbot_postgres psql -U postgres -d tbot_db

# Проверка таблиц
\dt
```

## Устранение проблем

### Docker не запускается

1. Убедитесь, что Docker Desktop установлен и запущен
2. Проверьте, что WSL2 включен (для Windows)
3. Перезапустите Docker Desktop

### Порт 5432 занят

```bash
# Проверка занятых портов
netstat -an | findstr 5432

# Остановка локального PostgreSQL (если запущен)
net stop postgresql-x64-15
```

### Ошибки подключения к API

1. Проверьте токен в `application.yml`
2. Убедитесь, что `use-sandbox: true` для тестирования
3. Проверьте интернет-соединение

## Полезные команды

```bash
# Остановка всех сервисов
docker-compose down

# Просмотр логов
docker-compose logs -f

# Перезапуск приложения
mvn spring-boot:run

# Очистка и пересборка
mvn clean compile
```

## Структура проекта

```
tbot/
├── docker-compose.yml          # PostgreSQL
├── docker-compose.pgadmin.yml  # pgAdmin (опционально)
├── init.sql                   # Инициализация БД
├── src/main/java/ru/perminov/
│   ├── config/               # Конфигурация
│   ├── controller/           # REST API
│   ├── model/               # Модели данных
│   ├── repository/          # Репозитории JPA
│   ├── service/             # Бизнес-логика
│   └── Main.java           # Главный класс
└── src/main/resources/
    └── application.yml      # Конфигурация приложения
```

## Документация

- [README.md](README.md) - Основная документация
- [API_EXAMPLES.md](API_EXAMPLES.md) - Примеры API
- [TRADING_EXAMPLES.md](TRADING_EXAMPLES.md) - Торговые операции
- [DOCKER_SETUP.md](DOCKER_SETUP.md) - Настройка Docker 