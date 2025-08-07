# Настройка API ключей для Tinkoff Trading Bot

## Обзор

Проект теперь поддерживает **два отдельных API ключа**:
- **Песочница (Sandbox)** - для тестирования без реальных денег
- **Реальная торговля (Production)** - для работы с реальными деньгами

## Конфигурация

### 1. Настройка в application.yml

```yaml
tinkoff:
  api:
    # Токен для песочницы (тестовый)
    sandbox-token: ${TINKOFF_SANDBOX_TOKEN:t.CRkcaE_K19MSklxNKVqJ2rcohwl5CGpwN6Hxp58mdase_kEotm5eXbt2PAlURvHZ6UkhGEPueod2VM7Op4jChg}
    # Токен для реальной торговли (продакшн)
    production-token: ${TINKOFF_PRODUCTION_TOKEN:}
    # Режим по умолчанию
    default-mode: sandbox  # sandbox или production
```

### 2. Переменные окружения

Для безопасности рекомендуется использовать переменные окружения:

```bash
# Windows PowerShell
$env:TINKOFF_SANDBOX_TOKEN="ваш_токен_песочницы"
$env:TINKOFF_PRODUCTION_TOKEN="ваш_токен_реальной_торговли"

# Linux/Mac
export TINKOFF_SANDBOX_TOKEN="ваш_токен_песочницы"
export TINKOFF_PRODUCTION_TOKEN="ваш_токен_реальной_торговли"
```

## Получение API ключей

### Песочница (Sandbox)
1. Зайдите на https://www.tinkoff.ru/invest/
2. Войдите в личный кабинет
3. Перейдите в раздел "API"
4. Создайте токен для **песочницы**
5. Скопируйте токен (начинается с `t.`)

### Реальная торговля (Production)
1. Зайдите на https://www.tinkoff.ru/invest/
2. Войдите в личный кабинет
3. Перейдите в раздел "API"
4. Создайте токен для **реальной торговли**
5. Скопируйте токен (начинается с `t.`)

⚠️ **ВАЖНО**: Токен реальной торговли дает доступ к вашим реальным деньгам!

## Переключение режимов

### Через веб-интерфейс
1. Откройте http://localhost:8081/
2. В разделе "Dashboard" найдите переключатель режима
3. Выберите нужный режим
4. Подтвердите переключение

### Через API
```bash
# Переключение в песочницу
curl -X POST "http://localhost:8081/api/trading-mode/switch-confirmed" \
  -d "mode=sandbox" \
  -H "Content-Type: application/x-www-form-urlencoded"

# Переключение в реальную торговлю
curl -X POST "http://localhost:8081/api/trading-mode/switch-confirmed" \
  -d "mode=production" \
  -H "Content-Type: application/x-www-form-urlencoded"
```

## Проверка статуса

### Через API
```bash
curl "http://localhost:8081/api/trading-mode/status"
```

### Через веб-интерфейс
Статус отображается в правом верхнем углу веб-интерфейса.

## Безопасность

### Рекомендации
1. **Никогда не коммитьте токены в Git**
2. Используйте переменные окружения
3. Регулярно обновляйте токены
4. Используйте песочницу для тестирования
5. Внимательно проверяйте режим перед реальной торговлей

### Проверка режима
- **Песочница**: желтый значок "Песочница"
- **Реальная торговля**: красный значок "Реальная торговля" с предупреждением

## Архитектура

### InvestApiManager
Новый сервис `InvestApiManager` управляет переключением между режимами:

```java
@Service
public class InvestApiManager {
    // Автоматическое переключение InvestApi при смене режима
    public synchronized void switchToMode(String mode) {
        // Создает новый InvestApi для указанного режима
    }
    
    // Получение текущего InvestApi
    public synchronized InvestApi getCurrentInvestApi() {
        // Возвращает InvestApi для текущего режима
    }
}
```

### Все сервисы используют InvestApiManager
- `TradingService` - торговые операции
- `AccountService` - управление аккаунтами
- `PortfolioService` - работа с портфелем
- `InstrumentService` - работа с инструментами
- `BondCalculationService` - расчеты по облигациям

## Устранение неполадок

### Ошибка "Токен не настроен"
Убедитесь, что токен для нужного режима настроен в `application.yml` или переменных окружения.

### Ошибка переключения режима
1. Проверьте, что токен для целевого режима настроен
2. Проверьте логи приложения
3. Убедитесь, что база данных доступна

### Проверка подключения
```bash
curl "http://localhost:8081/api/test/connection"
```
