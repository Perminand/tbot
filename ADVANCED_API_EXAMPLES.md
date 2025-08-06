# 📚 Примеры использования продвинутого API

## 🔧 Продвинутый технический анализ

### Получение всех технических индикаторов
```bash
curl -X GET "http://localhost:8081/api/trading-bot/indicators/BBG000B9XRY4" \
  -H "Content-Type: application/json"
```

**Ответ:**
```json
{
  "figi": "BBG000B9XRY4",
  "sma20": 150.25,
  "sma50": 148.75,
  "rsi": 65.5,
  "macd": {
    "macdLine": 2.15,
    "signalLine": 1.85,
    "histogram": 0.30
  },
  "bollingerBands": {
    "upperBand": 155.50,
    "middleBand": 150.25,
    "lowerBand": 145.00
  },
  "stochastic": {
    "kPercent": 75.5,
    "dPercent": 72.3
  },
  "volume": {
    "currentVolume": 1500000,
    "volumeRatio": 1.25,
    "volumeSignal": "HIGH"
  },
  "supportResistance": {
    "support": 145.00,
    "resistance": 155.50
  }
}
```

## 🎯 Продвинутые торговые сигналы

### Комплексный анализ торгового сигнала
```bash
curl -X GET "http://localhost:8081/api/trading-bot/advanced-signal/BBG000B9XRY4" \
  -H "Content-Type: application/json"
```

**Ответ:**
```json
{
  "figi": "BBG000B9XRY4",
  "action": "BUY",
  "strength": 75.5,
  "signals": {
    "trend": "BULLISH",
    "sma": "BULLISH",
    "rsi": "NEUTRAL",
    "macd": "BULLISH",
    "bb": "NEUTRAL",
    "stoch": "NEUTRAL",
    "volume": "HIGH"
  },
  "riskLevel": "Нормальный уровень риска",
  "riskAction": "Можно продолжать торговлю"
}
```

### Анализ следования за трендом
```bash
curl -X GET "http://localhost:8081/api/trading-bot/trend-following/BBG000B9XRY4" \
  -H "Content-Type: application/json"
```

**Ответ:**
```json
{
  "figi": "BBG000B9XRY4",
  "signal": "BUY",
  "reason": "Сильный восходящий тренд с высоким объемом",
  "dailyTrend": "BULLISH",
  "weeklyTrend": "BULLISH"
}
```

### Анализ уровней поддержки/сопротивления
```bash
curl -X GET "http://localhost:8081/api/trading-bot/support-resistance/BBG000B9XRY4" \
  -H "Content-Type: application/json"
```

**Ответ:**
```json
{
  "figi": "BBG000B9XRY4",
  "signal": "HOLD",
  "reason": "Цена в нейтральной зоне",
  "support": 145.00,
  "resistance": 155.50,
  "currentPrice": 150.25
}
```

## 🛡️ Риск-менеджмент

### Получение рекомендаций по риск-менеджменту
```bash
curl -X GET "http://localhost:8081/api/trading-bot/risk-recommendation/1635493e-e47e-49bb-84ca-5feca7c718ad" \
  -H "Content-Type: application/json"
```

**Ответ:**
```json
{
  "accountId": "1635493e-e47e-49bb-84ca-5feca7c718ad",
  "recommendation": "Нормальный уровень риска",
  "action": "Можно продолжать торговлю",
  "drawdown": 0.05,
  "dailyLoss": 0.01
}
```

### Проверка рисков для позиции
```bash
curl -X POST "http://localhost:8081/api/trading-bot/risk-check/1635493e-e47e-49bb-84ca-5feca7c718ad" \
  -H "Content-Type: application/json" \
  -d "figi=BBG000B9XRY4&price=150.25&lots=10"
```

**Ответ:**
```json
{
  "accountId": "1635493e-e47e-49bb-84ca-5feca7c718ad",
  "figi": "BBG000B9XRY4",
  "approved": true,
  "reason": "Риски в допустимых пределах"
}
```

### Расчет стоп-лосса и тейк-профита
```bash
curl -X GET "http://localhost:8081/api/trading-bot/stop-loss-take-profit/BBG000B9XRY4?entryPrice=150.25&direction=BUY" \
  -H "Content-Type: application/json"
```

**Ответ:**
```json
{
  "figi": "BBG000B9XRY4",
  "entryPrice": 150.25,
  "direction": "BUY",
  "stopLoss": 142.74,
  "takeProfit": 172.79
}
```

## 📊 Динамическая ребалансировка

### Выполнение динамической ребалансировки
```bash
curl -X POST "http://localhost:8081/api/trading-bot/dynamic-rebalancing/1635493e-e47e-49bb-84ca-5feca7c718ad" \
  -H "Content-Type: application/json"
```

**Ответ:**
```json
{
  "accountId": "1635493e-e47e-49bb-84ca-5feca7c718ad",
  "marketCondition": "BULL_MARKET",
  "targetAllocation": {
    "shares": 0.70,
    "bonds": 0.20,
    "etf": 0.10
  },
  "actions": [
    {
      "type": "BUY_SHARES",
      "amount": 50000.00,
      "description": "Увеличение доли акций"
    },
    {
      "type": "SELL_BONDS",
      "amount": 20000.00,
      "description": "Уменьшение доли облигаций"
    }
  ],
  "totalValue": 1000000.00
}
```

### Анализ диверсификации портфеля
```bash
curl -X GET "http://localhost:8081/api/trading-bot/diversification/1635493e-e47e-49bb-84ca-5feca7c718ad" \
  -H "Content-Type: application/json"
```

**Ответ:**
```json
{
  "accountId": "1635493e-e47e-49bb-84ca-5feca7c718ad",
  "sectorAllocation": {
    "Technology": 35.5,
    "Healthcare": 20.3,
    "Financial": 15.2,
    "Consumer": 12.8,
    "Energy": 8.7,
    "Industrial": 7.5
  },
  "countryAllocation": {
    "USA": 65.2,
    "Europe": 20.1,
    "Asia": 10.3,
    "Emerging": 4.4
  },
  "correlationRisk": 0.25,
  "diversificationScore": "GOOD"
}
```

## 🔄 Интеграция с существующими API

### Комбинированный анализ с базовыми возможностями
```bash
# 1. Получение анализа тренда
curl -X GET "http://localhost:8081/api/trading-bot/analysis/BBG000B9XRY4"

# 2. Получение продвинутого сигнала
curl -X GET "http://localhost:8081/api/trading-bot/advanced-signal/BBG000B9XRY4"

# 3. Проверка рисков
curl -X POST "http://localhost:8081/api/trading-bot/risk-check/1635493e-e47e-49bb-84ca-5feca7c718ad" \
  -d "figi=BBG000B9XRY4&price=150.25&lots=10"

# 4. Выполнение торговой стратегии (если все проверки пройдены)
curl -X POST "http://localhost:8081/api/trading-bot/strategy/1635493e-e47e-49bb-84ca-5feca7c718ad/BBG000B9XRY4"
```

## 📈 Примеры использования в JavaScript

### Получение комплексного анализа
```javascript
async function getAdvancedAnalysis(figi) {
  try {
    const response = await fetch(`/api/trading-bot/advanced-signal/${figi}`);
    const data = await response.json();
    
    console.log('Торговый сигнал:', data.action);
    console.log('Сила сигнала:', data.strength);
    console.log('Уровень риска:', data.riskLevel);
    
    return data;
  } catch (error) {
    console.error('Ошибка при получении анализа:', error);
  }
}

// Использование
getAdvancedAnalysis('BBG000B9XRY4');
```

### Мониторинг рисков
```javascript
async function monitorRisks(accountId) {
  try {
    const response = await fetch(`/api/trading-bot/risk-recommendation/${accountId}`);
    const data = await response.json();
    
    if (data.recommendation.includes('Высокий уровень риска')) {
      console.warn('⚠️ Внимание! Высокий уровень риска:', data.action);
    }
    
    return data;
  } catch (error) {
    console.error('Ошибка при мониторинге рисков:', error);
  }
}

// Использование
monitorRisks('1635493e-e47e-49bb-84ca-5feca7c718ad');
```

### Автоматическая ребалансировка
```javascript
async function performRebalancing(accountId) {
  try {
    const response = await fetch(`/api/trading-bot/dynamic-rebalancing/${accountId}`, {
      method: 'POST'
    });
    const data = await response.json();
    
    console.log('Рыночные условия:', data.marketCondition);
    console.log('Целевые доли:', data.targetAllocation);
    console.log('Выполненные действия:', data.actions);
    
    return data;
  } catch (error) {
    console.error('Ошибка при ребалансировке:', error);
  }
}

// Использование
performRebalancing('1635493e-e47e-49bb-84ca-5feca7c718ad');
```

## 🐍 Примеры использования в Python

### Анализ диверсификации
```python
import requests

def analyze_diversification(account_id):
    url = f"http://localhost:8081/api/trading-bot/diversification/{account_id}"
    
    try:
        response = requests.get(url)
        data = response.json()
        
        print(f"Оценка диверсификации: {data['diversificationScore']}")
        print(f"Корреляционный риск: {data['correlationRisk']}")
        
        print("\nРаспределение по секторам:")
        for sector, percentage in data['sectorAllocation'].items():
            print(f"  {sector}: {percentage}%")
            
        return data
    except Exception as e:
        print(f"Ошибка: {e}")

# Использование
analyze_diversification("1635493e-e47e-49bb-84ca-5feca7c718ad")
```

### Проверка стоп-лосса и тейк-профита
```python
def calculate_stop_loss_take_profit(figi, entry_price, direction):
    url = f"http://localhost:8081/api/trading-bot/stop-loss-take-profit/{figi}"
    params = {
        "entryPrice": entry_price,
        "direction": direction
    }
    
    try:
        response = requests.get(url, params=params)
        data = response.json()
        
        print(f"Стоп-лосс: {data['stopLoss']}")
        print(f"Тейк-профит: {data['takeProfit']}")
        
        return data
    except Exception as e:
        print(f"Ошибка: {e}")

# Использование
calculate_stop_loss_take_profit("BBG000B9XRY4", 150.25, "BUY")
```

## 📊 Мониторинг и алерты

### Создание системы мониторинга
```javascript
class TradingBotMonitor {
  constructor(accountId, figi) {
    this.accountId = accountId;
    this.figi = figi;
    this.interval = null;
  }
  
  startMonitoring() {
    this.interval = setInterval(async () => {
      await this.checkSignals();
      await this.checkRisks();
    }, 60000); // Проверка каждую минуту
  }
  
  stopMonitoring() {
    if (this.interval) {
      clearInterval(this.interval);
    }
  }
  
  async checkSignals() {
    const signal = await getAdvancedAnalysis(this.figi);
    
    if (signal.action === 'BUY' && signal.strength > 70) {
      console.log('🚀 Сильный сигнал на покупку!');
      // Отправка уведомления
    }
  }
  
  async checkRisks() {
    const risks = await monitorRisks(this.accountId);
    
    if (risks.recommendation.includes('Высокий уровень риска')) {
      console.log('⚠️ Высокий уровень риска!');
      // Отправка уведомления
    }
  }
}

// Использование
const monitor = new TradingBotMonitor(
  '1635493e-e47e-49bb-84ca-5feca7c718ad',
  'BBG000B9XRY4'
);
monitor.startMonitoring();
```

## 🔧 Настройка параметров

### Конфигурация риск-менеджмента
```yaml
# application.yml
risk-management:
  max-position-size: 0.05    # 5% от портфеля
  max-daily-loss: 0.02       # 2% дневной убыток
  max-drawdown: 0.15         # 15% максимальная просадка
  stop-loss: 0.05            # 5% стоп-лосс
  take-profit: 0.15          # 15% тейк-профит
```

### Настройка торговых стратегий
```yaml
trading-strategies:
  combined-analysis:
    trend-weight: 0.25
    sma-weight: 0.20
    rsi-weight: 0.15
    macd-weight: 0.15
    bb-weight: 0.10
    stoch-weight: 0.10
    volume-weight: 0.05
    min-signal-strength: 50
```

---

**Эти примеры помогут вам быстро начать работу с новыми возможностями торгового бота! 🚀** 