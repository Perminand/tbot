# Руководство по активации шортов для продакшена

## Что было исправлено

### 1. Логика принятия решений (`determineRecommendedAction`)
- **BEARISH тренд + RSI > 70:** Теперь возвращает `"SELL"` вместо `"HOLD"` для шортов
- **BEARISH тренд + RSI > 50:** Теперь возвращает `"SELL"` для шортов
- **SIDEWAYS тренд + RSI > 65:** Теперь возвращает `"SELL"` для шортов

### 2. Расчет приоритета (`calculateTradingScore`)
- Увеличен score для BEARISH тренда с 5 до 25
- Увеличен score для перекупленности (RSI > 70) с 20 до 30
- Добавлен бонус 20 очков для BEARISH тренда + SMA20 < SMA50

### 3. Настройки маржинальной торговли
Создан SQL-скрипт `enable_margin_trading.sql` для включения:
- `margin_enabled = true`
- `margin_allow_short = true`
- `margin_max_utilization_pct = 0.40`
- `margin_max_short_pct = 0.15`
- `margin_safety_pct = 0.70`

## Как активировать шорты

### 1. Выполните SQL-скрипт
```bash
psql -d your_database -f enable_margin_trading.sql
```

### 2. Или через API
```bash
curl -X POST "http://your-server/api/margin/settings" \
  -d "enabled=true&allowShort=true&maxUtilizationPct=0.40&maxShortPct=0.15"
```

### 3. Проверьте настройки
```bash
curl "http://your-server/api/margin/status"
```

## Условия для открытия шортов

### Технические условия:
1. **BEARISH тренд** + RSI > 70 (сильный сигнал)
2. **BEARISH тренд** + RSI > 50 (умеренный сигнал)
3. **SIDEWAYS тренд** + RSI > 65 (перекупленность)

### Системные условия:
1. `margin_enabled = true`
2. `margin_allow_short = true`
3. `marginService.canOpenShort(figi) = true` (флаг инструмента)
4. `marginService.isMarginOperationalForAccount(accountId) = true` (не песочница)
5. Достаточно маржи для шорта

## Логика работы

### Генерация SELL сигналов:
```java
// BEARISH тренд + перекупленность = шорт
if (trend == BEARISH && rsi > 70) {
    return "SELL"; // Шорт или закрытие позиции
}

// BEARISH тренд + умеренная перекупленность = шорт
if (trend == BEARISH && rsi > 50) {
    return "SELL"; // Шорт
}
```

### Открытие шорта:
```java
if (marginService.canOpenShort(figi) && marginService.isMarginOperationalForAccount(accountId)) {
    // Проверка маржи
    // Расчет размера позиции
    // Размещение ордера SELL
    // Установка SL/TP
}
```

## Мониторинг

### Логи для отслеживания:
- `"Открытие шорта по {}: {} лотов"`
- `"Шорт открыт"`
- `"Установлены SL/TP (шорт)"`
- `"Недостаточно маржи для шорта"`

### API для проверки:
- `GET /api/margin/status` - статус маржинальной торговли
- `GET /api/margin/attributes?accountId=...` - маржинальные атрибуты
- `GET /api/margin/buying-power?accountId=...` - покупательная способность

## Безопасность

### Риск-лимиты:
- Максимальная доля шорта: 15% от портфеля
- Безопасная доля маржи: 70%
- Автоматическая установка SL/TP при открытии шорта

### Проверки перед шортом:
1. Достаточно ли маржи
2. Разрешен ли шорт для инструмента
3. Не превышен ли лимит позиций
4. Достаточно ли score для действия

## Примечания

- **В песочнице шорты не работают** - `isMarginOperationalForAccount()` возвращает `false`
- **Нужен продакшн аккаунт** с доступной маржей
- **Не все инструменты поддерживают шорты** - проверяется флаг `shortEnabledFlag`
- **Шорты автоматически получают SL/TP** по дефолтным настройкам
