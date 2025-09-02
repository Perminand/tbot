package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.core.models.Portfolio;
import ru.tinkoff.piapi.core.models.Position;
import ru.tinkoff.piapi.contract.v1.OrderDirection;
// import ru.tinkoff.piapi.contract.v1.MoneyValue; // unused
import ru.perminov.dto.ShareDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioManagementService {
    
    private final PortfolioService portfolioService;
    private final OrderService orderService;
    private final MarketAnalysisService marketAnalysisService;
    private final BotLogService botLogService;
    private final InvestApiManager investApiManager;
    
    private final DynamicInstrumentService dynamicInstrumentService;
    private final MarginService marginService;
    private final RiskRuleService riskRuleService;
    private final AdvancedTradingStrategyService advancedTradingStrategyService;
    private final TradingSettingsService tradingSettingsService;
    
    // Целевые доли активов в портфеле
    private final Map<String, BigDecimal> targetAllocations = new HashMap<>();
    
    // Настройки автоматического мониторинга
    private boolean autoMonitoringEnabled = false;
    private String monitoredAccountId = null;
    
    // Инициализация целевых долей (пример)
    {
        targetAllocations.put("shares", new BigDecimal("0.60")); // 60% акции
        targetAllocations.put("bonds", new BigDecimal("0.30"));  // 30% облигации
        targetAllocations.put("etf", new BigDecimal("0.10"));    // 10% ETF
    }
    
    /**
     * Анализ текущего портфеля
     */
    public PortfolioAnalysis analyzePortfolio(String accountId) {
        Portfolio portfolio = portfolioService.getPortfolio(accountId);
        List<Position> positions = portfolio.getPositions();
        
        BigDecimal totalValue = BigDecimal.ZERO;
        Map<String, BigDecimal> currentAllocations = new HashMap<>();
        Map<String, BigDecimal> positionValues = new HashMap<>();
        
        // Расчет текущих значений позиций
        for (Position position : positions) {
            BigDecimal quantity = position.getQuantity();
            BigDecimal currentPrice = BigDecimal.ZERO;
            
            if (position.getCurrentPrice() != null) {
                try {
                    // Пробуем использовать правильный метод для Money
                    if (position.getCurrentPrice() instanceof ru.tinkoff.piapi.core.models.Money) {
                        ru.tinkoff.piapi.core.models.Money money = (ru.tinkoff.piapi.core.models.Money) position.getCurrentPrice();
                        currentPrice = money.getValue();
                        log.debug("Цена для {} через getValue(): {}", position.getFigi(), currentPrice);
                    } else {
                        // Фоллбек на парсинг строки
                        String priceStr = position.getCurrentPrice().toString();
                        log.debug("Price string for {}: {}", position.getFigi(), priceStr);
                        
                        if (priceStr.contains("value=")) {
                            String valuePart = priceStr.substring(priceStr.indexOf("value=") + 6);
                            valuePart = valuePart.substring(0, valuePart.indexOf(","));
                            currentPrice = new BigDecimal(valuePart);
                        } else {
                            String[] parts = priceStr.split("[^0-9.]");
                            for (String part : parts) {
                                if (!part.isEmpty() && part.matches("\\d+\\.?\\d*")) {
                                    currentPrice = new BigDecimal(part);
                                    break;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Не удалось получить цену для позиции {}: {}", position.getFigi(), e.getMessage());
                    currentPrice = BigDecimal.ZERO;
                }
            } else {
                currentPrice = BigDecimal.ZERO;
            }
            
            BigDecimal positionValue;
            
            // Для валютных позиций используем количество как стоимость
            if ("currency".equals(position.getInstrumentType())) {
                positionValue = quantity;
            } else {
                positionValue = quantity.multiply(currentPrice);
            }
            
            positionValues.put(position.getFigi(), positionValue);
            totalValue = totalValue.add(positionValue);
            
            // Группировка по типам инструментов
            String instrumentType = position.getInstrumentType();
            currentAllocations.merge(instrumentType, positionValue, BigDecimal::add);
        }
        
        // Расчет долей
        Map<String, BigDecimal> allocationPercentages = new HashMap<>();
        for (Map.Entry<String, BigDecimal> entry : currentAllocations.entrySet()) {
            if (totalValue.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal percentage = entry.getValue()
                    .divide(totalValue, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
                allocationPercentages.put(entry.getKey(), percentage);
            }
        }
        
        return new PortfolioAnalysis(
            totalValue,
            currentAllocations,
            allocationPercentages,
            positionValues,
            positions
        );
    }
    
    /**
     * Проверка необходимости ребалансировки
     */
    public RebalancingDecision checkRebalancing(String accountId) {
        PortfolioAnalysis analysis = analyzePortfolio(accountId);
        BigDecimal totalValue = analysis.getTotalValue();
        
        if (totalValue.compareTo(BigDecimal.ZERO) <= 0) {
            return new RebalancingDecision(false, "Недостаточно средств для ребалансировки");
        }
        
        Map<String, BigDecimal> currentAllocations = analysis.getAllocationPercentages();
        Map<String, BigDecimal> deviations = new HashMap<>();
        BigDecimal maxDeviation = BigDecimal.ZERO;
        
        // Расчет отклонений от целевых долей
        for (Map.Entry<String, BigDecimal> target : targetAllocations.entrySet()) {
            String assetType = target.getKey();
            BigDecimal targetPercentage = target.getValue().multiply(BigDecimal.valueOf(100));
            BigDecimal currentPercentage = currentAllocations.getOrDefault(assetType, BigDecimal.ZERO);
            BigDecimal deviation = currentPercentage.subtract(targetPercentage).abs();
            
            deviations.put(assetType, deviation);
            if (deviation.compareTo(maxDeviation) > 0) {
                maxDeviation = deviation;
            }
        }
        
        // Если максимальное отклонение больше 5%, нужна ребалансировка
        boolean needsRebalancing = maxDeviation.compareTo(BigDecimal.valueOf(5)) > 0;
        
        return new RebalancingDecision(
            needsRebalancing,
            needsRebalancing ? "Требуется ребалансировка" : "Портфель сбалансирован",
            deviations,
            maxDeviation
        );
    }
    
    /**
     * Выполнение ребалансировки портфеля
     */
    public void rebalancePortfolio(String accountId) {
        log.info("Начало ребалансировки портфеля для аккаунта: {}", accountId);
        
        PortfolioAnalysis analysis = analyzePortfolio(accountId);
        BigDecimal totalValue = analysis.getTotalValue();
        
        if (totalValue.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Недостаточно средств для ребалансировки");
            return;
        }
        
        // Расчет целевых значений для каждого типа активов
        Map<String, BigDecimal> targetValues = new HashMap<>();
        for (Map.Entry<String, BigDecimal> allocation : targetAllocations.entrySet()) {
            BigDecimal targetValue = totalValue.multiply(allocation.getValue());
            targetValues.put(allocation.getKey(), targetValue);
        }
        
        // Определение необходимых действий
        Map<String, BigDecimal> currentValues = analysis.getCurrentAllocations();
        
        for (Map.Entry<String, BigDecimal> target : targetValues.entrySet()) {
            String assetType = target.getKey();
            BigDecimal targetValue = target.getValue();
            BigDecimal currentValue = currentValues.getOrDefault(assetType, BigDecimal.ZERO);
            BigDecimal difference = targetValue.subtract(currentValue);
            
            if (difference.abs().compareTo(BigDecimal.valueOf(1000)) > 0) { // Минимальная сумма для ребалансировки
                log.info("{}: текущее значение = {}, целевое = {}, разница = {}", 
                    assetType, currentValue, targetValue, difference);
                
                // Здесь можно добавить логику для выбора конкретных инструментов
                // и размещения ордеров
            }
        }
        
        log.info("Ребалансировка портфеля завершена");
    }
    
    /**
     * Автоматическая торговля на основе анализа
     */
    public void executeTradingStrategy(String accountId, String figi) {
        try {
            // Проверяем доступность инструмента для торговли
            if (!dynamicInstrumentService.isInstrumentAvailable(figi)) {
                log.warn("Инструмент {} недоступен для торговли, пропускаем", figi);
                botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT, 
                    "Инструмент недоступен", "FIGI: " + figi + " - недоступен для торговли");
                return;
            }
            
            // Анализ тренда + ATR
            MarketAnalysisService.TrendAnalysis trend = 
                marketAnalysisService.analyzeTrend(figi, ru.tinkoff.piapi.contract.v1.CandleInterval.CANDLE_INTERVAL_DAY);
            int atrPeriod = tradingSettingsService.getInt("atr.period", 14);
            java.math.BigDecimal atr = marketAnalysisService.calculateATR(figi, ru.tinkoff.piapi.contract.v1.CandleInterval.CANDLE_INTERVAL_DAY, atrPeriod);
            if (trend.getCurrentPrice().compareTo(java.math.BigDecimal.ZERO) > 0) {
                java.math.BigDecimal atrPct = atr.divide(trend.getCurrentPrice(), 6, java.math.RoundingMode.HALF_UP);
                double minAtrPct = tradingSettingsService.getDouble("atr.min.pct", 0.002);
                double maxAtrPct = tradingSettingsService.getDouble("atr.max.pct", 0.08);
                // Фильтр слишком низкой волатильности (шум) и экстремальной волатильности
                if (atrPct.compareTo(java.math.BigDecimal.valueOf(minAtrPct)) < 0 || atrPct.compareTo(java.math.BigDecimal.valueOf(maxAtrPct)) > 0) {
                    log.info("ATR-фильтр: пропускаем {} (ATR%={})", figi, atrPct);
                    return;
                }
            }
            
            // Анализ портфеля
            PortfolioAnalysis portfolioAnalysis = analyzePortfolio(accountId);
            
            // Получаем рекомендуемое действие из продвинутого анализа сигналов
            AdvancedTradingStrategyService.TradingSignal advSignal = advancedTradingStrategyService.analyzeTradingSignal(figi, accountId);
            String actionByAdvanced = advSignal.getAction();

            // Базовый оппортьюнити для логирования и метрик (сохранено)
            TradingOpportunity opportunity = analyzeTradingOpportunity(figi, accountId);
            if (opportunity == null) {
                log.warn("Не удалось проанализировать торговую возможность для {}", figi);
                return;
            }
            
            // Сведение решений: отдаём приоритет продвинутому сигналу при достаточной силе
            double minStrength = tradingSettingsService.getDouble("signal.min.strength", 50.0);
            String action = actionByAdvanced != null && !"HOLD".equals(actionByAdvanced) &&
                (advSignal.getStrength() != null && advSignal.getStrength().compareTo(java.math.BigDecimal.valueOf(minStrength)) > 0)
                ? actionByAdvanced : opportunity.getRecommendedAction();
            log.info("Выполняем торговую операцию для {}: {}", figi, action);
            
            if ("BUY".equals(action)) {
                // Проверяем, есть ли свободные средства
                BigDecimal availableCash = getAvailableCash(portfolioAnalysis);
                BigDecimal buyingPower = marginService.getAvailableBuyingPower(accountId, portfolioAnalysis);
                log.info("Доступные средства для покупки: {}, покупательная способность: {}", availableCash, buyingPower);

                // Проверка средств: блокируем покупки только если не разрешена маржинальная торговля
                boolean allowNegativeCash = tradingSettingsService.getBoolean("margin-trading.allow-negative-cash", false);
                if (availableCash.compareTo(BigDecimal.ZERO) < 0 && !allowNegativeCash) {
                    log.warn("Реальные средства отрицательные ({}), блокируем покупки (маржинальная торговля отключена) [figi={}, accountId={}, price={}]", 
                            availableCash, figi, accountId, trend.getCurrentPrice());
                    botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT, 
                        "Блокировка покупок", String.format("FIGI: %s, Account: %s, Price: %.4f, Отрицательные средства: %.2f (маржинальная торговля отключена)", 
                            figi, accountId, trend.getCurrentPrice(), availableCash));
                    return;
                } else if (availableCash.compareTo(BigDecimal.ZERO) < 0 && allowNegativeCash) {
                    log.info("Реальные средства отрицательные ({}), но маржинальная торговля разрешена. Используем плечо. [figi={}, accountId={}, price={}]", 
                            availableCash, figi, accountId, trend.getCurrentPrice());
                    botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.RISK_MANAGEMENT, 
                        "Маржинальная покупка", String.format("FIGI: %s, Account: %s, Price: %.4f, Отрицательные средства: %.2f — используем плечо", 
                            figi, accountId, trend.getCurrentPrice(), availableCash));
                }

                // Если маржа включена, но недоступна для аккаунта — продолжаем с фоллбек-логикой внутри MarginService
                if (marginService.isMarginEnabled() && !marginService.isMarginOperationalForAccount(accountId)) {
                    log.warn("Маржа включена в настройках, но недоступна для аккаунта {}. Используем расчеты по настройкам (без реальных атрибутов).", accountId);
                }
                
                // Дополнительная проверка для маржинальных операций
                if (allowNegativeCash && availableCash.compareTo(BigDecimal.ZERO) < 0) {
                    double minBuyingPowerRatio = tradingSettingsService.getDouble("margin-trading.min-buying-power-ratio", 0.1);
                    BigDecimal minRequiredBuyingPower = trend.getCurrentPrice().multiply(BigDecimal.valueOf(minBuyingPowerRatio));
                    
                    if (buyingPower.compareTo(minRequiredBuyingPower) < 0) {
                        log.warn("Недостаточная покупательная способность для маржинальной операции [figi={}, accountId={}, price={}, ratio={}]. Требуется: {}, доступно: {}", 
                                figi, accountId, trend.getCurrentPrice(), minBuyingPowerRatio, minRequiredBuyingPower, buyingPower);
                        botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT, 
                            "Недостаточная покупательная способность", 
                            String.format("FIGI: %s, Account: %s, Price: %.4f, Ratio: %.3f, Требуется: %.2f, Доступно: %.2f", 
                                figi, accountId, trend.getCurrentPrice(), minBuyingPowerRatio, minRequiredBuyingPower, buyingPower));
                        return;
                    }
                }
                
                if (buyingPower.compareTo(BigDecimal.ZERO) > 0) {
                    // Проверяем, есть ли уже позиция по этому инструменту
                    boolean hasPosition = portfolioAnalysis.getPositionValues().containsKey(figi) && 
                                        portfolioAnalysis.getPositionValues().get(figi).compareTo(BigDecimal.ZERO) > 0;
                    
                    // Определяем размер покупки в зависимости от наличия позиции
                    BigDecimal buyAmount;
                    if (hasPosition) {
                        // Докупаем - используем меньшую сумму (1% от доступных средств)
                        buyAmount = buyingPower.multiply(BigDecimal.valueOf(0.01));
                        log.info("Докупаем позицию [figi={}, accountId={}, price={}] -> {} лотов", figi, accountId, trend.getCurrentPrice(), 
                                buyAmount.divide(trend.getCurrentPrice(), 0, RoundingMode.DOWN));
                    } else {
                        // Первая покупка - используем меньшую сумму (2% от доступных средств)
                        buyAmount = buyingPower.multiply(BigDecimal.valueOf(0.02));
                        log.info("Первая покупка [figi={}, accountId={}, price={}] -> {} лотов", figi, accountId, trend.getCurrentPrice(), 
                                buyAmount.divide(trend.getCurrentPrice(), 0, RoundingMode.DOWN));
                    }
                    
                    // Проверяем минимальную сумму для покупки (1 лот)
                    BigDecimal minBuyAmount = trend.getCurrentPrice();
                    if (buyAmount.compareTo(minBuyAmount) < 0) {
                        log.info("Сумма покупки {} меньше минимальной {}. Увеличиваем до минимальной.", buyAmount, minBuyAmount);
                        buyAmount = minBuyAmount;
                    }
                    
                    int lots = buyAmount.divide(trend.getCurrentPrice(), 0, RoundingMode.DOWN).intValue();

                    // ATR-кап размера позиции: ограничиваем стоимость позиции  по отношению к ATR
                    if (atr.compareTo(java.math.BigDecimal.ZERO) > 0) {
                        java.math.BigDecimal maxRiskPerTrade = portfolioAnalysis.getTotalValue().multiply(java.math.BigDecimal.valueOf(riskRuleService.getRiskPerTradePct()));
                        // Если стоп ~ 1*ATR, то стоимость позиции <= maxRisk / ATR
                        java.math.BigDecimal allowedLotsByAtr = maxRiskPerTrade.divide(atr, 0, RoundingMode.DOWN);
                        java.math.BigDecimal allowedLotsByPrice = allowedLotsByAtr.divide(trend.getCurrentPrice(), 0, RoundingMode.DOWN);
                        int capLots = allowedLotsByPrice.intValue();
                        if (capLots > 0 && lots > capLots) {
                            log.info("ATR-кап позиции: лоты {} -> {} (ATR={}, maxRisk={})", lots, capLots, atr, maxRiskPerTrade);
                            lots = capLots;
                        }
                    }
                    
                    // Дополнительная проверка: достаточно ли средств для покупки хотя бы 1 лота
                    if (buyingPower.compareTo(trend.getCurrentPrice()) < 0) {
                        log.warn("Недостаточно средств для покупки даже 1 лота [figi={}, accountId={}]. Нужно: {}, Доступно: {}", 
                                figi, accountId, trend.getCurrentPrice(), buyingPower);
                        botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT, 
                            "Недостаточно средств для покупки 1 лота", String.format("FIGI: %s, Account: %s, Price: %.4f, Нужно: %.2f, Доступно: %.2f", 
                                figi, accountId, trend.getCurrentPrice(), trend.getCurrentPrice(), buyingPower));
                        return;
                    }
                    
                    // Дополнительная проверка реальной доступности средств через API
                    try {
                        BigDecimal realAvailableCash = getAvailableCash(portfolioAnalysis);
                        BigDecimal requiredAmount = trend.getCurrentPrice().multiply(BigDecimal.valueOf(lots));
                        if (realAvailableCash.compareTo(requiredAmount) < 0) {
                            log.warn("Реальная проверка: недостаточно средств [figi={}, accountId={}] для покупки {} лотов. Нужно: {}, Доступно: {}", 
                                figi, accountId, lots, requiredAmount, realAvailableCash);
                            botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT, 
                                "Недостаточно реальных средств", String.format("FIGI: %s, Account: %s, Price: %.4f, Лотов: %d, Нужно: %.2f, Доступно: %.2f", 
                                    figi, accountId, trend.getCurrentPrice(), lots, requiredAmount, realAvailableCash));
                            return;
                        }
                    } catch (Exception e) {
                        log.warn("Ошибка проверки реальных средств для {}: {}", figi, e.getMessage());
                        // Продолжаем выполнение, но с осторожностью
                    }
                    
                    if (lots > 0) {
                        // Применяем стоп-правила если заданы (обрезаем размер позиции до стоп-риска)
                        PortfolioAnalysis finalAnalysis = portfolioAnalysis;
                        final int lotsBeforeRisk = lots;
                        java.util.concurrent.atomic.AtomicInteger adjustedLots = new java.util.concurrent.atomic.AtomicInteger(lotsBeforeRisk);
                        riskRuleService.findByFigi(figi).ifPresent(rule -> {
                            if (rule.getStopLossPct() != null) {
                                // мягкое ограничение: не превышать 1% портфеля на сделку при заданном SL
                                BigDecimal maxRiskPerTrade = finalAnalysis.getTotalValue().multiply(new BigDecimal("0.01"));
                                BigDecimal allowedCost = maxRiskPerTrade.divide(new BigDecimal(rule.getStopLossPct()), 0, RoundingMode.DOWN);
                                BigDecimal allowedLots = allowedCost.divide(trend.getCurrentPrice(), 0, RoundingMode.DOWN);
                                if (allowedLots.compareTo(BigDecimal.valueOf(adjustedLots.get())) < 0) {
                                    log.info("Ограничение по риску: сокращаем лоты {} -> {}", adjustedLots.get(), allowedLots);
                                    adjustedLots.set(allowedLots.intValue());
                                }
                            }
                        });
                        // Если явного правила нет — применяем дефолты из настроек
                        if (adjustedLots.get() == lotsBeforeRisk) {
                            double slDefault = riskRuleService.getDefaultStopLossPct();
                            BigDecimal maxRiskPerTrade = finalAnalysis.getTotalValue().multiply(BigDecimal.valueOf(riskRuleService.getRiskPerTradePct()));
                            BigDecimal allowedCost = maxRiskPerTrade.divide(BigDecimal.valueOf(slDefault), 0, RoundingMode.DOWN);
                            BigDecimal allowedLots = allowedCost.divide(trend.getCurrentPrice(), 0, RoundingMode.DOWN);
                            if (allowedLots.compareTo(BigDecimal.valueOf(adjustedLots.get())) < 0) {
                                adjustedLots.set(allowedLots.intValue());
                                log.info("Дефолтное ограничение по риску: лоты {} -> {}", lotsBeforeRisk, adjustedLots.get());
                            }
                        }
                        lots = adjustedLots.get();

                        String actionType = hasPosition ? "докупка" : "покупка";
                        BigDecimal totalCost = trend.getCurrentPrice().multiply(BigDecimal.valueOf(lots));
                        
                        // Определяем тип операции (маржинальная или обычная)
                        String operationType = (allowNegativeCash && availableCash.compareTo(BigDecimal.ZERO) < 0) ? "маржинальная " : "";
                        String fullActionType = operationType + actionType;
                        
                        log.info("Размещение ордера на {}: {} лотов по цене {} (общая стоимость: {}, доступные средства: {})", 
                            fullActionType, lots, trend.getCurrentPrice(), totalCost, availableCash);
                        botLogService.addLogEntry(BotLogService.LogLevel.TRADE, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                            "Размещение ордера на " + fullActionType, String.format("FIGI: %s, Лотов: %d, Цена: %.2f, Стоимость: %.2f, Средства: %.2f", 
                                figi, lots, trend.getCurrentPrice(), totalCost, availableCash));
                        
                        // Размещаем реальный ордер
                        try {
                            orderService.placeMarketOrder(figi, lots, OrderDirection.ORDER_DIRECTION_BUY, accountId);
                            botLogService.addLogEntry(BotLogService.LogLevel.SUCCESS, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                                "Ордер на " + fullActionType + " размещен", String.format("FIGI: %s, Лотов: %d", figi, lots));
                            // Авто-установка SL/TP по дефолтным настройкам, если для FIGI ещё нет правил
                            try {
                                if (riskRuleService.findByFigi(figi).isEmpty()) {
                                    double sl = riskRuleService.getDefaultStopLossPct();
                                    double tp = riskRuleService.getDefaultTakeProfitPct();
                                    riskRuleService.upsert(figi, sl, tp, true);
                                    log.info("Установлены уровни SL/TP для {}: SL={}%, TP={}%, активированы", figi, sl * 100, tp * 100);
                                    botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.RISK_MANAGEMENT,
                                        "Установлены SL/TP",
                                        String.format("FIGI: %s, SL: %.2f%%, TP: %.2f%%", figi, sl * 100, tp * 100));
                                }
                            } catch (Exception e) {
                                log.warn("Не удалось установить правила SL/TP для {}: {}", figi, e.getMessage());
                            }
                        } catch (Exception e) {
                            log.error("Ошибка размещения ордера на {}: {}", actionType, e.getMessage());
                            botLogService.addLogEntry(BotLogService.LogLevel.ERROR, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                                "Ошибка размещения ордера на " + actionType, e.getMessage());
                            // НЕ останавливаем выполнение, продолжаем с другими инструментами
                        }
                    } else {
                        log.warn("Не удалось рассчитать количество лотов для покупки. Сумма: {}, Цена: {}, Лотов: {}", 
                            buyAmount, trend.getCurrentPrice(), lots);
                        botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT, 
                            "Ошибка расчета лотов", String.format("Сумма: %.2f, Цена: %.2f, Лотов: %d", 
                                buyAmount, trend.getCurrentPrice(), lots));
                    }
                } else {
                    log.warn("Нет свободных средств для покупки");
                    botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT, 
                        "Нет свободных средств", "Доступно: " + buyingPower);
                }
            } else if ("SELL".equals(action)) {
                // Проверяем, есть ли позиция по этому инструменту
                BigDecimal positionValue = portfolioAnalysis.getPositionValues().get(figi);
                if (positionValue != null && positionValue.compareTo(BigDecimal.ZERO) != 0) {
                    // Находим позицию для получения количества лотов
                    Position position = portfolioAnalysis.getPositions().stream()
                        .filter(p -> p.getFigi().equals(figi))
                        .findFirst()
                        .orElse(null);
                    
                    if (position != null && position.getQuantity().compareTo(BigDecimal.ZERO) != 0) {
                        int lots = Math.abs(position.getQuantity().intValue()); // Берем абсолютное значение
                        boolean isShortPosition = position.getQuantity().compareTo(BigDecimal.ZERO) < 0;
                        
                        String actionDescription = isShortPosition ? "закрытие шорта" : "продажа";
                        log.info("Размещение ордера на {}: {} лотов по цене {}", actionDescription, lots, trend.getCurrentPrice());
                        
                        botLogService.addLogEntry(BotLogService.LogLevel.TRADE, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                            "Размещение ордера на " + actionDescription, String.format("FIGI: %s, Лотов: %d, Цена: %.2f", 
                                figi, lots, trend.getCurrentPrice()));
                        
                        // Размещаем реальный ордер
                        try {
                            orderService.placeMarketOrder(figi, lots, OrderDirection.ORDER_DIRECTION_SELL, accountId);
                            botLogService.addLogEntry(BotLogService.LogLevel.SUCCESS, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                                "Ордер на " + actionDescription + " размещен", String.format("FIGI: %s, Лотов: %d", figi, lots));
                        } catch (Exception e) {
                            log.error("Ошибка размещения ордера на {}: {}", actionDescription, e.getMessage());
                            botLogService.addLogEntry(BotLogService.LogLevel.ERROR, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                                "Ошибка размещения ордера на " + actionDescription, e.getMessage());
                            // НЕ останавливаем выполнение, продолжаем с другими инструментами
                        }
                    } else {
                        log.warn("Нет позиции для продажи по инструменту {}", figi);
                        botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT, 
                            "Нет позиции для продажи", "FIGI: " + figi);
                    }
                } else {
                    // Позиции нет. Рассматриваем открытие шорта, если это разрешено и доступно
                    if (marginService.canOpenShort(figi) && marginService.isMarginOperationalForAccount(accountId)) {
                        BigDecimal targetShortAmount = marginService.calculateTargetShortAmount(accountId, portfolioAnalysis);
                        if (targetShortAmount.compareTo(trend.getCurrentPrice()) >= 0) {
                            int lots = targetShortAmount.divide(trend.getCurrentPrice(), 0, RoundingMode.DOWN).intValue();
                            
                            // Дополнительная проверка реальной доступности маржи для шорта
                            try {
                                var marginAttrs = marginService.getAccountMarginAttributes(accountId);
                                if (marginAttrs != null) {
                                    BigDecimal liquid = marginService.toBigDecimal(marginAttrs.getLiquidPortfolio());
                                    BigDecimal minimal = marginService.toBigDecimal(marginAttrs.getMinimalMargin());
                                    BigDecimal availableMargin = liquid.subtract(minimal);
                                    BigDecimal requiredMargin = trend.getCurrentPrice().multiply(BigDecimal.valueOf(lots));
                                    
                                    if (availableMargin.compareTo(requiredMargin) < 0) {
                                        log.warn("Реальная проверка маржи: недостаточно для шорта {} лотов. Нужно: {}, Доступно: {}", 
                                            lots, requiredMargin, availableMargin);
                                        botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT, 
                                            "Недостаточно маржи для шорта", String.format("Лотов: %d, Нужно: %.2f, Доступно: %.2f", 
                                                lots, requiredMargin, availableMargin));
                                        return;
                                    }
                                }
                            } catch (Exception e) {
                                log.warn("Ошибка проверки маржи для шорта {}: {}", figi, e.getMessage());
                                // Продолжаем выполнение, но с осторожностью
                            }
                            
                            log.info("Открытие шорта по {}: {} лотов", figi, lots);
                            botLogService.addLogEntry(BotLogService.LogLevel.TRADE, BotLogService.LogCategory.AUTOMATIC_TRADING,
                                "Открытие шорта", String.format("FIGI: %s, Лотов: %d", figi, lots));
                            try {
                                orderService.placeMarketOrder(figi, lots, OrderDirection.ORDER_DIRECTION_SELL, accountId);
                                botLogService.addLogEntry(BotLogService.LogLevel.SUCCESS, BotLogService.LogCategory.AUTOMATIC_TRADING,
                                    "Шорт открыт", String.format("FIGI: %s, Лотов: %d", figi, lots));
                                // Авто-установка SL/TP по дефолтным настройкам, если для FIGI ещё нет правил
                                try {
                                    if (riskRuleService.findByFigi(figi).isEmpty()) {
                                        double sl = riskRuleService.getDefaultStopLossPct();
                                        double tp = riskRuleService.getDefaultTakeProfitPct();
                                        riskRuleService.upsert(figi, sl, tp, true);
                                        log.info("Установлены уровни SL/TP для {}: SL={}%, TP={}%, активированы (шорт)", figi, sl * 100, tp * 100);
                                        botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.RISK_MANAGEMENT,
                                            "Установлены SL/TP (шорт)",
                                            String.format("FIGI: %s, SL: %.2f%%, TP: %.2f%%", figi, sl * 100, tp * 100));
                                    }
                                } catch (Exception e) {
                                    log.warn("Не удалось установить правила SL/TP для {} (шорт): {}", figi, e.getMessage());
                                }
                            } catch (Exception e) {
                                log.error("Ошибка открытия шорта: {}", e.getMessage());
                                botLogService.addLogEntry(BotLogService.LogLevel.ERROR, BotLogService.LogCategory.AUTOMATIC_TRADING,
                                    "Ошибка открытия шорта", e.getMessage());
                            }
                        } else {
                            log.warn("Недостаточно лимита для шорта по {}", figi);
                        }
                    } else if (marginService.canOpenShort(figi) && !marginService.isMarginOperationalForAccount(accountId)) {
                        log.warn("Шорт разрешен настройками, но недоступен для аккаунта {} (песочница/нет маржинальных атрибутов)", accountId);
                    } else {
                        log.warn("Нет позиции для продажи по инструменту {}", figi);
                        botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT, 
                            "Нет позиции для продажи", "FIGI: " + figi);
                    }
                }
            } else if ("BUY".equals(action)) {
                // Проверяем, есть ли шорт-позиция для закрытия
                BigDecimal positionValue = portfolioAnalysis.getPositionValues().get(figi);
                if (positionValue != null && positionValue.compareTo(BigDecimal.ZERO) < 0) {
                    log.info("Обнаружена шорт-позиция для закрытия: {} (значение: {})", figi, positionValue);
                    // Находим шорт-позицию для получения количества лотов
                    Position position = portfolioAnalysis.getPositions().stream()
                        .filter(p -> p.getFigi().equals(figi))
                        .findFirst()
                        .orElse(null);
                    
                    if (position != null && position.getQuantity().compareTo(BigDecimal.ZERO) < 0) {
                        int lots = Math.abs(position.getQuantity().intValue()); // Берем абсолютное значение
                        log.info("Закрытие шорта: {} лотов по цене {}", lots, trend.getCurrentPrice());
                        
                        botLogService.addLogEntry(BotLogService.LogLevel.TRADE, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                            "Закрытие шорта", String.format("FIGI: %s, Лотов: %d, Цена: %.2f", 
                                figi, lots, trend.getCurrentPrice()));
                        
                        // Размещаем реальный ордер на покупку для закрытия шорта
                        // ВАЖНО: При закрытии шортов НЕ проверяем отрицательные средства,
                        // так как это может привести к неконтролируемым убыткам
                        try {
                            orderService.placeMarketOrder(figi, lots, OrderDirection.ORDER_DIRECTION_BUY, accountId);
                            botLogService.addLogEntry(BotLogService.LogLevel.SUCCESS, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                                "Шорт закрыт", String.format("FIGI: %s, Лотов: %d", figi, lots));
                        } catch (Exception e) {
                            log.error("Ошибка закрытия шорта: {}", e.getMessage());
                            botLogService.addLogEntry(BotLogService.LogLevel.ERROR, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                                "Ошибка закрытия шорта", e.getMessage());
                            // НЕ останавливаем выполнение, продолжаем с другими инструментами
                        }
                    } else {
                        log.warn("Нет шорт-позиции для закрытия по инструменту {}", figi);
                        botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT, 
                            "Нет шорт-позиции для закрытия", "FIGI: " + figi);
                    }
                } else {
                    // Нет шорт-позиции, но есть сигнал на покупку - это обычная покупка
                    log.info("Обычная покупка (не закрытие шорта): {} (позиция: {})", figi, positionValue);
                    // Проверяем, есть ли свободные средства
                    BigDecimal availableCash = getAvailableCash(portfolioAnalysis);
                    BigDecimal buyingPower = marginService.getAvailableBuyingPower(accountId, portfolioAnalysis);
                    
                    // Дополнительная проверка: если реальные средства отрицательные, блокируем покупки
                    if (availableCash.compareTo(BigDecimal.ZERO) < 0) {
                        log.warn("Реальные средства отрицательные ({}), блокируем покупки", availableCash);
                        botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT, 
                            "Блокировка покупок", String.format("Отрицательные средства: %.2f", availableCash));
                        return;
                    }
                    
                    if (buyingPower.compareTo(BigDecimal.ZERO) > 0) {
                        // Логика покупки (аналогично BUY выше)
                        // ... (можно вынести в отдельный метод)
                        log.info("Покупка нового инструмента: {} (покупательная способность: {})", figi, buyingPower);
                        botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                            "Покупка нового инструмента", String.format("FIGI: %s, Покупательная способность: %.2f", figi, buyingPower));
                    } else {
                        log.warn("Нет свободных средств для покупки нового инструмента {}", figi);
                        botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT, 
                            "Нет средств для покупки", "FIGI: " + figi);
                    }
                }
            } else {
                log.info("Действие HOLD - никаких операций не выполняем");
                botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                    "Действие HOLD", "FIGI: " + figi + " - никаких операций не выполняем");
            }
            
        } catch (Exception e) {
            log.error("Ошибка при выполнении торговой стратегии для {}: {}", figi, e.getMessage());
            botLogService.addLogEntry(BotLogService.LogLevel.ERROR, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                "Ошибка выполнения торговой стратегии", "FIGI: " + figi + " - " + e.getMessage());
            // НЕ останавливаем выполнение, продолжаем с другими инструментами
        }
    }
    
    private BigDecimal getAvailableCash(PortfolioAnalysis analysis) {
        // Получаем реальные доступные средства из портфеля
        // Ищем позицию с валютой (обычно RUB)
        for (Position position : analysis.getPositions()) {
            if ("currency".equals(position.getInstrumentType())) {
                log.info("Найдена валюта в портфеле: {} - {}", position.getFigi(), position.getQuantity());
                return position.getQuantity();
            }
        }
        
        // Если не найдена валюта, возвращаем 0
        log.warn("Не найдены доступные средства в портфеле");
        return BigDecimal.ZERO;
    }
    
    public static class PortfolioAnalysis {
        private final BigDecimal totalValue;
        private final Map<String, BigDecimal> currentAllocations;
        private final Map<String, BigDecimal> allocationPercentages;
        private final Map<String, BigDecimal> positionValues;
        private final List<Position> positions;
        
        public PortfolioAnalysis(BigDecimal totalValue, 
                               Map<String, BigDecimal> currentAllocations,
                               Map<String, BigDecimal> allocationPercentages,
                               Map<String, BigDecimal> positionValues,
                               List<Position> positions) {
            this.totalValue = totalValue;
            this.currentAllocations = currentAllocations;
            this.allocationPercentages = allocationPercentages;
            this.positionValues = positionValues;
            this.positions = positions;
        }
        
        // Getters
        public BigDecimal getTotalValue() { return totalValue; }
        public Map<String, BigDecimal> getCurrentAllocations() { return currentAllocations; }
        public Map<String, BigDecimal> getAllocationPercentages() { return allocationPercentages; }
        public Map<String, BigDecimal> getPositionValues() { return positionValues; }
        public List<Position> getPositions() { return positions; }
    }
    
    public static class RebalancingDecision {
        private final boolean needsRebalancing;
        private final String reason;
        private final Map<String, BigDecimal> deviations;
        private final BigDecimal maxDeviation;
        
        public RebalancingDecision(boolean needsRebalancing, String reason) {
            this(needsRebalancing, reason, Map.of(), BigDecimal.ZERO);
        }
        
        public RebalancingDecision(boolean needsRebalancing, String reason, 
                                 Map<String, BigDecimal> deviations, BigDecimal maxDeviation) {
            this.needsRebalancing = needsRebalancing;
            this.reason = reason;
            this.deviations = deviations;
            this.maxDeviation = maxDeviation;
        }
        
        // Getters
        public boolean isNeedsRebalancing() { return needsRebalancing; }
        public String getReason() { return reason; }
        public Map<String, BigDecimal> getDeviations() { return deviations; }
        public BigDecimal getMaxDeviation() { return maxDeviation; }
    }
    
    /**
     * Автоматический выбор лучших инструментов для торговли
     */
    public List<TradingOpportunity> findBestTradingOpportunities(String accountId) {
        try {
            String mode = investApiManager != null ? investApiManager.getCurrentMode() : null;
            log.info("Поиск лучших торговых возможностей для аккаунта: {} (mode={})", accountId, mode);
            botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.TRADING_STRATEGY, 
                "Начало поиска торговых возможностей", "Аккаунт: " + accountId + (mode != null ? ", Режим: " + mode : ""));
            
            List<TradingOpportunity> opportunities = new ArrayList<>();
            
            // 1. Анализируем существующие позиции для продажи
            List<TradingOpportunity> sellOpportunities = analyzeExistingPositions(accountId);
            opportunities.addAll(sellOpportunities);
            
            // 2. Анализируем новые инструменты для покупки
            List<ShareDto> availableShares = getAvailableShares();
            botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.MARKET_ANALYSIS, 
                "Получен список инструментов", "Количество: " + availableShares.size());
            
            for (ShareDto share : availableShares) {
                // Дополнительная проверка статуса торговли
                if (!"SECURITY_TRADING_STATUS_NORMAL_TRADING".equals(share.getTradingStatus())) {
                    log.debug("Пропускаем инструмент {} - статус торговли: {}", share.getFigi(), share.getTradingStatus());
                    continue;
                }
                
                try {
                    TradingOpportunity opportunity = analyzeTradingOpportunity(share.getFigi(), accountId);
                    if (opportunity != null) {
                        opportunities.add(opportunity);
                        botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.TECHNICAL_INDICATORS, 
                            "Анализ инструмента завершен", String.format("FIGI: %s, Score: %.1f, Действие: %s", 
                                share.getFigi(), opportunity.getScore(), opportunity.getRecommendedAction()));
                    }
                    
                    // Добавляем задержку между запросами для избежания лимитов API
                    Thread.sleep(100); // 100ms задержка
                    
                } catch (Exception e) {
                    log.warn("Ошибка анализа инструмента {}: {}", share.getFigi(), e.getMessage());
                    botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.TECHNICAL_INDICATORS, 
                        "Ошибка анализа инструмента", "FIGI: " + share.getFigi() + ", Ошибка: " + e.getMessage());
                    // Продолжаем с следующим инструментом, не останавливаем выполнение
                }
            }
            
            // Сортируем по приоритету (лучшие возможности первыми)
            opportunities.sort((o1, o2) -> o2.getScore().compareTo(o1.getScore()));
            
            // Возвращаем топ-10 возможностей
            List<TradingOpportunity> result = opportunities.stream().limit(10).collect(Collectors.toList());
            
            botLogService.addLogEntry(BotLogService.LogLevel.SUCCESS, BotLogService.LogCategory.TRADING_STRATEGY, 
                "Поиск торговых возможностей завершен", "Найдено возможностей: " + result.size());
            
            return result;
            
        } catch (Exception e) {
            log.error("Ошибка при поиске торговых возможностей: {}", e.getMessage());
            botLogService.addLogEntry(BotLogService.LogLevel.ERROR, BotLogService.LogCategory.TRADING_STRATEGY, 
                "Ошибка поиска торговых возможностей", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Анализ существующих позиций для продажи
     */
    private List<TradingOpportunity> analyzeExistingPositions(String accountId) {
        List<TradingOpportunity> sellOpportunities = new ArrayList<>();
        
        try {
            PortfolioAnalysis portfolioAnalysis = analyzePortfolio(accountId);
            List<Position> positions = portfolioAnalysis.getPositions();
            
            log.info("Анализ {} существующих позиций для продажи", positions.size());
            botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.PORTFOLIO_MANAGEMENT, 
                "Анализ существующих позиций", "Количество позиций: " + positions.size());
            
            for (Position position : positions) {
                // Пропускаем валютные позиции
                if ("currency".equals(position.getInstrumentType())) {
                    continue;
                }
                
                // Анализируем позиции с количеством != 0 (включая шорты)
                if (position.getQuantity().compareTo(BigDecimal.ZERO) != 0) {
                    try {
                        TradingOpportunity opportunity = analyzeTradingOpportunity(position.getFigi(), accountId);
                        
                        // Определяем, является ли позиция шортом
                        boolean isShortPosition = position.getQuantity().compareTo(BigDecimal.ZERO) < 0;
                        
                        // Для шортов логика обратная: если рекомендуют SELL, то нужно закрыть шорт (BUY)
                        // Для длинных позиций: если рекомендуют SELL, то продаем
                        String actionForPosition = isShortPosition ? 
                            ("SELL".equals(opportunity.getRecommendedAction()) ? "BUY" : opportunity.getRecommendedAction()) :
                            opportunity.getRecommendedAction();
                        
                        if (opportunity != null && ("SELL".equals(actionForPosition) || "BUY".equals(actionForPosition))) {
                            // Увеличиваем score для позиций, которые нужно закрыть
                            opportunity = new TradingOpportunity(
                                opportunity.getFigi(),
                                opportunity.getCurrentPrice(),
                                opportunity.getTrend(),
                                opportunity.getRsi(),
                                opportunity.getSma20(),
                                opportunity.getSma50(),
                                opportunity.getScore().add(BigDecimal.valueOf(10)), // Бонус за существующую позицию
                                actionForPosition
                            );
                            sellOpportunities.add(opportunity);
                            
                            String actionDescription = isShortPosition ? 
                                ("BUY".equals(actionForPosition) ? "закрытия шорта" : "действия с шортом") :
                                ("SELL".equals(actionForPosition) ? "продажи" : "действия с позицией");
                            
                            log.info("Найдена возможность {}: {} (Score: {}, Позиция: {})", 
                                actionDescription, position.getFigi(), opportunity.getScore(), 
                                isShortPosition ? "ШОРТ" : "ДЛИННАЯ");
                            botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.PORTFOLIO_MANAGEMENT, 
                                "Найдена возможность " + actionDescription, String.format("FIGI: %s, Score: %.1f, Тип: %s", 
                                    position.getFigi(), opportunity.getScore(), isShortPosition ? "ШОРТ" : "ДЛИННАЯ"));
                        }
                        
                        // Добавляем задержку между запросами
                        Thread.sleep(200); // 200ms задержка для анализа позиций
                        
                    } catch (Exception e) {
                        log.warn("Ошибка анализа позиции {}: {}", position.getFigi(), e.getMessage());
                    }
                }
            }
            
            log.info("Найдено {} возможностей для продажи", sellOpportunities.size());
            botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.PORTFOLIO_MANAGEMENT, 
                "Анализ позиций завершен", "Возможностей продажи: " + sellOpportunities.size());
            
        } catch (Exception e) {
            log.error("Ошибка при анализе существующих позиций: {}", e.getMessage());
            botLogService.addLogEntry(BotLogService.LogLevel.ERROR, BotLogService.LogCategory.PORTFOLIO_MANAGEMENT, 
                "Ошибка анализа позиций", e.getMessage());
        }
        
        return sellOpportunities;
    }
    
    /**
     * Анализ торговой возможности для конкретного инструмента
     */
    private TradingOpportunity analyzeTradingOpportunity(String figi, String accountId) {
        try {
            // Получаем технический анализ
            MarketAnalysisService.TrendAnalysis trendAnalysis = 
                marketAnalysisService.analyzeTrend(figi, ru.tinkoff.piapi.contract.v1.CandleInterval.CANDLE_INTERVAL_DAY);
            
            // Получаем технические индикаторы
            BigDecimal sma20 = marketAnalysisService.calculateSMA(figi, 
                ru.tinkoff.piapi.contract.v1.CandleInterval.CANDLE_INTERVAL_DAY, 20);
            BigDecimal sma50 = marketAnalysisService.calculateSMA(figi, 
                ru.tinkoff.piapi.contract.v1.CandleInterval.CANDLE_INTERVAL_DAY, 50);
            BigDecimal rsi = marketAnalysisService.calculateRSI(figi, 
                ru.tinkoff.piapi.contract.v1.CandleInterval.CANDLE_INTERVAL_DAY, 14);
            
            // Рассчитываем оценку (score) для инструмента
            BigDecimal score = calculateTradingScore(trendAnalysis, sma20, sma50, rsi);
            
            // Получаем информацию о портфеле для проверки позиций
            PortfolioAnalysis portfolioAnalysis = analyzePortfolio(accountId);
            boolean hasPosition = portfolioAnalysis.getPositionValues().containsKey(figi) && 
                                portfolioAnalysis.getPositionValues().get(figi).compareTo(BigDecimal.ZERO) > 0;
            
            // Определяем рекомендуемое действие с учетом позиций
            String recommendedAction = determineRecommendedAction(trendAnalysis, rsi, hasPosition);
            
            return new TradingOpportunity(
                figi,
                trendAnalysis.getCurrentPrice(),
                trendAnalysis.getTrend().name(),
                rsi,
                sma20,
                sma50,
                score,
                recommendedAction
            );
            
        } catch (Exception e) {
            log.warn("Ошибка анализа торговой возможности для {}: {}", figi, e.getMessage());
            return null;
        }
    }
    
    /**
     * Расчет оценки торговой возможности
     */
    private BigDecimal calculateTradingScore(MarketAnalysisService.TrendAnalysis trendAnalysis, 
                                           BigDecimal sma20, BigDecimal sma50, BigDecimal rsi) {
        BigDecimal score = BigDecimal.ZERO;
        
        // Оценка тренда
        switch (trendAnalysis.getTrend()) {
            case BULLISH:
                score = score.add(BigDecimal.valueOf(30));
                break;
            case SIDEWAYS:
                score = score.add(BigDecimal.valueOf(15));
                break;
            case BEARISH:
                score = score.add(BigDecimal.valueOf(25)); // Увеличиваем score для BEARISH тренда
                break;
            default:
                // UNKNOWN or other values
                score = score.add(BigDecimal.valueOf(0));
                break;
        }
        
        // Оценка RSI
        if (rsi.compareTo(BigDecimal.valueOf(30)) < 0) {
            // Перепроданность - хорошая возможность для покупки
            score = score.add(BigDecimal.valueOf(25));
        } else if (rsi.compareTo(BigDecimal.valueOf(70)) > 0) {
            // Перекупленность - возможность для продажи/шорта
            score = score.add(BigDecimal.valueOf(30)); // Увеличиваем score для перекупленности
        } else {
            // Нейтральная зона
            score = score.add(BigDecimal.valueOf(10));
        }
        
        // Оценка SMA
        if (sma20.compareTo(sma50) > 0) {
            score = score.add(BigDecimal.valueOf(15));
        } else {
            // BEARISH тренд + SMA20 < SMA50 = хорошая возможность для шорта
            if (trendAnalysis.getTrend() == MarketAnalysisService.TrendType.BEARISH) {
                score = score.add(BigDecimal.valueOf(20));
            }
        }
        
        // Оценка волатильности (если цена не равна нулю)
        if (trendAnalysis.getCurrentPrice().compareTo(BigDecimal.ZERO) > 0) {
            score = score.add(BigDecimal.valueOf(10));
        }
        
        return score;
    }
    
    /**
     * Определение рекомендуемого действия
     */
    private String determineRecommendedAction(MarketAnalysisService.TrendAnalysis trendAnalysis, BigDecimal rsi, boolean hasPosition) {
        // Логика для принятия торговых решений с учетом возможности докупки, продажи и шортов
        // Примечание: проверка доступности средств выполняется в executeTradingStrategy
        
        if (trendAnalysis.getTrend() == MarketAnalysisService.TrendType.BULLISH) {
            if (rsi.compareTo(BigDecimal.valueOf(40)) < 0) {
                return "BUY"; // Сильная покупка при перепроданности (докупаем или покупаем)
            } else if (rsi.compareTo(BigDecimal.valueOf(60)) < 0) {
                return hasPosition ? "HOLD" : "BUY"; // Умеренная покупка - докупаем только при хороших условиях
            } else if (rsi.compareTo(BigDecimal.valueOf(75)) > 0) {
                return hasPosition ? "SELL" : "HOLD"; // Продажа при перекупленности даже в восходящем тренде
            }
        } else if (trendAnalysis.getTrend() == MarketAnalysisService.TrendType.BEARISH) {
            if (rsi.compareTo(BigDecimal.valueOf(70)) > 0) {
                return hasPosition ? "SELL" : "HOLD"; // Сильная продажа при перекупленности (только если есть позиция)
            } else if (rsi.compareTo(BigDecimal.valueOf(50)) > 0) {
                return hasPosition ? "SELL" : "HOLD"; // Умеренная продажа при нисходящем тренде (только если есть позиция)
            } else if (rsi.compareTo(BigDecimal.valueOf(30)) < 0) {
                return "BUY"; // Покупка при сильной перепроданности даже в нисходящем тренде
            }
        }
        
        // Для бокового тренда используем RSI
        if (rsi.compareTo(BigDecimal.valueOf(35)) < 0) {
            return "BUY"; // Докупаем при сильной перепроданности
        } else if (rsi.compareTo(BigDecimal.valueOf(65)) > 0) {
            return hasPosition ? "SELL" : "HOLD"; // Продажа при перекупленности (только если есть позиция)
        }
        
        return "HOLD";
    }
    
    /**
     * Получение доступных акций
     */
    private List<ShareDto> getAvailableShares() {
        try {
            log.info("Получение доступных инструментов через DynamicInstrumentService...");
            
            // Используем новый динамический сервис
            List<ShareDto> instruments = dynamicInstrumentService.getAvailableInstruments();
            
            log.info("Получено {} доступных инструментов для анализа", instruments.size());
            return instruments;
            
        } catch (Exception e) {
            log.error("Ошибка при получении инструментов: {}", e.getMessage());
            // В случае ошибки возвращаем базовый набор инструментов
            return getFallbackInstruments();
        }
    }
    
    /**
     * Резервный набор инструментов в случае ошибки получения данных
     */
    private List<ShareDto> getFallbackInstruments() {
        List<ShareDto> shares = new ArrayList<>();
        
        // Добавляем популярные акции
        ShareDto apple = new ShareDto();
        apple.setFigi("BBG000B9XRY4");
        apple.setTicker("AAPL");
        apple.setName("Apple Inc.");
        apple.setCurrency("USD");
        apple.setExchange("MOEX");
        apple.setTradingStatus("SECURITY_TRADING_STATUS_NORMAL_TRADING");
        shares.add(apple);
        
        // Добавляем облигацию из портфеля
        ShareDto bond = new ShareDto();
        bond.setFigi("TCS00A107D74");
        bond.setTicker("TCS00A10");
        bond.setName("Облигация Тинькофф");
        bond.setCurrency("RUB");
        bond.setExchange("MOEX");
        bond.setTradingStatus("SECURITY_TRADING_STATUS_NORMAL_TRADING");
        shares.add(bond);
        
        return shares;
    }
    
    /**
     * Автоматическое выполнение торговых операций
     */
    public void executeAutomaticTrading(String accountId) {
        try {
            String mode = investApiManager != null ? investApiManager.getCurrentMode() : null;
            log.info("Запуск автоматической торговли для аккаунта: {} (mode={})", accountId, mode);
            botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                "Запуск автоматической торговли", "Аккаунт: " + accountId + (mode != null ? ", Режим: " + mode : ""));
            
            // 1. АНАЛИЗ ПОРТФЕЛЯ
            log.info("Начало анализа портфеля для аккаунта: {}", accountId);
            botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.PORTFOLIO_ANALYSIS, 
                "Начало анализа портфеля", "Аккаунт: " + accountId);
            
            PortfolioAnalysis portfolioAnalysis = analyzePortfolio(accountId);
            log.info("Анализ портфеля завершен. Общая стоимость: {}, Позиций: {}", 
                portfolioAnalysis.getTotalValue(), portfolioAnalysis.getPositions().size());
            botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.PORTFOLIO_ANALYSIS, 
                "Анализ портфеля завершен", String.format("Общая стоимость: %.2f, Позиций: %d", 
                    portfolioAnalysis.getTotalValue(), portfolioAnalysis.getPositions().size()));
            
            // 2. ПРОВЕРКА РЕБАЛАНСИРОВКИ
            log.info("Проверка необходимости ребалансировки");
            botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.PORTFOLIO_ANALYSIS, 
                "Проверка ребалансировки", "");
            
            RebalancingDecision rebalancingDecision = checkRebalancing(accountId);
            if (rebalancingDecision.isNeedsRebalancing()) {
                log.info("Требуется ребалансировка: {}", rebalancingDecision.getReason());
                botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.PORTFOLIO_ANALYSIS, 
                    "Требуется ребалансировка", rebalancingDecision.getReason());
                
                // Выполняем ребалансировку
                log.info("Выполнение ребалансировки портфеля");
                botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.PORTFOLIO_ANALYSIS, 
                    "Выполнение ребалансировки", "");
                rebalancePortfolio(accountId);
            } else {
                log.info("Ребалансировка не требуется");
                botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.PORTFOLIO_ANALYSIS, 
                    "Ребалансировка не требуется", "");
            }
            
            // 3. ПОИСК ТОРГОВЫХ ВОЗМОЖНОСТЕЙ
            log.info("Поиск торговых возможностей");
            botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.MARKET_ANALYSIS, 
                "Поиск торговых возможностей", "");
            
            List<TradingOpportunity> opportunities = findBestTradingOpportunities(accountId);
            
            if (opportunities.isEmpty()) {
                log.info("Нет подходящих торговых возможностей");
                botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                    "Нет торговых возможностей", "Подходящих инструментов не найдено");
                return;
            }
            
            // Выбираем лучшую возможность для торговли (предпочитаем BUY/SELL над HOLD)
            TradingOpportunity bestOpportunity = null;
            
            // Сначала ищем возможности с действиями BUY или SELL
            for (TradingOpportunity opportunity : opportunities) {
                if (("BUY".equals(opportunity.getRecommendedAction()) || "SELL".equals(opportunity.getRecommendedAction())) &&
                    opportunity.getScore().compareTo(BigDecimal.valueOf(30)) >= 0) {
                    bestOpportunity = opportunity;
                    break;
                }
            }
            
            // Если не нашли BUY/SELL, берем первую возможность с высоким score
            if (bestOpportunity == null) {
                for (TradingOpportunity opportunity : opportunities) {
                    if (opportunity.getScore().compareTo(BigDecimal.valueOf(30)) >= 0) {
                        bestOpportunity = opportunity;
                        break;
                    }
                }
            }
            
            if (bestOpportunity != null) {
                log.info("Выполняем торговую операцию для {}: {} (Score: {})", 
                    bestOpportunity.getFigi(), bestOpportunity.getRecommendedAction(), bestOpportunity.getScore());
                
                botLogService.addLogEntry(BotLogService.LogLevel.TRADE, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                    "Выполнение торговой операции", String.format("FIGI: %s, Действие: %s, Score: %.1f, RSI: %.1f, Тренд: %s", 
                        bestOpportunity.getFigi(), bestOpportunity.getRecommendedAction(), bestOpportunity.getScore(), 
                        bestOpportunity.getRsi(), bestOpportunity.getTrend()));
                
                try {
                    executeTradingStrategy(accountId, bestOpportunity.getFigi());
                } catch (Exception e) {
                    log.error("Ошибка выполнения торговой стратегии для {}: {}", bestOpportunity.getFigi(), e.getMessage());
                    botLogService.addLogEntry(BotLogService.LogLevel.ERROR, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                        "Ошибка выполнения торговой стратегии", "FIGI: " + bestOpportunity.getFigi() + " - " + e.getMessage());
                    // Продолжаем выполнение, не останавливаем бота
                }
            } else {
                log.info("Нет подходящих торговых возможностей с достаточным score");
                botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT, 
                    "Нет подходящих торговых возможностей", "Все возможности имеют score < 30 или только HOLD");
            }
            
            log.info("Автоматическая торговля завершена");
            botLogService.addLogEntry(BotLogService.LogLevel.SUCCESS, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                "Автоматическая торговля завершена", "");
            
        } catch (Exception e) {
            log.error("Ошибка при автоматической торговле: {}", e.getMessage());
            botLogService.addLogEntry(BotLogService.LogLevel.ERROR, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                "Ошибка автоматической торговли", e.getMessage());
            // НЕ останавливаем бота, продолжаем работу
        }
    }
    
    /**
     * Класс для представления торговой возможности
     */
    public static class TradingOpportunity {
        private final String figi;
        private final BigDecimal currentPrice;
        private final String trend;
        private final BigDecimal rsi;
        private final BigDecimal sma20;
        private final BigDecimal sma50;
        private final BigDecimal score;
        private final String recommendedAction;
        
        public TradingOpportunity(String figi, BigDecimal currentPrice, String trend, 
                                BigDecimal rsi, BigDecimal sma20, BigDecimal sma50, 
                                BigDecimal score, String recommendedAction) {
            this.figi = figi;
            this.currentPrice = currentPrice;
            this.trend = trend;
            this.rsi = rsi;
            this.sma20 = sma20;
            this.sma50 = sma50;
            this.score = score;
            this.recommendedAction = recommendedAction;
        }
        
        // Getters
        public String getFigi() { return figi; }
        public BigDecimal getCurrentPrice() { return currentPrice; }
        public String getTrend() { return trend; }
        public BigDecimal getRsi() { return rsi; }
        public BigDecimal getSma20() { return sma20; }
        public BigDecimal getSma50() { return sma50; }
        public BigDecimal getScore() { return score; }
        public String getRecommendedAction() { return recommendedAction; }
    }
    
    /**
     * Включение автоматического мониторинга
     */
    public void startAutoMonitoring(String accountId) {
        this.autoMonitoringEnabled = true;
        this.monitoredAccountId = accountId;
        String mode = investApiManager != null ? investApiManager.getCurrentMode() : null;
        log.info("Автоматический мониторинг включен для аккаунта: {} (mode={})", accountId, mode);
        botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.AUTOMATIC_TRADING, 
            "Автоматический мониторинг включен", "Аккаунт: " + accountId + (mode != null ? ", Режим: " + mode : ""));
    }
    
    /**
     * Выключение автоматического мониторинга
     */
    public void stopAutoMonitoring() {
        this.autoMonitoringEnabled = false;
        this.monitoredAccountId = null;
        log.info("Автоматический мониторинг выключен");
        botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.AUTOMATIC_TRADING, 
            "Автоматический мониторинг выключен", "");
    }
    
    /**
     * Получение статуса автоматического мониторинга
     */
    public boolean isAutoMonitoringEnabled() {
        return autoMonitoringEnabled;
    }
    
    /**
     * Автоматический мониторинг каждые 5 минут
     */
    @Scheduled(fixedRate = 300000) // 5 минут = 300000 мс
    public void autoMonitoringTask() {
        if (!autoMonitoringEnabled || monitoredAccountId == null) {
            return;
        }
        
        try {
            log.info("Запуск автоматического мониторинга для аккаунта: {}", monitoredAccountId);
            botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                "Запуск автоматического мониторинга", "Аккаунт: " + monitoredAccountId);
            
            // Анализируем рынок и выполняем торговлю
            executeAutomaticTrading(monitoredAccountId);
            
            log.info("Автоматический мониторинг завершен");
            botLogService.addLogEntry(BotLogService.LogLevel.SUCCESS, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                "Автоматический мониторинг завершен", "");
            
        } catch (Exception e) {
            log.error("Ошибка в автоматическом мониторинге: {}", e.getMessage());
            botLogService.addLogEntry(BotLogService.LogLevel.ERROR, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                "Ошибка автоматического мониторинга", e.getMessage());
        }
    }
    
    /**
     * Быстрый мониторинг каждую минуту (анализ + торговля при хороших возможностях)
     */
    @Scheduled(fixedRate = 60000) // 1 минута = 60000 мс
    public void quickMonitoringTask() {
        if (!autoMonitoringEnabled || monitoredAccountId == null) {
            return;
        }
        
        try {
            // Анализируем возможности
            List<TradingOpportunity> opportunities = findBestTradingOpportunities(monitoredAccountId);
            
            // Ищем лучшую возможность для торговли (только BUY/SELL)
            TradingOpportunity bestTradingOpportunity = null;
            for (TradingOpportunity opportunity : opportunities) {
                if ("BUY".equals(opportunity.getRecommendedAction()) || "SELL".equals(opportunity.getRecommendedAction())) {
                    if (bestTradingOpportunity == null || opportunity.getScore().compareTo(bestTradingOpportunity.getScore()) > 0) {
                        bestTradingOpportunity = opportunity;
                    }
                }
            }
            
            // Логируем найденные возможности
            if (!opportunities.isEmpty()) {
                TradingOpportunity bestOpportunity = opportunities.get(0);
                log.info("Быстрый мониторинг: лучшая возможность - {} ({}), Score: {}", 
                    bestOpportunity.getFigi(), bestOpportunity.getRecommendedAction(), bestOpportunity.getScore());
                
                botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.MARKET_ANALYSIS, 
                    "Быстрый мониторинг", String.format("Лучшая возможность: %s (%s), Score: %.1f", 
                        bestOpportunity.getFigi(), bestOpportunity.getRecommendedAction(), bestOpportunity.getScore()));
                
                // Выполняем торговлю если есть хорошая возможность для торговли
                if (bestTradingOpportunity != null && bestTradingOpportunity.getScore().compareTo(BigDecimal.valueOf(60)) > 0) {
                    log.info("Выполняем торговую операцию для {} ({}), Score: {}", 
                        bestTradingOpportunity.getFigi(), bestTradingOpportunity.getRecommendedAction(), bestTradingOpportunity.getScore());
                    
                    botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                        "Выполнение торговой операции", String.format("FIGI: %s, Действие: %s, Score: %.1f", 
                            bestTradingOpportunity.getFigi(), bestTradingOpportunity.getRecommendedAction(), bestTradingOpportunity.getScore()));
                    
                    executeTradingStrategy(monitoredAccountId, bestTradingOpportunity.getFigi());
                }
            }
            
        } catch (Exception e) {
            log.warn("Ошибка в быстром мониторинге: {}", e.getMessage());
        }
    }
} 