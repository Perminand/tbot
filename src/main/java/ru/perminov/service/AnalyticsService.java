package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {
    
    /**
     * Анализ производительности портфеля
     */
    public PortfolioPerformance analyzePortfolioPerformance(BigDecimal currentValue, BigDecimal initialValue, 
                                                          List<BigDecimal> dailyValues) {
        
        PortfolioPerformance performance = new PortfolioPerformance();
        
        // Общая доходность
        BigDecimal totalReturn = currentValue.subtract(initialValue);
        BigDecimal totalReturnPercent = totalReturn.divide(initialValue, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
        
        performance.setTotalReturn(totalReturn);
        performance.setTotalReturnPercent(totalReturnPercent);
        performance.setCurrentValue(currentValue);
        performance.setInitialValue(initialValue);
        
        // Расчет волатильности
        if (dailyValues.size() > 1) {
            BigDecimal volatility = calculateVolatility(dailyValues);
            performance.setVolatility(volatility);
        }
        
        // Расчет максимальной просадки
        BigDecimal maxDrawdown = calculateMaxDrawdown(dailyValues);
        performance.setMaxDrawdown(maxDrawdown);
        
        // Расчет коэффициента Шарпа (упрощенный)
        BigDecimal sharpeRatio = calculateSharpeRatio(dailyValues);
        performance.setSharpeRatio(sharpeRatio);
        
        return performance;
    }
    
    /**
     * Анализ торговых операций
     */
    public TradingAnalytics analyzeTradingOperations(List<TradeOperation> operations) {
        
        TradingAnalytics analytics = new TradingAnalytics();
        
        if (operations.isEmpty()) {
            return analytics;
        }
        
        // Общая статистика
        analytics.setTotalTrades(operations.size());
        
        // Прибыльные и убыточные сделки
        long profitableTrades = operations.stream()
            .filter(op -> op.getPnL().compareTo(BigDecimal.ZERO) > 0)
            .count();
        
        long losingTrades = operations.stream()
            .filter(op -> op.getPnL().compareTo(BigDecimal.ZERO) < 0)
            .count();
        
        analytics.setProfitableTrades((int) profitableTrades);
        analytics.setLosingTrades((int) losingTrades);
        
        // Винрейт
        BigDecimal winRate = BigDecimal.valueOf(profitableTrades)
            .divide(BigDecimal.valueOf(operations.size()), 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
        analytics.setWinRate(winRate);
        
        // Общий P&L
        BigDecimal totalPnL = operations.stream()
            .map(TradeOperation::getPnL)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        analytics.setTotalPnL(totalPnL);
        
        // Средняя прибыль и убыток
        BigDecimal avgProfit = operations.stream()
            .filter(op -> op.getPnL().compareTo(BigDecimal.ZERO) > 0)
            .map(TradeOperation::getPnL)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(profitableTrades > 0 ? profitableTrades : 1), 2, RoundingMode.HALF_UP);
        
        BigDecimal avgLoss = operations.stream()
            .filter(op -> op.getPnL().compareTo(BigDecimal.ZERO) < 0)
            .map(TradeOperation::getPnL)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(losingTrades > 0 ? losingTrades : 1), 2, RoundingMode.HALF_UP);
        
        analytics.setAverageProfit(avgProfit);
        analytics.setAverageLoss(avgLoss);
        
        // Соотношение риск/прибыль
        if (avgLoss.compareTo(BigDecimal.ZERO) != 0) {
            BigDecimal riskRewardRatio = avgProfit.divide(avgLoss.abs(), 2, RoundingMode.HALF_UP);
            analytics.setRiskRewardRatio(riskRewardRatio);
        }
        
        return analytics;
    }
    
    /**
     * Анализ по инструментам
     */
    public Map<String, InstrumentAnalytics> analyzeInstruments(List<TradeOperation> operations) {
        
        Map<String, InstrumentAnalytics> instrumentAnalytics = new HashMap<>();
        
        // Группируем операции по инструментам
        Map<String, List<TradeOperation>> operationsByInstrument = operations.stream()
            .collect(java.util.stream.Collectors.groupingBy(TradeOperation::getFigi));
        
        for (Map.Entry<String, List<TradeOperation>> entry : operationsByInstrument.entrySet()) {
            String figi = entry.getKey();
            List<TradeOperation> instrumentOperations = entry.getValue();
            
            InstrumentAnalytics analytics = new InstrumentAnalytics();
            analytics.setFigi(figi);
            analytics.setTotalTrades(instrumentOperations.size());
            
            // P&L по инструменту
            BigDecimal totalPnL = instrumentOperations.stream()
                .map(TradeOperation::getPnL)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            analytics.setTotalPnL(totalPnL);
            
            // Винрейт по инструменту
            long profitableTrades = instrumentOperations.stream()
                .filter(op -> op.getPnL().compareTo(BigDecimal.ZERO) > 0)
                .count();
            
            BigDecimal winRate = BigDecimal.valueOf(profitableTrades)
                .divide(BigDecimal.valueOf(instrumentOperations.size()), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
            analytics.setWinRate(winRate);
            
            instrumentAnalytics.put(figi, analytics);
        }
        
        return instrumentAnalytics;
    }
    
    /**
     * Расчет волатильности
     */
    private BigDecimal calculateVolatility(List<BigDecimal> values) {
        if (values.size() < 2) {
            return BigDecimal.ZERO;
        }
        
        // Среднее значение
        BigDecimal mean = values.stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP);
        
        // Дисперсия
        BigDecimal variance = values.stream()
            .map(value -> value.subtract(mean).pow(2))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP);
        
        // Стандартное отклонение (волатильность)
        return BigDecimal.valueOf(Math.sqrt(variance.doubleValue()));
    }
    
    /**
     * Расчет максимальной просадки
     */
    private BigDecimal calculateMaxDrawdown(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        BigDecimal peak = values.get(0);
        
        for (BigDecimal value : values) {
            if (value.compareTo(peak) > 0) {
                peak = value;
            }
            
            BigDecimal drawdown = peak.subtract(value).divide(peak, 4, RoundingMode.HALF_UP);
            if (drawdown.compareTo(maxDrawdown) > 0) {
                maxDrawdown = drawdown;
            }
        }
        
        return maxDrawdown.multiply(BigDecimal.valueOf(100)); // В процентах
    }
    
    /**
     * Расчет коэффициента Шарпа (упрощенный)
     */
    private BigDecimal calculateSharpeRatio(List<BigDecimal> values) {
        if (values.size() < 2) {
            return BigDecimal.ZERO;
        }
        
        // Средняя доходность
        BigDecimal meanReturn = values.stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP);
        
        // Волатильность
        BigDecimal volatility = calculateVolatility(values);
        
        if (volatility.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        // Коэффициент Шарпа = (доходность - безрисковая ставка) / волатильность
        // Безрисковая ставка = 0 для упрощения
        return meanReturn.divide(volatility, 4, RoundingMode.HALF_UP);
    }
    
    /**
     * Модель производительности портфеля
     */
    public static class PortfolioPerformance {
        private BigDecimal totalReturn;
        private BigDecimal totalReturnPercent;
        private BigDecimal currentValue;
        private BigDecimal initialValue;
        private BigDecimal volatility;
        private BigDecimal maxDrawdown;
        private BigDecimal sharpeRatio;
        
        // Getters and setters
        public BigDecimal getTotalReturn() { return totalReturn; }
        public void setTotalReturn(BigDecimal totalReturn) { this.totalReturn = totalReturn; }
        
        public BigDecimal getTotalReturnPercent() { return totalReturnPercent; }
        public void setTotalReturnPercent(BigDecimal totalReturnPercent) { this.totalReturnPercent = totalReturnPercent; }
        
        public BigDecimal getCurrentValue() { return currentValue; }
        public void setCurrentValue(BigDecimal currentValue) { this.currentValue = currentValue; }
        
        public BigDecimal getInitialValue() { return initialValue; }
        public void setInitialValue(BigDecimal initialValue) { this.initialValue = initialValue; }
        
        public BigDecimal getVolatility() { return volatility; }
        public void setVolatility(BigDecimal volatility) { this.volatility = volatility; }
        
        public BigDecimal getMaxDrawdown() { return maxDrawdown; }
        public void setMaxDrawdown(BigDecimal maxDrawdown) { this.maxDrawdown = maxDrawdown; }
        
        public BigDecimal getSharpeRatio() { return sharpeRatio; }
        public void setSharpeRatio(BigDecimal sharpeRatio) { this.sharpeRatio = sharpeRatio; }
    }
    
    /**
     * Модель торговой аналитики
     */
    public static class TradingAnalytics {
        private int totalTrades;
        private int profitableTrades;
        private int losingTrades;
        private BigDecimal winRate;
        private BigDecimal totalPnL;
        private BigDecimal averageProfit;
        private BigDecimal averageLoss;
        private BigDecimal riskRewardRatio;
        
        // Getters and setters
        public int getTotalTrades() { return totalTrades; }
        public void setTotalTrades(int totalTrades) { this.totalTrades = totalTrades; }
        
        public int getProfitableTrades() { return profitableTrades; }
        public void setProfitableTrades(int profitableTrades) { this.profitableTrades = profitableTrades; }
        
        public int getLosingTrades() { return losingTrades; }
        public void setLosingTrades(int losingTrades) { this.losingTrades = losingTrades; }
        
        public BigDecimal getWinRate() { return winRate; }
        public void setWinRate(BigDecimal winRate) { this.winRate = winRate; }
        
        public BigDecimal getTotalPnL() { return totalPnL; }
        public void setTotalPnL(BigDecimal totalPnL) { this.totalPnL = totalPnL; }
        
        public BigDecimal getAverageProfit() { return averageProfit; }
        public void setAverageProfit(BigDecimal averageProfit) { this.averageProfit = averageProfit; }
        
        public BigDecimal getAverageLoss() { return averageLoss; }
        public void setAverageLoss(BigDecimal averageLoss) { this.averageLoss = averageLoss; }
        
        public BigDecimal getRiskRewardRatio() { return riskRewardRatio; }
        public void setRiskRewardRatio(BigDecimal riskRewardRatio) { this.riskRewardRatio = riskRewardRatio; }
    }
    
    /**
     * Модель аналитики по инструменту
     */
    public static class InstrumentAnalytics {
        private String figi;
        private int totalTrades;
        private BigDecimal totalPnL;
        private BigDecimal winRate;
        
        // Getters and setters
        public String getFigi() { return figi; }
        public void setFigi(String figi) { this.figi = figi; }
        
        public int getTotalTrades() { return totalTrades; }
        public void setTotalTrades(int totalTrades) { this.totalTrades = totalTrades; }
        
        public BigDecimal getTotalPnL() { return totalPnL; }
        public void setTotalPnL(BigDecimal totalPnL) { this.totalPnL = totalPnL; }
        
        public BigDecimal getWinRate() { return winRate; }
        public void setWinRate(BigDecimal winRate) { this.winRate = winRate; }
    }
    
    /**
     * Модель торговой операции
     */
    public static class TradeOperation {
        private String figi;
        private String action; // BUY/SELL
        private int lots;
        private BigDecimal price;
        private BigDecimal pnl;
        private LocalDateTime timestamp;
        
        // Getters and setters
        public String getFigi() { return figi; }
        public void setFigi(String figi) { this.figi = figi; }
        
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        
        public int getLots() { return lots; }
        public void setLots(int lots) { this.lots = lots; }
        
        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }
        
        public BigDecimal getPnL() { return pnl; }
        public void setPnL(BigDecimal pnl) { this.pnl = pnl; }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    }
}
