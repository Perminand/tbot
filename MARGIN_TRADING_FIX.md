# Исправление проблемы с маржинальной торговлей

## Проблема
При отсутствии собственных средств в маржинальном аккаунте бот работал только с шортами, игнорируя недостаток средств для покупок.

## Причина
1. **Логика принятия решений**: В методе `determineRecommendedAction` при медвежьем тренде (BEARISH) бот всегда возвращал "SELL" независимо от наличия позиции
2. **Отсутствие проверки средств**: Не было проверки отрицательных средств перед попытками покупки
3. **Неправильная обработка отрицательного кэша**: В `MarginService` отрицательный кэш преобразовывался в 0, что скрывало проблему
4. **Игнорирование шортов**: Бот не анализировал короткие позиции для их закрытия

## Исправления

### 1. Исправлена логика принятия решений
**Файл**: `src/main/java/ru/perminov/service/PortfolioManagementService.java`

**Было**:
```java
} else if (trendAnalysis.getTrend() == MarketAnalysisService.TrendType.BEARISH) {
    if (rsi.compareTo(BigDecimal.valueOf(70)) > 0) {
        return "SELL"; // Сильная продажа при перекупленности (шорт или закрытие позиции)
    } else if (rsi.compareTo(BigDecimal.valueOf(50)) > 0) {
        return hasPosition ? "SELL" : "SELL"; // Умеренная продажа при нисходящем тренде (шорт)
    }
}
```

**Стало**:
```java
} else if (trendAnalysis.getTrend() == MarketAnalysisService.TrendType.BEARISH) {
    if (rsi.compareTo(BigDecimal.valueOf(70)) > 0) {
        return hasPosition ? "SELL" : "HOLD"; // Сильная продажа при перекупленности (только если есть позиция)
    } else if (rsi.compareTo(BigDecimal.valueOf(50)) > 0) {
        return hasPosition ? "SELL" : "HOLD"; // Умеренная продажа при нисходящем тренде (только если есть позиция)
    }
}
```

### 2. Добавлена проверка отрицательных средств
**Файл**: `src/main/java/ru/perminov/service/PortfolioManagementService.java`

Добавлена проверка в начале логики BUY:
```java
// Дополнительная проверка: если реальные средства отрицательные, блокируем покупки
if (availableCash.compareTo(BigDecimal.ZERO) < 0) {
    log.warn("Реальные средства отрицательные ({}), блокируем покупки", availableCash);
    botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT, 
        "Блокировка покупок", String.format("Отрицательные средства: %.2f", availableCash));
    return;
}
```

### 3. Исправлена логика расчета покупательной способности
**Файл**: `src/main/java/ru/perminov/service/MarginService.java`

**Было**:
```java
BigDecimal nonNegativeCash = cash.max(BigDecimal.ZERO);
if (!isMarginEnabled()) return nonNegativeCash;
```

**Стало**:
```java
// Если кэш отрицательный, возвращаем 0 - покупки невозможны
if (cash.compareTo(BigDecimal.ZERO) < 0) {
    log.warn("Отрицательные средства: {}, покупательная способность = 0", cash);
    return BigDecimal.ZERO;
}

if (!isMarginEnabled()) return cash;
```

### 4. Добавлена поддержка закрытия шортов ⭐ НОВОЕ
**Файл**: `src/main/java/ru/perminov/service/PortfolioManagementService.java`

**Проблема**: Бот игнорировал короткие позиции (quantity < 0) при анализе

**Исправления**:

1. **Анализ всех позиций** (включая шорты):
```java
// Было: анализируем только позиции с количеством > 0
if (position.getQuantity().compareTo(BigDecimal.ZERO) > 0) {

// Стало: анализируем позиции с количеством != 0 (включая шорты)
if (position.getQuantity().compareTo(BigDecimal.ZERO) != 0) {
```

2. **Правильная логика действий для шортов**:
```java
// Определяем, является ли позиция шортом
boolean isShortPosition = position.getQuantity().compareTo(BigDecimal.ZERO) < 0;

// Для шортов логика обратная: если рекомендуют SELL, то нужно закрыть шорт (BUY)
String actionForPosition = isShortPosition ? 
    ("SELL".equals(opportunity.getRecommendedAction()) ? "BUY" : opportunity.getRecommendedAction()) :
    opportunity.getRecommendedAction();
```

3. **Закрытие шортов через BUY**:
```java
} else if ("BUY".equals(action)) {
    // Проверяем, есть ли шорт-позиция для закрытия
    if (positionValue != null && positionValue.compareTo(BigDecimal.ZERO) < 0) {
        // Закрываем шорт покупкой
        orderService.placeMarketOrder(figi, lots, OrderDirection.ORDER_DIRECTION_BUY, accountId);
    }
}
```

## Результат
Теперь бот:
1. **Не будет пытаться покупать** при отрицательных средствах
2. **Не будет открывать шорты** при медвежьем тренде, если нет позиции для продажи
3. **Будет корректно логировать** все попытки торговли и причины их блокировки
4. **Будет работать только с продажами существующих позиций** при недостатке средств
5. **Сможет закрывать шорты** через покупку обратно ⭐ НОВОЕ
6. **Будет анализировать все позиции** (длинные и короткие) ⭐ НОВОЕ

## Рекомендации
1. Пополните счет для восстановления положительного баланса
2. Проверьте настройки маржинальной торговли в базе данных
3. Мониторьте логи для отслеживания торговых операций
4. Теперь бот сможет автоматически закрывать шорты при соответствующих сигналах
