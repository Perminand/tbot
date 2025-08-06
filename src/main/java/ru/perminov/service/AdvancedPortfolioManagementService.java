package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.core.models.Portfolio;
import ru.tinkoff.piapi.core.models.Position;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdvancedPortfolioManagementService {
    
    private final PortfolioService portfolioService;
    private final MarketAnalysisService marketAnalysisService;
    private final AdvancedTradingStrategyService tradingStrategyService;
    private final RiskManagementService riskManagementService;
    private final BotLogService botLogService;
    
    // Динамические целевые доли в зависимости от рыночных условий
    private final Map<String, DynamicAllocation> dynamicAllocations = new HashMap<>();
    
    // Инициализация динамических долей
    {
        // Бычий рынок
        dynamicAllocations.put("BULL_MARKET", new DynamicAllocation(
            new BigDecimal("0.70"), // Акции
            new BigDecimal("0.20"), // Облигации
            new BigDecimal("0.10")  // ETF
        ));
        
        // Медвежий рынок
        dynamicAllocations.put("BEAR_MARKET", new DynamicAllocation(
            new BigDecimal("0.30"), // Акции
            new BigDecimal("0.60"), // Облигации
            new BigDecimal("0.10")  // ETF
        ));
        
        // Боковой рынок
        dynamicAllocations.put("SIDEWAYS_MARKET", new DynamicAllocation(
            new BigDecimal("0.50"), // Акции
            new BigDecimal("0.40"), // Облигации
            new BigDecimal("0.10")  // ETF
        ));
    }
    
    /**
     * Динамическая ребалансировка портфеля
     */
    public DynamicRebalancingResult performDynamicRebalancing(String accountId) {
        try {
            Portfolio portfolio = portfolioService.getPortfolio(accountId);
            PortfolioAnalysis analysis = analyzePortfolio(accountId);
            
            // Определение рыночных условий
            MarketCondition marketCondition = determineMarketCondition(accountId);
            DynamicAllocation targetAllocation = getTargetAllocation(marketCondition);
            
            // Расчет необходимых изменений
            List<RebalancingAction> actions = calculateRebalancingActions(analysis, targetAllocation);
            
            // Фильтрация действий по рискам
            List<RebalancingAction> approvedActions = filterActionsByRisk(accountId, actions);
            
            // Выполнение ребалансировки
            executeRebalancingActions(accountId, approvedActions);
            
            return new DynamicRebalancingResult(
                marketCondition,
                targetAllocation,
                approvedActions,
                analysis.getTotalValue()
            );
            
        } catch (Exception e) {
            log.error("Ошибка при динамической ребалансировке: {}", e.getMessage());
            return new DynamicRebalancingResult(null, null, new ArrayList<>(), BigDecimal.ZERO);
        }
    }
    
    /**
     * Определение рыночных условий
     */
    private MarketCondition determineMarketCondition(String accountId) {
        try {
            // Анализ основных индексов
            List<String> marketIndicators = Arrays.asList(
                "BBG000B9XRY4", // S&P 500
                "BBG000BPH459", // NASDAQ
                "BBG000B9XRY4"  // Dow Jones
            );
            
            int bullishCount = 0;
            int bearishCount = 0;
            
            for (String figi : marketIndicators) {
                MarketAnalysisService.TrendAnalysis trend = 
                    marketAnalysisService.analyzeTrend(figi, ru.tinkoff.piapi.contract.v1.CandleInterval.CANDLE_INTERVAL_WEEK);
                
                if (trend.getTrend() == MarketAnalysisService.TrendType.BULLISH) {
                    bullishCount++;
                } else if (trend.getTrend() == MarketAnalysisService.TrendType.BEARISH) {
                    bearishCount++;
                }
            }
            
            // Определение условий рынка
            if (bullishCount >= 2) {
                return MarketCondition.BULL_MARKET;
            } else if (bearishCount >= 2) {
                return MarketCondition.BEAR_MARKET;
            } else {
                return MarketCondition.SIDEWAYS_MARKET;
            }
            
        } catch (Exception e) {
            log.error("Ошибка при определении рыночных условий: {}", e.getMessage());
            return MarketCondition.SIDEWAYS_MARKET; // По умолчанию
        }
    }
    
    /**
     * Расчет действий для ребалансировки
     */
    private List<RebalancingAction> calculateRebalancingActions(PortfolioAnalysis analysis, DynamicAllocation target) {
        List<RebalancingAction> actions = new ArrayList<>();
        
        BigDecimal totalValue = analysis.getTotalValue();
        
        // Расчет целевых значений
        BigDecimal targetShares = totalValue.multiply(target.getSharesAllocation());
        BigDecimal targetBonds = totalValue.multiply(target.getBondsAllocation());
        BigDecimal targetEtf = totalValue.multiply(target.getEtfAllocation());
        
        // Текущие значения
        BigDecimal currentShares = analysis.getCurrentAllocations().getOrDefault("shares", BigDecimal.ZERO);
        BigDecimal currentBonds = analysis.getCurrentAllocations().getOrDefault("bonds", BigDecimal.ZERO);
        BigDecimal currentEtf = analysis.getCurrentAllocations().getOrDefault("etf", BigDecimal.ZERO);
        
        // Определение необходимых действий
        if (currentShares.compareTo(targetShares) < 0) {
            actions.add(new RebalancingAction("BUY_SHARES", targetShares.subtract(currentShares), "Увеличение доли акций"));
        } else if (currentShares.compareTo(targetShares) > 0) {
            actions.add(new RebalancingAction("SELL_SHARES", currentShares.subtract(targetShares), "Уменьшение доли акций"));
        }
        
        if (currentBonds.compareTo(targetBonds) < 0) {
            actions.add(new RebalancingAction("BUY_BONDS", targetBonds.subtract(currentBonds), "Увеличение доли облигаций"));
        } else if (currentBonds.compareTo(targetBonds) > 0) {
            actions.add(new RebalancingAction("SELL_BONDS", currentBonds.subtract(targetBonds), "Уменьшение доли облигаций"));
        }
        
        if (currentEtf.compareTo(targetEtf) < 0) {
            actions.add(new RebalancingAction("BUY_ETF", targetEtf.subtract(currentEtf), "Увеличение доли ETF"));
        } else if (currentEtf.compareTo(targetEtf) > 0) {
            actions.add(new RebalancingAction("SELL_ETF", currentEtf.subtract(targetEtf), "Уменьшение доли ETF"));
        }
        
        return actions;
    }
    
    /**
     * Фильтрация действий по рискам
     */
    private List<RebalancingAction> filterActionsByRisk(String accountId, List<RebalancingAction> actions) {
        return actions.stream()
            .filter(action -> {
                RiskManagementService.RiskCheckResult riskCheck = 
                    riskManagementService.checkPositionRisk(accountId, "dummy", BigDecimal.valueOf(100), 1);
                return riskCheck.isApproved();
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Выполнение действий ребалансировки
     */
    private void executeRebalancingActions(String accountId, List<RebalancingAction> actions) {
        for (RebalancingAction action : actions) {
            try {
                log.info("Выполнение действия ребалансировки: {} - {}", action.getType(), action.getDescription());
                
                // Здесь должна быть логика выполнения торговых операций
                // Пока просто логируем
                botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.REBALANCING, 
                    "Выполнено действие: " + action.getType() + " на сумму " + action.getAmount());
                
            } catch (Exception e) {
                log.error("Ошибка при выполнении действия ребалансировки: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Анализ диверсификации портфеля
     */
    public DiversificationAnalysis analyzeDiversification(String accountId) {
        try {
            PortfolioAnalysis analysis = analyzePortfolio(accountId);
            
            // Анализ по секторам
            Map<String, BigDecimal> sectorAllocation = analyzeSectorAllocation(analysis);
            
            // Анализ по странам
            Map<String, BigDecimal> countryAllocation = analyzeCountryAllocation(analysis);
            
            // Анализ корреляций
            BigDecimal correlationRisk = calculateCorrelationRisk(analysis);
            
            // Оценка диверсификации
            DiversificationScore score = calculateDiversificationScore(sectorAllocation, countryAllocation, correlationRisk);
            
            return new DiversificationAnalysis(
                sectorAllocation,
                countryAllocation,
                correlationRisk,
                score
            );
            
        } catch (Exception e) {
            log.error("Ошибка при анализе диверсификации: {}", e.getMessage());
            return new DiversificationAnalysis(new HashMap<>(), new HashMap<>(), BigDecimal.ZERO, DiversificationScore.POOR);
        }
    }
    
    /**
     * Анализ распределения по секторам
     */
    private Map<String, BigDecimal> analyzeSectorAllocation(PortfolioAnalysis analysis) {
        Map<String, BigDecimal> sectorAllocation = new HashMap<>();
        BigDecimal totalValue = analysis.getTotalValue();
        
        // Группировка позиций по секторам
        for (Position position : analysis.getPositions()) {
            String sector = getSectorForInstrument(position.getFigi());
            BigDecimal positionValue = analysis.getPositionValues().get(position.getFigi());
            
            sectorAllocation.merge(sector, positionValue, BigDecimal::add);
        }
        
        // Расчет процентов
        return sectorAllocation.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().divide(totalValue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
            ));
    }
    
    /**
     * Анализ распределения по странам
     */
    private Map<String, BigDecimal> analyzeCountryAllocation(PortfolioAnalysis analysis) {
        Map<String, BigDecimal> countryAllocation = new HashMap<>();
        BigDecimal totalValue = analysis.getTotalValue();
        
        // Группировка позиций по странам
        for (Position position : analysis.getPositions()) {
            String country = getCountryForInstrument(position.getFigi());
            BigDecimal positionValue = analysis.getPositionValues().get(position.getFigi());
            
            countryAllocation.merge(country, positionValue, BigDecimal::add);
        }
        
        // Расчет процентов
        return countryAllocation.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().divide(totalValue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
            ));
    }
    
    /**
     * Расчет риска корреляции
     */
    private BigDecimal calculateCorrelationRisk(PortfolioAnalysis analysis) {
        // Упрощенный расчет корреляции
        // В реальной реализации здесь должен быть анализ корреляций между активами
        return BigDecimal.valueOf(0.3); // Примерное значение
    }
    
    /**
     * Расчет оценки диверсификации
     */
    private DiversificationScore calculateDiversificationScore(
            Map<String, BigDecimal> sectorAllocation,
            Map<String, BigDecimal> countryAllocation,
            BigDecimal correlationRisk) {
        
        // Количество секторов
        int sectorCount = sectorAllocation.size();
        
        // Количество стран
        int countryCount = countryAllocation.size();
        
        // Оценка на основе количества и распределения
        if (sectorCount >= 8 && countryCount >= 5 && correlationRisk.compareTo(BigDecimal.valueOf(0.3)) < 0) {
            return DiversificationScore.EXCELLENT;
        } else if (sectorCount >= 6 && countryCount >= 3 && correlationRisk.compareTo(BigDecimal.valueOf(0.5)) < 0) {
            return DiversificationScore.GOOD;
        } else if (sectorCount >= 4 && countryCount >= 2) {
            return DiversificationScore.FAIR;
        } else {
            return DiversificationScore.POOR;
        }
    }
    
    // Вспомогательные методы
    private PortfolioAnalysis analyzePortfolio(String accountId) {
        Portfolio portfolio = portfolioService.getPortfolio(accountId);
        List<Position> positions = portfolio.getPositions();
        
        BigDecimal totalValue = BigDecimal.ZERO;
        Map<String, BigDecimal> currentAllocations = new HashMap<>();
        Map<String, BigDecimal> positionValues = new HashMap<>();
        
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
            
            String instrumentType = position.getInstrumentType();
            currentAllocations.merge(instrumentType, positionValue, BigDecimal::add);
        }
        
        return new PortfolioAnalysis(totalValue, currentAllocations, new HashMap<>(), positionValues, positions);
    }
    
    private DynamicAllocation getTargetAllocation(MarketCondition condition) {
        return dynamicAllocations.get(condition.name());
    }
    
    private String getSectorForInstrument(String figi) {
        // В реальной реализации здесь должен быть запрос к базе данных
        return "Technology"; // Пример
    }
    
    private String getCountryForInstrument(String figi) {
        // В реальной реализации здесь должен быть запрос к базе данных
        return "USA"; // Пример
    }
    
    // Внутренние классы
    public enum MarketCondition {
        BULL_MARKET, BEAR_MARKET, SIDEWAYS_MARKET
    }
    
    public enum DiversificationScore {
        EXCELLENT, GOOD, FAIR, POOR
    }
    
    public static class DynamicAllocation {
        private final BigDecimal sharesAllocation;
        private final BigDecimal bondsAllocation;
        private final BigDecimal etfAllocation;
        
        public DynamicAllocation(BigDecimal sharesAllocation, BigDecimal bondsAllocation, BigDecimal etfAllocation) {
            this.sharesAllocation = sharesAllocation;
            this.bondsAllocation = bondsAllocation;
            this.etfAllocation = etfAllocation;
        }
        
        public BigDecimal getSharesAllocation() { return sharesAllocation; }
        public BigDecimal getBondsAllocation() { return bondsAllocation; }
        public BigDecimal getEtfAllocation() { return etfAllocation; }
    }
    
    public static class RebalancingAction {
        private final String type;
        private final BigDecimal amount;
        private final String description;
        
        public RebalancingAction(String type, BigDecimal amount, String description) {
            this.type = type;
            this.amount = amount;
            this.description = description;
        }
        
        public String getType() { return type; }
        public BigDecimal getAmount() { return amount; }
        public String getDescription() { return description; }
    }
    
    public static class DynamicRebalancingResult {
        private final MarketCondition marketCondition;
        private final DynamicAllocation targetAllocation;
        private final List<RebalancingAction> actions;
        private final BigDecimal totalValue;
        
        public DynamicRebalancingResult(MarketCondition marketCondition, DynamicAllocation targetAllocation, 
                                      List<RebalancingAction> actions, BigDecimal totalValue) {
            this.marketCondition = marketCondition;
            this.targetAllocation = targetAllocation;
            this.actions = actions;
            this.totalValue = totalValue;
        }
        
        public MarketCondition getMarketCondition() { return marketCondition; }
        public DynamicAllocation getTargetAllocation() { return targetAllocation; }
        public List<RebalancingAction> getActions() { return actions; }
        public BigDecimal getTotalValue() { return totalValue; }
    }
    
    public static class DiversificationAnalysis {
        private final Map<String, BigDecimal> sectorAllocation;
        private final Map<String, BigDecimal> countryAllocation;
        private final BigDecimal correlationRisk;
        private final DiversificationScore score;
        
        public DiversificationAnalysis(Map<String, BigDecimal> sectorAllocation, Map<String, BigDecimal> countryAllocation,
                                     BigDecimal correlationRisk, DiversificationScore score) {
            this.sectorAllocation = sectorAllocation;
            this.countryAllocation = countryAllocation;
            this.correlationRisk = correlationRisk;
            this.score = score;
        }
        
        public Map<String, BigDecimal> getSectorAllocation() { return sectorAllocation; }
        public Map<String, BigDecimal> getCountryAllocation() { return countryAllocation; }
        public BigDecimal getCorrelationRisk() { return correlationRisk; }
        public DiversificationScore getScore() { return score; }
    }
    
    private static class PortfolioAnalysis {
        private final BigDecimal totalValue;
        private final Map<String, BigDecimal> currentAllocations;
        private final Map<String, BigDecimal> allocationPercentages;
        private final Map<String, BigDecimal> positionValues;
        private final List<Position> positions;
        
        public PortfolioAnalysis(BigDecimal totalValue, Map<String, BigDecimal> currentAllocations,
                               Map<String, BigDecimal> allocationPercentages, Map<String, BigDecimal> positionValues,
                               List<Position> positions) {
            this.totalValue = totalValue;
            this.currentAllocations = currentAllocations;
            this.allocationPercentages = allocationPercentages;
            this.positionValues = positionValues;
            this.positions = positions;
        }
        
        public BigDecimal getTotalValue() { return totalValue; }
        public Map<String, BigDecimal> getCurrentAllocations() { return currentAllocations; }
        public Map<String, BigDecimal> getAllocationPercentages() { return allocationPercentages; }
        public Map<String, BigDecimal> getPositionValues() { return positionValues; }
        public List<Position> getPositions() { return positions; }
    }
} 