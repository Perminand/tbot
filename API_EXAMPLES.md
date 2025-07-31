# Примеры использования Tinkoff Invest API

## Основные эндпоинты API

### 1. Получение списка инструментов

```bash
curl -X GET "http://localhost:8080/api/tinkoff/instruments" \
  -H "Content-Type: application/json"
```

### 2. Получение информации об инструменте

```bash
curl -X GET "http://localhost:8080/api/tinkoff/instruments/BG000B9XRY4" \
  -H "Content-Type: application/json"
```

### 3. Получение портфеля

```bash
curl -X GET "http://localhost:8080/api/tinkoff/portfolio?accountId=your_account_id" \
  -H "Content-Type: application/json"
```

### 4. Получение списка аккаунтов

```bash
curl -X GET "http://localhost:8080/api/tinkoff/accounts" \
  -H "Content-Type: application/json"
```

### 5. Получение рыночных данных

```bash
curl -X GET "http://localhost:8080/api/tinkoff/market-data/BG000B9XRY4" \
  -H "Content-Type: application/json"
```

## Примеры ответов API

### Инструмент (Instrument)

```json
{
  "figi": "BBG000B9XRY4",
  "ticker": "AAPL",
  "isin": "US0378331005",
  "name": "Apple Inc.",
  "currency": "usd",
  "exchange": "MOEX",
  "sector": "Technology",
  "countryOfRisk": "US",
  "countryOfRiskName": "США",
  "instrumentType": "share",
  "instrumentKind": "share",
  "shareType": "common",
  "nominal": "1",
  "nominalCurrency": "usd",
  "tradingStatus": "normal_trading",
  "otcFlag": false,
  "buyAvailableFlag": true,
  "sellAvailableFlag": true,
  "minPriceIncrement": "0.01",
  "apiTradeAvailableFlag": true,
  "uid": "BBG000B9XRY4",
  "realExchange": "MOEX",
  "positionUid": "BBG000B9XRY4",
  "forIisFlag": false,
  "forQualInvestorFlag": false,
  "weekendFlag": false,
  "blockedTcaFlag": false,
  "first1minCandleDate": "2020-01-01T00:00:00Z",
  "first1dayCandleDate": "2020-01-01T00:00:00Z",
  "riskLevel": "low"
}
```

### Портфель (Portfolio)

```json
{
  "totalAmountShares": {
    "currency": "usd",
    "value": "1000.00"
  },
  "totalAmountBonds": {
    "currency": "usd",
    "value": "500.00"
  },
  "totalAmountEtf": {
    "currency": "usd",
    "value": "200.00"
  },
  "totalAmountCurrencies": {
    "currency": "usd",
    "value": "100.00"
  },
  "totalAmountFutures": {
    "currency": "usd",
    "value": "0.00"
  },
  "expectedYield": {
    "currency": "usd",
    "value": "50.00"
  },
  "positions": [
    {
      "figi": "BBG000B9XRY4",
      "ticker": "AAPL",
      "isin": "US0378331005",
      "instrumentType": "share",
      "balance": "10",
      "blocked": "0",
      "lots": "10",
      "averagePositionPrice": {
        "currency": "usd",
        "value": "150.00"
      },
      "averagePositionPriceNoNkd": {
        "currency": "usd",
        "value": "150.00"
      },
      "name": "Apple Inc.",
      "currency": "usd",
      "currentPrice": {
        "currency": "usd",
        "value": "155.00"
      },
      "averagePositionPriceFifo": {
        "currency": "usd",
        "value": "150.00"
      },
      "quantityLots": "10"
    }
  ]
}
```

### Рыночные данные (Market Data)

```json
{
  "figi": "BBG000B9XRY4",
  "ticker": "AAPL",
  "isin": "US0378331005",
  "instrumentType": "share",
  "currency": "usd",
  "name": "Apple Inc.",
  "exchange": "MOEX",
  "countryOfRisk": "US",
  "countryOfRiskName": "США",
  "tradingStatus": "normal_trading",
  "otcFlag": false,
  "buyAvailableFlag": true,
  "sellAvailableFlag": true,
  "minPriceIncrement": "0.01",
  "apiTradeAvailableFlag": true,
  "uid": "BBG000B9XRY4",
  "realExchange": "MOEX",
  "positionUid": "BBG000B9XRY4",
  "forIisFlag": false,
  "forQualInvestorFlag": false,
  "weekendFlag": false,
  "blockedTcaFlag": false,
  "first1minCandleDate": "2020-01-01T00:00:00Z",
  "first1dayCandleDate": "2020-01-01T00:00:00Z",
  "riskLevel": "low",
  "lastPrice": {
    "currency": "usd",
    "value": "155.00"
  },
  "closePrice": {
    "currency": "usd",
    "value": "154.50"
  },
  "limitUp": {
    "currency": "usd",
    "value": "170.00"
  },
  "limitDown": {
    "currency": "usd",
    "value": "140.00"
  }
}
```

## Популярные FIGI

### Акции

- **AAPL** (Apple Inc.): `BBG000B9XRY4`
- **MSFT** (Microsoft Corp.): `BBG000BPH459`
- **GOOGL** (Alphabet Inc.): `BBG000B9XRY4`
- **TSLA** (Tesla Inc.): `BBG000N9MNX3`

### Облигации

- **ОФЗ-26242**: `BBG00QPYJ5X0`
- **ОФЗ-26243**: `BBG00QPYJ5Y9`

### ETF

- **FXUS** (Тинькофф US Equities): `BBG00QPYJ5X0`
- **FXIT** (Тинькофф Technology): `BBG00QPYJ5Y9`

## Обработка ошибок

### 401 Unauthorized
```json
{
  "error": "Unauthorized",
  "message": "Invalid API token"
}
```

### 404 Not Found
```json
{
  "error": "Not Found",
  "message": "Instrument not found"
}
```

### 500 Internal Server Error
```json
{
  "error": "Internal Server Error",
  "message": "API service unavailable"
}
```

## Рекомендации по использованию

1. **Используйте песочницу** для тестирования
2. **Кэшируйте данные** инструментов
3. **Обрабатывайте ошибки** API
4. **Используйте правильные FIGI** для инструментов
5. **Следите за лимитами** API запросов

## Дополнительные ресурсы

- [Официальная документация Tinkoff Invest API](https://russianinvestments.github.io/investAPI/swagger-ui/)
- [Swagger UI](https://russianinvestments.github.io/investAPI/swagger-ui/)
- [GitHub репозиторий](https://github.com/Tinkoff/investAPI) 