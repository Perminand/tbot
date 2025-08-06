# Руководство по расчетам НКД и доходности облигаций

## Обзор

Этот документ описывает, как рассчитываются **НКД (Накопленный купонный доход)** и **Доходность** для облигаций в торговом боте.

## Что такое НКД?

**НКД (Накопленный купонный доход)** - это часть купонного дохода, которая накопилась с момента последней выплаты купона до текущей даты.

### Формула расчета НКД:

```
НКД = (Купон × Дни с последней выплаты) / Дни между купонами
```

### Пример расчета:
- Купон: 50 рублей
- Дни с последней выплаты: 30
- Дни между купонами: 90
- НКД = (50 × 30) / 90 = 16.67 рублей

## Типы доходности

### 1. Текущая доходность (Current Yield)
Отношение годового купонного дохода к текущей цене облигации.

**Формула:**
```
Текущая доходность = (Годовой купонный доход / Текущая цена) × 100%
```

### 2. Доходность к погашению (YTM - Yield to Maturity)
Полная доходность с учетом изменения цены облигации до погашения.

**Формула:**
```
YTM = ((Номинал - Текущая цена + Купонный доход) / Текущая цена) / Лет до погашения × 100%
```

## API Endpoints

### 1. Расчет НКД
```
GET /api/bond-calculations/nkd/{figi}?currentPrice=100
```

**Параметры:**
- `figi` - FIGI облигации
- `currentPrice` - текущая цена (по умолчанию 100)

**Ответ:**
```json
{
  "figi": "BBG0013HGFT4",
  "currentPrice": 100,
  "nkd": 16.67,
  "message": "НКД рассчитан успешно"
}
```

### 2. Расчет текущей доходности
```
GET /api/bond-calculations/yield/{figi}?currentPrice=100
```

**Ответ:**
```json
{
  "figi": "BBG0013HGFT4",
  "currentPrice": 100,
  "yield": 8.5,
  "yieldPercent": "8.5%",
  "message": "Доходность рассчитана успешно"
}
```

### 3. Расчет YTM
```
GET /api/bond-calculations/ytm/{figi}?currentPrice=100&faceValue=1000
```

**Параметры:**
- `figi` - FIGI облигации
- `currentPrice` - текущая цена (по умолчанию 100)
- `faceValue` - номинал облигации (по умолчанию 1000)

**Ответ:**
```json
{
  "figi": "BBG0013HGFT4",
  "currentPrice": 100,
  "faceValue": 1000,
  "ytm": 12.3,
  "ytmPercent": "12.3%",
  "message": "YTM рассчитан успешно"
}
```

### 4. Полный анализ облигации
```
GET /api/bond-calculations/analysis/{figi}?currentPrice=100&faceValue=1000
```

**Ответ:**
```json
{
  "figi": "BBG0013HGFT4",
  "currentPrice": 100,
  "faceValue": 1000,
  "nkd": 16.67,
  "currentYield": 8.5,
  "ytm": 12.3,
  "currentYieldPercent": "8.5%",
  "ytmPercent": "12.3%",
  "message": "Анализ облигации выполнен успешно"
}
```

## Примеры использования

### JavaScript
```javascript
// Расчет НКД
const response = await fetch('/api/bond-calculations/nkd/BBG0013HGFT4?currentPrice=95.5');
const data = await response.json();
console.log(`НКД: ${data.nkd} рублей`);

// Расчет доходности
const yieldResponse = await fetch('/api/bond-calculations/yield/BBG0013HGFT4?currentPrice=95.5');
const yieldData = await yieldResponse.json();
console.log(`Доходность: ${yieldData.yieldPercent}`);
```

### Python
```python
import requests

# Расчет НКД
response = requests.get('http://localhost:8081/api/bond-calculations/nkd/BBG0013HGFT4', 
                       params={'currentPrice': 95.5})
data = response.json()
print(f"НКД: {data['nkd']} рублей")

# Полный анализ
analysis = requests.get('http://localhost:8081/api/bond-calculations/analysis/BBG0013HGFT4',
                       params={'currentPrice': 95.5, 'faceValue': 1000})
analysis_data = analysis.json()
print(f"НКД: {analysis_data['nkd']}")
print(f"Текущая доходность: {analysis_data['currentYieldPercent']}")
print(f"YTM: {analysis_data['ytmPercent']}")
```

### cURL
```bash
# Расчет НКД
curl "http://localhost:8081/api/bond-calculations/nkd/BBG0013HGFT4?currentPrice=95.5"

# Полный анализ
curl "http://localhost:8081/api/bond-calculations/analysis/BBG0013HGFT4?currentPrice=95.5&faceValue=1000"
```

## Интеграция в портфель

В интерфейсе портфеля НКД и доходность отображаются для облигаций:

- **НКД** - показывает накопленный купонный доход
- **Доходность** - показывает текущую доходность облигации

## Важные замечания

1. **НКД** рассчитывается только для облигаций
2. **Доходность** может быть рассчитана для любых инструментов с купонными выплатами
3. Расчеты основаны на данных из Tinkoff API
4. Для акций и других инструментов НКД и доходность равны 0
5. **Торговля** - бот может торговать любыми доступными средствами (без минимального порога)

## Ограничения

- Расчеты требуют доступа к данным о купонах облигации
- Некоторые облигации могут не иметь данных о купонах
- YTM рассчитывается по упрощенной формуле
- Реальные расчеты могут отличаться из-за рыночных условий

## Дальнейшее развитие

В будущих версиях планируется:
- Более точный расчет YTM
- Учет налогов при расчете доходности
- Кэширование расчетов для улучшения производительности
- Интеграция с другими источниками данных 