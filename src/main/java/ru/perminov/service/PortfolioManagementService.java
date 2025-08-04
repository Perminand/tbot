package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.core.models.Portfolio;
import ru.tinkoff.piapi.core.models.Position;
import ru.tinkoff.piapi.contract.v1.OrderDirection;
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
    
    // Целевые доли активов в портфеле
    private final Map<String, BigDecimal> targetAllocations = new HashMap<>();
    
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
                    currentPrice = new BigDecimal(position.getCurrentPrice().toString());
                } catch (Exception e) {
                    log.warn("Не удалось получить цену для позиции {}", position.getFigi());
                }
            }
            
            BigDecimal positionValue = quantity.multiply(currentPrice);
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
                if (availableCash.compareTo(BigDecimal.valueOf(10000)) > 0) {
                    // Покупаем на 10% от доступных средств
                    BigDecimal buyAmount = availableCash.multiply(BigDecimal.valueOf(0.1));
                    int lots = buyAmount.divide(trend.getCurrentPrice(), 0, RoundingMode.DOWN).intValue();
                    
                    if (lots > 0) {
                        log.info("Размещение ордера на покупку: {} лотов по цене {}", lots, trend.getCurrentPrice());
                        botLogService.addLogEntry(BotLogService.LogLevel.TRADE, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                            "Размещение ордера на покупку", String.format("FIGI: %s, Лотов: %d, Цена: %.2f", 
                                figi, lots, trend.getCurrentPrice()));
                        
                        // Размещаем реальный ордер
                        try {
                            orderService.placeMarketOrder(figi, lots, OrderDirection.ORDER_DIRECTION_BUY, accountId);
                            botLogService.addLogEntry(BotLogService.LogLevel.SUCCESS, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                                "Ордер на покупку размещен", String.format("FIGI: %s, Лотов: %d", figi, lots));
                        } catch (Exception e) {
                            log.error("Ошибка размещения ордера на покупку: {}", e.getMessage());
                            botLogService.addLogEntry(BotLogService.LogLevel.ERROR, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                                "Ошибка размещения ордера на покупку", e.getMessage());
                        }
                    } else {
                        log.warn("Недостаточно средств для покупки лотов");
                        botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT, 
                            "Недостаточно средств для покупки", "Сумма: " + buyAmount + ", Цена: " + trend.getCurrentPrice());
                    }
                } else {
                    log.warn("Недостаточно свободных средств для покупки");
                    botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT, 
                        "Недостаточно свободных средств", "Доступно: " + availableCash);
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
        // В реальном приложении нужно получить доступные средства из API
        return BigDecimal.valueOf(100000); // Пример
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
            
            // Получаем доступные акции
            List<ShareDto> availableShares = getAvailableShares();
            botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.MARKET_ANALYSIS, 
                "Получен список инструментов", "Количество: " + availableShares.size());
            
            // Анализируем каждый инструмент
            List<TradingOpportunity> opportunities = new ArrayList<>();
            
            for (ShareDto share : availableShares) {
                try {
                    TradingOpportunity opportunity = analyzeTradingOpportunity(share.getFigi(), accountId);
                    if (opportunity != null) {
                        opportunities.add(opportunity);
                        botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.TECHNICAL_INDICATORS, 
                            "Анализ инструмента завершен", String.format("FIGI: %s, Score: %.1f, Действие: %s", 
                                share.getFigi(), opportunity.getScore(), opportunity.getRecommendedAction()));
                    }
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
            
            // Определяем рекомендуемое действие
            String recommendedAction = determineRecommendedAction(trendAnalysis, rsi);
            
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
    private String determineRecommendedAction(MarketAnalysisService.TrendAnalysis trendAnalysis, BigDecimal rsi) {
        // Более агрессивная логика для принятия торговых решений
        if (trendAnalysis.getTrend() == MarketAnalysisService.TrendType.BULLISH) {
            if (rsi.compareTo(BigDecimal.valueOf(40)) < 0) {
                return "BUY"; // Сильная покупка при перепроданности
            } else if (rsi.compareTo(BigDecimal.valueOf(60)) < 0) {
                return "BUY"; // Умеренная покупка при восходящем тренде
            }
        } else if (trendAnalysis.getTrend() == MarketAnalysisService.TrendType.BEARISH) {
            if (rsi.compareTo(BigDecimal.valueOf(70)) > 0) {
                return "SELL"; // Сильная продажа при перекупленности
            } else if (rsi.compareTo(BigDecimal.valueOf(50)) > 0) {
                return "SELL"; // Умеренная продажа при нисходящем тренде
            }
        }
        
        // Для бокового тренда используем RSI
        if (rsi.compareTo(BigDecimal.valueOf(35)) < 0) {
            return "BUY";
        } else if (rsi.compareTo(BigDecimal.valueOf(65)) > 0) {
            return "SELL";
        }
        
        return "HOLD";
    }
    
    /**
     * Получение доступных акций
     */
    private List<ShareDto> getAvailableShares() {
        // Здесь можно добавить кэширование и фильтрацию
        // Пока возвращаем популярные акции
        List<ShareDto> shares = new ArrayList<>();
        
        ShareDto apple = new ShareDto();
        apple.setFigi("BBG000B9XRY4");
        apple.setTicker("AAPL");
        apple.setName("Apple Inc.");
        apple.setCurrency("USD");
        apple.setExchange("MOEX");
        apple.setTradingStatus("SECURITY_TRADING_STATUS_NORMAL_TRADING");
        shares.add(apple);
        
        ShareDto microsoft = new ShareDto();
        microsoft.setFigi("BBG000B9XRY5");
        microsoft.setTicker("MSFT");
        microsoft.setName("Microsoft Corporation");
        microsoft.setCurrency("USD");
        microsoft.setExchange("MOEX");
        microsoft.setTradingStatus("SECURITY_TRADING_STATUS_NORMAL_TRADING");
        shares.add(microsoft);
        
        ShareDto google = new ShareDto();
        google.setFigi("BBG000B9XRY6");
        google.setTicker("GOOGL");
        google.setName("Alphabet Inc.");
        google.setCurrency("USD");
        google.setExchange("MOEX");
        google.setTradingStatus("SECURITY_TRADING_STATUS_NORMAL_TRADING");
        shares.add(google);
        
        ShareDto tesla = new ShareDto();
        tesla.setFigi("BBG000B9XRY7");
        tesla.setTicker("TSLA");
        tesla.setName("Tesla Inc.");
        tesla.setCurrency("USD");
        tesla.setExchange("MOEX");
        tesla.setTradingStatus("SECURITY_TRADING_STATUS_NORMAL_TRADING");
        shares.add(tesla);
        
        ShareDto amazon = new ShareDto();
        amazon.setFigi("BBG000B9XRY8");
        amazon.setTicker("AMZN");
        amazon.setName("Amazon.com Inc.");
        amazon.setCurrency("USD");
        amazon.setExchange("MOEX");
        amazon.setTradingStatus("SECURITY_TRADING_STATUS_NORMAL_TRADING");
        shares.add(amazon);
        
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
            
            // Получаем лучшие торговые возможности
            List<TradingOpportunity> opportunities = findBestTradingOpportunities(accountId);
            
            if (opportunities.isEmpty()) {
                log.info("Нет подходящих торговых возможностей");
                botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                    "Нет торговых возможностей", "Подходящих инструментов не найдено");
                return;
            }
            
            // Выбираем лучшую возможность
            TradingOpportunity bestOpportunity = opportunities.get(0);
            
            if (bestOpportunity.getScore().compareTo(BigDecimal.valueOf(30)) >= 0) {
                log.info("Выполняем торговую операцию для {}: {} (Score: {})", 
                    bestOpportunity.getFigi(), bestOpportunity.getRecommendedAction(), bestOpportunity.getScore());
                
                botLogService.addLogEntry(BotLogService.LogLevel.TRADE, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                    "Выполнение торговой операции", String.format("FIGI: %s, Действие: %s, Score: %.1f, RSI: %.1f, Тренд: %s", 
                        bestOpportunity.getFigi(), bestOpportunity.getRecommendedAction(), bestOpportunity.getScore(), 
                        bestOpportunity.getRsi(), bestOpportunity.getTrend()));
                
                executeTradingStrategy(accountId, bestOpportunity.getFigi());
            } else {
                log.info("Недостаточно высокий score для торговли: {} (порог: 30)", bestOpportunity.getScore());
                botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT, 
                    "Недостаточный score для торговли", String.format("Score: %.1f < 30, FIGI: %s, Действие: %s", 
                        bestOpportunity.getScore(), bestOpportunity.getFigi(), bestOpportunity.getRecommendedAction()));
            }
            
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
} 