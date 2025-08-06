package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.core.models.Portfolio;
import ru.tinkoff.piapi.core.models.Position;
import ru.tinkoff.piapi.contract.v1.OrderDirection;
import ru.tinkoff.piapi.contract.v1.MoneyValue;
import ru.tinkoff.piapi.contract.v1.Quotation;
import ru.tinkoff.piapi.contract.v1.Share;
import ru.tinkoff.piapi.contract.v1.Bond;
import ru.tinkoff.piapi.contract.v1.Etf;
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
    private final InstrumentService instrumentService;
    
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
                    // Извлекаем цену из строкового представления объекта
                    String priceStr = position.getCurrentPrice().toString();
                    log.debug("Price string for {}: {}", position.getFigi(), priceStr);
                    
                    // Ищем числовое значение в строке
                    if (priceStr.contains("value=")) {
                        String valuePart = priceStr.substring(priceStr.indexOf("value=") + 6);
                        valuePart = valuePart.substring(0, valuePart.indexOf(","));
                        currentPrice = new BigDecimal(valuePart);
                    } else {
                        // Попробуем найти любое число в строке
                        String[] parts = priceStr.split("[^0-9.]");
                        for (String part : parts) {
                            if (!part.isEmpty() && part.matches("\\d+\\.?\\d*")) {
                                currentPrice = new BigDecimal(part);
                                break;
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
            try {
                // Пытаемся получить информацию об инструменте
                Share share = instrumentService.getShareByFigi(figi);
                Bond bond = instrumentService.getBondByFigi(figi);
                Etf etf = instrumentService.getEtfByFigi(figi);
                
                if (share == null && bond == null && etf == null) {
                    log.warn("Инструмент {} не найден или недоступен для торговли", figi);
                    botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT, 
                        "Инструмент недоступен", "FIGI: " + figi + " - не найден или недоступен для торговли");
                    return;
                }
            } catch (Exception e) {
                log.warn("Ошибка проверки доступности инструмента {}: {}", figi, e.getMessage());
                botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT, 
                    "Ошибка проверки инструмента", "FIGI: " + figi + " - " + e.getMessage());
                return;
            }
            
            // Анализ тренда
            MarketAnalysisService.TrendAnalysis trend = 
                marketAnalysisService.analyzeTrend(figi, ru.tinkoff.piapi.contract.v1.CandleInterval.CANDLE_INTERVAL_DAY);
            
            // Анализ портфеля
            PortfolioAnalysis portfolioAnalysis = analyzePortfolio(accountId);
            
            // Получаем рекомендуемое действие из анализа торговой возможности
            TradingOpportunity opportunity = analyzeTradingOpportunity(figi, accountId);
            if (opportunity == null) {
                log.warn("Не удалось проанализировать торговую возможность для {}", figi);
                return;
            }
            
            String action = opportunity.getRecommendedAction();
            log.info("Выполняем торговую операцию для {}: {}", figi, action);
            
            if ("BUY".equals(action)) {
                // Проверяем, есть ли свободные средства
                BigDecimal availableCash = getAvailableCash(portfolioAnalysis);
                log.info("Доступные средства для покупки: {}", availableCash);
                
                if (availableCash.compareTo(BigDecimal.ZERO) > 0) {
                    // Проверяем, есть ли уже позиция по этому инструменту
                    boolean hasPosition = portfolioAnalysis.getPositionValues().containsKey(figi) && 
                                        portfolioAnalysis.getPositionValues().get(figi).compareTo(BigDecimal.ZERO) > 0;
                    
                    // Определяем размер покупки в зависимости от наличия позиции
                    BigDecimal buyAmount;
                    if (hasPosition) {
                        // Докупаем - используем меньшую сумму (2% от доступных средств)
                        buyAmount = availableCash.multiply(BigDecimal.valueOf(0.02));
                        log.info("Докупаем позицию по {}: {} лотов", figi, buyAmount.divide(trend.getCurrentPrice(), 0, RoundingMode.DOWN));
                    } else {
                        // Первая покупка - используем меньшую сумму (5% от доступных средств)
                        buyAmount = availableCash.multiply(BigDecimal.valueOf(0.05));
                        log.info("Первая покупка {}: {} лотов", figi, buyAmount.divide(trend.getCurrentPrice(), 0, RoundingMode.DOWN));
                    }
                    
                    int lots = buyAmount.divide(trend.getCurrentPrice(), 0, RoundingMode.DOWN).intValue();
                    
                    if (lots > 0) {
                        String actionType = hasPosition ? "докупка" : "покупка";
                        log.info("Размещение ордера на {}: {} лотов по цене {}", actionType, lots, trend.getCurrentPrice());
                        botLogService.addLogEntry(BotLogService.LogLevel.TRADE, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                            "Размещение ордера на " + actionType, String.format("FIGI: %s, Лотов: %d, Цена: %.2f", 
                                figi, lots, trend.getCurrentPrice()));
                        
                        // Размещаем реальный ордер
                        try {
                            orderService.placeMarketOrder(figi, lots, OrderDirection.ORDER_DIRECTION_BUY, accountId);
                            botLogService.addLogEntry(BotLogService.LogLevel.SUCCESS, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                                "Ордер на " + actionType + " размещен", String.format("FIGI: %s, Лотов: %d", figi, lots));
                        } catch (Exception e) {
                            log.error("Ошибка размещения ордера на {}: {}", actionType, e.getMessage());
                            botLogService.addLogEntry(BotLogService.LogLevel.ERROR, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                                "Ошибка размещения ордера на " + actionType, e.getMessage());
                        }
                    } else {
                        log.warn("Недостаточно средств для покупки лотов");
                        botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT, 
                            "Недостаточно средств для покупки", "Сумма: " + buyAmount + ", Цена: " + trend.getCurrentPrice());
                    }
                } else {
                    log.warn("Нет свободных средств для покупки");
                    botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT, 
                        "Нет свободных средств", "Доступно: " + availableCash);
                }
            } else if ("SELL".equals(action)) {
                // Проверяем, есть ли позиция по этому инструменту
                BigDecimal positionValue = portfolioAnalysis.getPositionValues().get(figi);
                if (positionValue != null && positionValue.compareTo(BigDecimal.ZERO) > 0) {
                    // Находим позицию для получения количества лотов
                    Position position = portfolioAnalysis.getPositions().stream()
                        .filter(p -> p.getFigi().equals(figi))
                        .findFirst()
                        .orElse(null);
                    
                    if (position != null && position.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
                        int lots = position.getQuantity().intValue();
                        log.info("Размещение ордера на продажу: {} лотов по цене {}", lots, trend.getCurrentPrice());
                        
                        botLogService.addLogEntry(BotLogService.LogLevel.TRADE, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                            "Размещение ордера на продажу", String.format("FIGI: %s, Лотов: %d, Цена: %.2f", 
                                figi, lots, trend.getCurrentPrice()));
                        
                        // Размещаем реальный ордер
                        try {
                            orderService.placeMarketOrder(figi, lots, OrderDirection.ORDER_DIRECTION_SELL, accountId);
                            botLogService.addLogEntry(BotLogService.LogLevel.SUCCESS, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                                "Ордер на продажу размещен", String.format("FIGI: %s, Лотов: %d", figi, lots));
                        } catch (Exception e) {
                            log.error("Ошибка размещения ордера на продажу: {}", e.getMessage());
                            botLogService.addLogEntry(BotLogService.LogLevel.ERROR, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                                "Ошибка размещения ордера на продажу", e.getMessage());
                        }
                    } else {
                        log.warn("Нет позиции для продажи по инструменту {}", figi);
                        botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT, 
                            "Нет позиции для продажи", "FIGI: " + figi);
                    }
                } else {
                    log.warn("Нет позиции для продажи по инструменту {}", figi);
                    botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT, 
                        "Нет позиции для продажи", "FIGI: " + figi);
                }
            } else {
                log.info("Действие HOLD - никаких операций не выполняем");
                botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                    "Действие HOLD", "FIGI: " + figi + " - никаких операций не выполняем");
            }
            
        } catch (Exception e) {
            log.error("Ошибка при выполнении торговой стратегии: {}", e.getMessage());
            botLogService.addLogEntry(BotLogService.LogLevel.ERROR, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                "Ошибка выполнения торговой стратегии", e.getMessage());
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
            log.info("Поиск лучших торговых возможностей для аккаунта: {}", accountId);
            botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.TRADING_STRATEGY, 
                "Начало поиска торговых возможностей", "Аккаунт: " + accountId);
            
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
                
                // Анализируем только позиции с количеством > 0
                if (position.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
                    try {
                        TradingOpportunity opportunity = analyzeTradingOpportunity(position.getFigi(), accountId);
                        if (opportunity != null && "SELL".equals(opportunity.getRecommendedAction())) {
                            // Увеличиваем score для позиций, которые нужно продать
                            opportunity = new TradingOpportunity(
                                opportunity.getFigi(),
                                opportunity.getCurrentPrice(),
                                opportunity.getTrend(),
                                opportunity.getRsi(),
                                opportunity.getSma20(),
                                opportunity.getSma50(),
                                opportunity.getScore().add(BigDecimal.valueOf(10)), // Бонус за существующую позицию
                                "SELL"
                            );
                            sellOpportunities.add(opportunity);
                            
                            log.info("Найдена возможность продажи: {} (Score: {})", 
                                position.getFigi(), opportunity.getScore());
                            botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.PORTFOLIO_MANAGEMENT, 
                                "Найдена возможность продажи", String.format("FIGI: %s, Score: %.1f", 
                                    position.getFigi(), opportunity.getScore()));
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
                score = score.add(BigDecimal.valueOf(5));
                break;
        }
        
        // Оценка RSI
        if (rsi.compareTo(BigDecimal.valueOf(30)) < 0) {
            // Перепроданность - хорошая возможность для покупки
            score = score.add(BigDecimal.valueOf(25));
        } else if (rsi.compareTo(BigDecimal.valueOf(70)) > 0) {
            // Перекупленность - возможность для продажи
            score = score.add(BigDecimal.valueOf(20));
        } else {
            // Нейтральная зона
            score = score.add(BigDecimal.valueOf(10));
        }
        
        // Оценка SMA
        if (sma20.compareTo(sma50) > 0) {
            score = score.add(BigDecimal.valueOf(15));
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
        // Логика для принятия торговых решений с учетом возможности докупки и продажи
        
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
                return hasPosition ? "SELL" : "HOLD"; // Сильная продажа при перекупленности
            } else if (rsi.compareTo(BigDecimal.valueOf(50)) > 0) {
                return hasPosition ? "SELL" : "HOLD"; // Умеренная продажа при нисходящем тренде
            } else if (rsi.compareTo(BigDecimal.valueOf(30)) < 0) {
                return "BUY"; // Покупка при сильной перепроданности даже в нисходящем тренде
            }
        }
        
        // Для бокового тренда используем RSI
        if (rsi.compareTo(BigDecimal.valueOf(35)) < 0) {
            return "BUY"; // Докупаем при сильной перепроданности
        } else if (rsi.compareTo(BigDecimal.valueOf(65)) > 0) {
            return hasPosition ? "SELL" : "HOLD"; // Продаем только если есть позиция
        }
        
        return "HOLD";
    }
    
    /**
     * Получение доступных акций
     */
    private List<ShareDto> getAvailableShares() {
        List<ShareDto> allInstruments = new ArrayList<>();
        
        try {
            log.info("Получение всех доступных инструментов для анализа...");
            
            // Получаем акции
            List<ru.tinkoff.piapi.contract.v1.Share> shares = instrumentService.getTradableShares();
            log.info("Получено {} акций для анализа", shares.size());
            
            // Добавляем акции (ограничиваем количество для производительности)
            int maxShares = Math.min(shares.size(), 20); // Уменьшаем до 20 акций для снижения нагрузки
            int addedShares = 0;
            for (int i = 0; i < shares.size() && addedShares < maxShares; i++) {
                ru.tinkoff.piapi.contract.v1.Share share = shares.get(i);
                
                // Проверяем, что акция доступна для торговли
                if (share.getTradingStatus().name().equals("SECURITY_TRADING_STATUS_NORMAL_TRADING")) {
                    ShareDto shareDto = new ShareDto();
                    shareDto.setFigi(share.getFigi());
                    shareDto.setTicker(share.getTicker());
                    shareDto.setName(share.getName());
                    shareDto.setCurrency(share.getCurrency());
                    shareDto.setExchange(share.getExchange());
                    shareDto.setTradingStatus(share.getTradingStatus().name());
                    allInstruments.add(shareDto);
                    addedShares++;
                }
            }
            
            // Получаем облигации
            List<ru.tinkoff.piapi.contract.v1.Bond> bonds = instrumentService.getTradableBonds();
            log.info("Получено {} облигаций для анализа", bonds.size());
            
            // Добавляем облигации (ограничиваем количество)
            int maxBonds = Math.min(bonds.size(), 10); // Уменьшаем до 10 облигаций
            int addedBonds = 0;
            for (int i = 0; i < bonds.size() && addedBonds < maxBonds; i++) {
                ru.tinkoff.piapi.contract.v1.Bond bond = bonds.get(i);
                
                // Проверяем, что облигация доступна для торговли
                if (bond.getTradingStatus().name().equals("SECURITY_TRADING_STATUS_NORMAL_TRADING")) {
                    ShareDto bondDto = new ShareDto();
                    bondDto.setFigi(bond.getFigi());
                    bondDto.setTicker(bond.getTicker());
                    bondDto.setName(bond.getName());
                    bondDto.setCurrency(bond.getCurrency());
                    bondDto.setExchange(bond.getExchange());
                    bondDto.setTradingStatus(bond.getTradingStatus().name());
                    allInstruments.add(bondDto);
                    addedBonds++;
                }
            }
            
            // Получаем ETF
            List<ru.tinkoff.piapi.contract.v1.Etf> etfs = instrumentService.getTradableEtfs();
            log.info("Получено {} ETF для анализа", etfs.size());
            
            // Добавляем ETF (ограничиваем количество)
            int maxEtfs = Math.min(etfs.size(), 5); // Уменьшаем до 5 ETF
            int addedEtfs = 0;
            for (int i = 0; i < etfs.size() && addedEtfs < maxEtfs; i++) {
                ru.tinkoff.piapi.contract.v1.Etf etf = etfs.get(i);
                
                // Проверяем, что ETF доступен для торговли
                if (etf.getTradingStatus().name().equals("SECURITY_TRADING_STATUS_NORMAL_TRADING")) {
                    ShareDto etfDto = new ShareDto();
                    etfDto.setFigi(etf.getFigi());
                    etfDto.setTicker(etf.getTicker());
                    etfDto.setName(etf.getName());
                    etfDto.setCurrency(etf.getCurrency());
                    etfDto.setExchange(etf.getExchange());
                    etfDto.setTradingStatus(etf.getTradingStatus().name());
                    allInstruments.add(etfDto);
                    addedEtfs++;
                }
            }
            
            log.info("Всего инструментов для анализа: {}", allInstruments.size());
            
        } catch (Exception e) {
            log.error("Ошибка при получении инструментов: {}", e.getMessage());
            // В случае ошибки возвращаем базовый набор инструментов
            return getFallbackInstruments();
        }
        
        return allInstruments;
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
            log.info("Запуск автоматической торговли для аккаунта: {}", accountId);
            botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                "Запуск автоматической торговли", "Аккаунт: " + accountId);
            
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
                
                executeTradingStrategy(accountId, bestOpportunity.getFigi());
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
        log.info("Автоматический мониторинг включен для аккаунта: {}", accountId);
        botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.AUTOMATIC_TRADING, 
            "Автоматический мониторинг включен", "Аккаунт: " + accountId);
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