# Улучшение маржинальной торговли

## Проблема
Блокировка покупок при отрицательных средствах мешала работе с брокерским плечом согласно маржинальным настройкам.

## Решение

### 1. Добавлены настройки маржинальной торговли
В `application.yml` добавлены новые параметры:
```yaml
margin-trading:
  allow-negative-cash: true    # Разрешить покупки при отрицательных средствах (используя плечо)
  min-buying-power-ratio: 0.1  # Минимальное отношение покупательной способности к стоимости покупки
```

### 2. Изменена логика блокировки покупок
В `PortfolioManagementService.executeTradingStrategy()`:

**Было:**
```java
if (availableCash.compareTo(BigDecimal.ZERO) < 0) {
    log.warn("Реальные средства отрицательные ({}), блокируем покупки", availableCash);
    return;
}
```

**Стало:**
```java
boolean allowNegativeCash = tradingSettingsService.getBoolean("margin.allow.negative.cash", false);
if (availableCash.compareTo(BigDecimal.ZERO) < 0 && !allowNegativeCash) {
    log.warn("Реальные средства отрицательные ({}), блокируем покупки (маржинальная торговля отключена)", availableCash);
    return;
} else if (availableCash.compareTo(BigDecimal.ZERO) < 0 && allowNegativeCash) {
    log.info("Реальные средства отрицательные ({}), но маржинальная торговля разрешена. Используем плечо.", availableCash);
}
```

### 3. Добавлена проверка покупательной способности
Для маржинальных операций добавлена проверка минимального отношения покупательной способности:
```java
if (allowNegativeCash && availableCash.compareTo(BigDecimal.ZERO) < 0) {
    double minBuyingPowerRatio = tradingSettingsService.getDouble("margin.min.buying.power.ratio", 0.1);
    BigDecimal minRequiredBuyingPower = trend.getCurrentPrice().multiply(BigDecimal.valueOf(minBuyingPowerRatio));
    
    if (buyingPower.compareTo(minRequiredBuyingPower) < 0) {
        log.warn("Недостаточная покупательная способность для маржинальной операции");
        return;
    }
}
```

### 4. Улучшено логирование
- Логи теперь показывают тип операции: "маржинальная покупка" vs "покупка"
- Отображается информация о доступных средствах
- Добавлены предупреждения о недостаточной покупательной способности

## Результат

Теперь бот может:
1. ✅ Работать с брокерским плечом при отрицательных средствах
2. ✅ Проверять достаточность покупательной способности для маржинальных операций
3. ✅ Логировать маржинальные операции с соответствующими метками
4. ✅ Сохранять безопасность через настройки и проверки

## Настройки

Для включения маржинальной торговли установите в `application.yml`:
```yaml
margin-trading:
  allow-negative-cash: true
  min-buying-power-ratio: 0.1  # 10% от стоимости покупки
```

Для отключения:
```yaml
margin-trading:
  allow-negative-cash: false
```
