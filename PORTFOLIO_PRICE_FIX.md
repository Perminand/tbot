# 🔧 Исправление отображения средней цены в портфеле

## Проблема
На вкладке "Портфель" некорректно отображается средняя цена - вместо реальной средней цены показывается то же значение, что и текущая цена.

## Причина
В файле `src/main/resources/static/js/app.js` в функции `displayPortfolio` на строке 686 средняя цена отображалась как `position.displayValue` вместо `position.averagePrice`.

## Исправления

### 1. Исправлен фронтенд (`app.js`)
```javascript
// БЫЛО:
<td>${position.displayValue || 'N/A'}</td>
<td>${position.displayValue || 'N/A'}</td>

// СТАЛО:
<td>${position.currentPriceDisplay || 'N/A'}</td>
<td>${position.averagePriceDisplay || 'N/A'}</td>
```

### 2. Добавлены функции форматирования (`app.js`)
```javascript
function formatPrice(price) {
    if (price === null || price === undefined) return null;
    if (typeof price === 'number') {
        return `₽${price.toFixed(2)}`;
    }
    if (typeof price === 'string') {
        const numPrice = parseFloat(price);
        if (!isNaN(numPrice)) {
            return `₽${numPrice.toFixed(2)}`;
        }
    }
    return null;
}

function formatYield(yield) {
    if (yield === null || yield === undefined) return null;
    if (typeof yield === 'number') {
        return `${yield.toFixed(2)}%`;
    }
    if (typeof yield === 'string') {
        const numYield = parseFloat(yield);
        if (!isNaN(numYield)) {
            return `${numYield.toFixed(2)}%`;
        }
    }
    return null;
}
```

### 3. Улучшен бэкенд (`PortfolioDto.java`)

#### Добавлены форматированные поля:
```java
// Добавляем форматированные поля для отображения
private String currentPriceDisplay;
private String averagePriceDisplay;
private String accumulatedCouponYieldDisplay;
private String yieldDisplay;
```

#### Добавлены методы форматирования:
```java
private static String formatPrice(BigDecimal price) {
    if (price == null || price.compareTo(BigDecimal.ZERO) == 0) {
        return "N/A";
    }
    return "₽" + price.setScale(2, BigDecimal.ROUND_HALF_UP);
}

private static String formatYield(BigDecimal yield) {
    if (yield == null || yield.compareTo(BigDecimal.ZERO) == 0) {
        return "N/A";
    }
    return yield.setScale(2, BigDecimal.ROUND_HALF_UP) + "%";
}
```

#### Улучшена обработка цен:
```java
// Упрощенная обработка currentPrice
BigDecimal currentPrice = extractPriceFromMoney(position.getCurrentPrice());
dto.setCurrentPrice(currentPrice);

// Упрощенная обработка averagePrice
BigDecimal averagePrice = extractPriceFromMoney(position.getAveragePositionPrice());
dto.setAveragePrice(averagePrice);

// Устанавливаем форматированные значения для отображения
dto.setCurrentPriceDisplay(formatPrice(dto.getCurrentPrice()));
dto.setAveragePriceDisplay(formatPrice(dto.getAveragePrice()));
```

#### Добавлен расчет доходности:
```java
// Рассчитываем доходность для всех инструментов
try {
    if (dto.getCurrentPrice() != null && dto.getAveragePrice() != null && 
        dto.getAveragePrice().compareTo(BigDecimal.ZERO) > 0) {
        BigDecimal yield = dto.getCurrentPrice()
            .subtract(dto.getAveragePrice())
            .divide(dto.getAveragePrice(), 4, BigDecimal.ROUND_HALF_UP)
            .multiply(BigDecimal.valueOf(100));
        dto.setYield(yield);
    } else {
        dto.setYield(BigDecimal.ZERO);
    }
} catch (Exception e) {
    dto.setYield(BigDecimal.ZERO);
}
```

### 4. Улучшено отображение тикеров и названий
```java
// Для разных типов инструментов разные префиксы
if (figi.startsWith("TCS")) {
    dto.setTicker("TCS");
    dto.setName("Тинькофф Банк");
} else if (figi.startsWith("BBG")) {
    // Для BBG кодов берем первые 4 символа после BBG
    if (figi.length() > 7) {
        dto.setTicker(figi.substring(4, 8));
    } else {
        dto.setTicker(figi.substring(0, Math.min(8, figi.length())));
    }
    dto.setName(getInstrumentTypeDisplayName(position.getInstrumentType()) + " " + dto.getTicker());
}
```

### 5. Добавлен отладочный контроллер
Создан `PortfolioTestController` для отладки проблем с портфелем:
```
GET /api/portfolio-test/debug?accountId=123456
```

## Результат
Теперь в портфеле корректно отображаются:
- ✅ **Текущая цена** - актуальная рыночная цена
- ✅ **Средняя цена** - средняя цена покупки
- ✅ **НКД** - накопленный купонный доход (для облигаций)
- ✅ **Доходность** - процентная доходность позиции
- ✅ **Тикеры** - более читаемые символы инструментов
- ✅ **Названия** - понятные названия инструментов

## Тестирование
1. Запустите приложение
2. Перейдите на вкладку "Портфель"
3. Проверьте, что средняя цена отличается от текущей цены
4. Для отладки используйте: `GET /api/portfolio-test/debug?accountId=YOUR_ACCOUNT_ID`

## Дополнительные улучшения
- Добавлено логирование для отладки проблем с парсингом цен
- Улучшена обработка ошибок при извлечении цен
- Добавлена поддержка различных форматов FIGI кодов
- Реализован расчет доходности для всех типов инструментов
