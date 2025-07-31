# Примеры торговых операций

## Рыночные ордера

### Покупка акций

```bash
# Покупка 10 лотов Apple (AAPL)
curl -X POST "http://localhost:8080/api/tinkoff/orders/market-buy" \
  -H "Content-Type: application/json" \
  -d "figi=BBG000B9XRY4&lots=10&accountId=your_account_id"
```

### Продажа акций

```bash
# Продажа 5 лотов Microsoft (MSFT)
curl -X POST "http://localhost:8080/api/tinkoff/orders/market-sell" \
  -H "Content-Type: application/json" \
  -d "figi=BBG000BPH459&lots=5&accountId=your_account_id"
```

## Лимитные ордера

### Покупка по лимиту

```bash
# Покупка 10 лотов Apple по цене 150 USD
curl -X POST "http://localhost:8080/api/tinkoff/orders/limit" \
  -H "Content-Type: application/json" \
  -d "figi=BBG000B9XRY4&lots=10&price=150.00&direction=ORDER_DIRECTION_BUY&accountId=your_account_id"
```

### Продажа по лимиту

```bash
# Продажа 5 лотов Microsoft по цене 300 USD
curl -X POST "http://localhost:8080/api/tinkoff/orders/limit" \
  -H "Content-Type: application/json" \
  -d "figi=BBG000BPH459&lots=5&price=300.00&direction=ORDER_DIRECTION_SELL&accountId=your_account_id"
```

## Управление ордерами

### Получение списка активных ордеров

```bash
curl -X GET "http://localhost:8080/api/tinkoff/orders?accountId=your_account_id" \
  -H "Content-Type: application/json"
```

### Получение статуса ордера

```bash
curl -X GET "http://localhost:8080/api/tinkoff/orders/order_1234567890_123?accountId=your_account_id" \
  -H "Content-Type: application/json"
```

### Отмена ордера

```bash
curl -X POST "http://localhost:8080/api/tinkoff/orders/cancel" \
  -H "Content-Type: application/json" \
  -d "orderId=order_1234567890_123&accountId=your_account_id"
```

## Примеры ответов

### Успешное размещение ордера

```json
{
  "orderId": "order_1234567890_123",
  "figi": "BBG000B9XRY4",
  "operation": "Buy",
  "status": "Fill",
  "requestedLots": "10",
  "executedLots": "10",
  "price": "155.00",
  "currency": "usd",
  "orderDate": "2024-01-15T10:30:00Z",
  "orderType": "Market",
  "message": "Order executed successfully",
  "commission": "1.55",
  "accountId": "your_account_id"
}
```

### Статус ордера

```json
{
  "orderId": "order_1234567890_123",
  "figi": "BBG000B9XRY4",
  "operation": "Buy",
  "status": "New",
  "requestedLots": "10",
  "executedLots": "0",
  "price": "150.00",
  "currency": "usd",
  "orderDate": "2024-01-15T10:30:00Z",
  "orderType": "Limit",
  "message": "Order placed",
  "commission": "0.00",
  "accountId": "your_account_id"
}
```

### Список активных ордеров

```json
{
  "orders": [
    {
      "orderId": "order_1234567890_123",
      "figi": "BBG000B9XRY4",
      "operation": "Buy",
      "status": "New",
      "requestedLots": "10",
      "executedLots": "0",
      "price": "150.00",
      "currency": "usd",
      "orderDate": "2024-01-15T10:30:00Z",
      "orderType": "Limit",
      "message": "Order placed",
      "commission": "0.00",
      "accountId": "your_account_id"
    },
    {
      "orderId": "order_1234567890_124",
      "figi": "BBG000BPH459",
      "operation": "Sell",
      "status": "New",
      "requestedLots": "5",
      "executedLots": "0",
      "price": "300.00",
      "currency": "usd",
      "orderDate": "2024-01-15T10:35:00Z",
      "orderType": "Limit",
      "message": "Order placed",
      "commission": "0.00",
      "accountId": "your_account_id"
    }
  ]
}
```

## Направления ордеров

- `ORDER_DIRECTION_BUY` - Покупка
- `ORDER_DIRECTION_SELL` - Продажа

## Типы ордеров

- `ORDER_TYPE_MARKET` - Рыночный ордер
- `ORDER_TYPE_LIMIT` - Лимитный ордер

## Статусы ордеров

- `New` - Новый ордер
- `PartiallyFill` - Частично исполнен
- `Fill` - Полностью исполнен
- `Cancelled` - Отменен
- `Replaced` - Заменен
- `PendingCancel` - Ожидает отмены
- `Rejected` - Отклонен
- `PendingReplace` - Ожидает замены
- `PendingNew` - Ожидает размещения

## Важные замечания

1. **Используйте песочницу** для тестирования торговых операций
2. **Проверяйте баланс** перед размещением ордеров
3. **Следите за комиссиями** при торговле
4. **Используйте правильные FIGI** для инструментов
5. **Обрабатывайте ошибки** API
6. **Сохраняйте ID ордеров** для последующего управления

## Безопасность

- Все торговые операции требуют валидный токен API
- Используйте HTTPS для всех запросов
- Не передавайте токен в URL или логах
- Регулярно обновляйте токен API

## Ограничения

- Минимальный размер лота: 1
- Максимальный размер лота: зависит от инструмента
- Лимиты на количество ордеров: зависят от аккаунта
- Время исполнения: зависит от рыночных условий 