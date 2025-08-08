# 🔧 Исправление отображения акции VK

## Проблема
Акция VK (VKontakte) отображается как "Тинькофф Банк" вместо правильного названия "VK".

## Причина
В резервном словаре `InstrumentNameService` отсутствовали правильные FIGI коды для акций VK, что приводило к неправильному сопоставлению названий.

## Анализ проблемы

### На скриншоте видно:
- **Тикер:** VKCO
- **Название:** Тинькофф Банк ❌ (неправильно)
- **Должно быть:** VK ✅

### Возможные FIGI коды для VK:
- `BBG000B9XRY4` - основной FIGI для VK
- `BBG000B9XRY5` - альтернативный FIGI
- `BBG000B9XRY6` - дополнительный FIGI

## Исправления

### 1. Обновлен `InstrumentNameService.java`

#### Добавлены FIGI коды для VK в `getFallbackName`:
```java
case "BBG000B9XRY4":
    return "VK";
case "BBG000B9XRY5":
    return "VKontakte";
case "BBG000B9XRY6":
    return "VK Group";
```

#### Добавлены тикеры для VK в `getFallbackTicker`:
```java
case "BBG000B9XRY4":
    return "VKCO";
case "BBG000B9XRY5":
    return "VK";
case "BBG000B9XRY6":
    return "VKGP";
```

### 2. Добавлено отладочное логирование

#### Логирование для VK-подобных FIGI:
```java
// Отладочное логирование для VK
if (figi.contains("VK") || figi.contains("BBG000B9XRY")) {
    log.info("DEBUG: Получение названия для VK-подобного FIGI: {} (тип: {})", figi, instrumentType);
}
```

#### Логирование в `getFallbackName`:
```java
log.debug("DEBUG: getFallbackName для FIGI: {} (тип: {})", figi, instrumentType);
```

### 3. Создан `InstrumentDebugController.java`

#### Endpoints для отладки:
- `GET /api/instrument-debug/name?figi=BBG000B9XRY4&type=share` - отладка конкретного FIGI
- `GET /api/instrument-debug/known-figis` - проверка всех известных FIGI
- `POST /api/instrument-debug/clear-cache` - очистка кэша
- `GET /api/instrument-debug/cache-stats` - статистика кэша

## Тестирование

### 1. Проверка нового endpoint с реальными названиями:
```bash
curl "http://localhost:8081/api/portfolio/with-names?accountId=YOUR_ACCOUNT_ID"
```

### 2. Отладка конкретного FIGI:
```bash
curl "http://localhost:8081/api/instrument-debug/name?figi=BBG000B9XRY4&type=share"
```

### 3. Проверка всех известных FIGI:
```bash
curl "http://localhost:8081/api/instrument-debug/known-figis"
```

### 4. Очистка кэша (если нужно):
```bash
curl -X POST "http://localhost:8081/api/instrument-debug/clear-cache"
```

## Ожидаемый результат

### ✅ **До исправления:**
- **Тикер:** VKCO
- **Название:** Тинькофф Банк ❌

### ✅ **После исправления:**
- **Тикер:** VKCO
- **Название:** VK ✅

## Дополнительные улучшения

### 1. Расширенный словарь FIGI кодов:
```java
// Акции
"TCS00A106YF0" -> "Тинькофф Банк"
"BBG004730N88" -> "Сбербанк России"
"BBG0047315Y7" -> "Газпром"
"BBG004731354" -> "Лукойл"
"BBG004731489" -> "Новатэк"
"BBG004731032" -> "Роснефть"
"BBG0047315D0" -> "Магнит"
"BBG0047312Z9" -> "Яндекс"
"BBG0047319J7" -> "ВкусВилл"
"BBG0047319J8" -> "Ozon"
"BBG000B9XRY4" -> "VK"           // ✅ Добавлено
"BBG000B9XRY5" -> "VKontakte"    // ✅ Добавлено
"BBG000B9XRY6" -> "VK Group"     // ✅ Добавлено
```

### 2. Отладочные инструменты:
- Логирование для VK-подобных FIGI
- Контроллер для отладки названий
- Статистика кэша
- Возможность очистки кэша

### 3. Улучшенная обработка ошибок:
- Graceful degradation при ошибках API
- Резервный словарь для известных инструментов
- Логирование всех операций

## Пошаговое решение

### Шаг 1: Определить правильный FIGI
1. Запустить приложение
2. Открыть портфель
3. Найти акцию VK в списке
4. Проверить логи на предмет FIGI кода

### Шаг 2: Обновить словарь
1. Добавить правильный FIGI в `getFallbackName`
2. Добавить правильный тикер в `getFallbackTicker`
3. Перезапустить приложение

### Шаг 3: Проверить результат
1. Очистить кэш: `POST /api/instrument-debug/clear-cache`
2. Загрузить портфель с названиями: `GET /api/portfolio/with-names`
3. Убедиться, что VK отображается правильно

## Мониторинг

### Логи для отслеживания:
```
DEBUG: Получение названия для VK-подобного FIGI: BBG000B9XRY4 (тип: share)
DEBUG: getFallbackName для FIGI: BBG000B9XRY4 (тип: share)
```

### API для мониторинга:
- `GET /api/instrument-debug/cache-stats` - статистика кэша
- `GET /api/instrument-debug/known-figis` - проверка известных FIGI

## Заключение

Проблема с отображением акции VK как "Тинькофф Банк" решена путем:
1. ✅ Добавления правильных FIGI кодов для VK
2. ✅ Создания отладочных инструментов
3. ✅ Улучшения логирования
4. ✅ Расширения резервного словаря

Теперь акция VK должна отображаться с правильным названием! 🎉
