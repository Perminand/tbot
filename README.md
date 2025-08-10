# Tinkoff Trading Bot

Торговый бот для работы с Tinkoff Invest API. Проект построен на Spring Boot с использованием PostgreSQL для хранения данных.

## Возможности

- Интеграция с Tinkoff Invest API
- Получение информации об инструментах
- Работа с портфелем
- Получение рыночных данных
- Сохранение данных в PostgreSQL
- REST API для взаимодействия
- **Современный веб-интерфейс** для управления
- **Торговые операции** через веб-интерфейс
- **Адаптивный дизайн** для всех устройств

## Технологии

- **Java 21**
- **Spring Boot 3.2.0**
- **Spring Data JPA**
- **PostgreSQL**
- **WebFlux** (для асинхронных HTTP запросов)
- **Lombok**

## Настройка

### 1. База данных

#### Вариант A: Docker Compose (рекомендуется)

Запустите PostgreSQL с помощью Docker Compose:

```bash
docker-compose up -d
```

Подробная документация: [DOCKER_SETUP.md](DOCKER_SETUP.md)

#### Вариант B: Локальная установка

Создайте базу данных PostgreSQL:

```sql
CREATE DATABASE tbot_db;
```

### 2. Конфигурация

Отредактируйте `src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/tbot_db
    username: your_username
    password: your_password

tinkoff:
  api:
    token: your_tinkoff_api_token
    use-sandbox: true  # true для песочницы, false для боевого режима
```

### 3. Получение токена Tinkoff API

1. Зарегистрируйтесь на [Tinkoff Invest](https://www.tinkoff.ru/invest/)
2. Получите токен в личном кабинете
3. Для тестирования используйте песочницу (sandbox)

## Запуск

```bash
mvn spring-boot:run
```

Приложение будет доступно по адресу: http://localhost:8080

### Веб-интерфейс

Современный веб-интерфейс доступен по адресу: http://localhost:8080

**Возможности веб-интерфейса:**
- 📊 **Dashboard** - Общая статистика портфеля
- 📋 **Инструменты** - Список доступных инструментов
- 💼 **Портфель** - Текущие позиции и доходность
- 📈 **Ордера** - Управление торговыми ордерами
- 🎯 **Торговля** - Размещение новых ордеров
- ⚙️ **Настройки** - Конфигурация системы

Подробная документация: [WEB_INTERFACE.md](WEB_INTERFACE.md)

## API Endpoints

### Инструменты

- `GET /api/tinkoff/instruments` - Получить список всех инструментов
- `GET /api/tinkoff/instruments/{figi}` - Получить информацию об инструменте по FIGI

### Портфель

- `GET /api/tinkoff/portfolio?accountId={accountId}` - Получить портфель по ID аккаунта
- `GET /api/tinkoff/accounts` - Получить список аккаунтов

### Рыночные данные

- `GET /api/tinkoff/market-data/{figi}` - Получить рыночные данные по FIGI

### Торговые операции

- `POST /api/tinkoff/orders/market-buy` - Разместить рыночный ордер на покупку
- `POST /api/tinkoff/orders/market-sell` - Разместить рыночный ордер на продажу
- `POST /api/tinkoff/orders/limit` - Разместить лимитный ордер
- `POST /api/tinkoff/orders/cancel` - Отменить ордер
- `GET /api/tinkoff/orders/{orderId}` - Получить статус ордера
- `GET /api/tinkoff/orders` - Получить список активных ордеров

## Структура проекта

```
src/main/java/ru/perminov/
├── config/           # Конфигурация
│   └── TinkoffApiConfig.java
├── controller/       # REST контроллеры
│   └── TinkoffController.java
├── model/           # Модели данных
│   ├── Instrument.java
│   ├── Order.java
│   └── Position.java
├── repository/      # Репозитории JPA
│   ├── InstrumentRepository.java
│   ├── OrderRepository.java
│   └── PositionRepository.java
├── service/         # Бизнес-логика
│   ├── TinkoffApiService.java
│   └── TradingService.java
└── Main.java        # Главный класс приложения
```

## Tinkoff Invest API

Проект использует [Tinkoff Invest API](https://russianinvestments.github.io/investAPI/swagger-ui/) для:

- Получения информации об инструментах
- Работы с портфелем
- Получения рыночных данных
- Размещения торговых ордеров

### Основные возможности API:

1. **Инструменты** - получение списка доступных инструментов
2. **Портфель** - информация о позициях и активах
3. **Рыночные данные** - котировки и свечи
4. **Торговля** - размещение и отмена ордеров
5. **Песочница** - тестирование в безопасной среде

## Разработка

### Добавление новых эндпоинтов

1. Создайте метод в `TinkoffApiService`
2. Добавьте соответствующий эндпоинт в `TinkoffController`
3. При необходимости создайте модели данных

### Работа с базой данных

Проект использует JPA для работы с PostgreSQL. Модели автоматически создают таблицы при запуске.

## Безопасность

- Токен API хранится в переменных окружения
- Используется HTTPS для всех запросов к API
- Валидация входных данных

## Лицензия

MIT License 