package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.perminov.dto.ShareDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RiskManagementService {
    
    // Максимальная доля одного инструмента в портфеле (5%)
    private static final BigDecimal MAX_POSITION_SIZE = new BigDecimal("0.05");
    
    // Максимальная доля одного сектора (20%)
    private static final BigDecimal MAX_SECTOR_SIZE = new BigDecimal("0.20");
    
    // Максимальный дневной убыток (2%)
    private static final BigDecimal MAX_DAILY_LOSS = new BigDecimal("0.02");
    
    // Минимальный размер позиции в рублях
    private static final BigDecimal MIN_POSITION_VALUE = new BigDecimal("10000");
    
    // Максимальный размер позиции в рублях
    private static final BigDecimal MAX_POSITION_VALUE = new BigDecimal("100000");
    
    /**
     * Проверка рисков для новой позиции
     */
    public RiskAssessment assessPositionRisk(String figi, BigDecimal quantity, BigDecimal price, 
                                           BigDecimal portfolioValue, Map<String, BigDecimal> currentPositions) {
        
        BigDecimal positionValue = quantity.multiply(price);
        BigDecimal positionShare = positionValue.divide(portfolioValue, 4, RoundingMode.HALF_UP);
        
        RiskAssessment assessment = new RiskAssessment();
        assessment.setPositionValue(positionValue);
        assessment.setPositionShare(positionShare);
            
            // Проверка размера позиции
        if (positionShare.compareTo(MAX_POSITION_SIZE) > 0) {
            assessment.addRisk("POSITION_SIZE", "Размер позиции превышает 5% портфеля: " + 
                positionShare.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP) + "%");
        }
        
        // Проверка минимального размера
        if (positionValue.compareTo(MIN_POSITION_VALUE) < 0) {
            assessment.addRisk("MIN_SIZE", "Размер позиции меньше минимального: " + positionValue + " руб.");
        }
        
        // Проверка максимального размера
        if (positionValue.compareTo(MAX_POSITION_VALUE) > 0) {
            assessment.addRisk("MAX_SIZE", "Размер позиции превышает максимальный: " + positionValue + " руб.");
        }
        
        // Проверка концентрации риска
        BigDecimal totalExposure = currentPositions.values().stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .add(positionValue);
        
        if (totalExposure.compareTo(portfolioValue.multiply(new BigDecimal("0.8"))) > 0) {
            assessment.addRisk("CONCENTRATION", "Высокая концентрация риска: " + 
                totalExposure.divide(portfolioValue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP) + "%");
        }
        
        return assessment;
    }
    
    /**
     * Расчет оптимального размера позиции
     */
    public BigDecimal calculateOptimalPositionSize(BigDecimal availableCash, BigDecimal price, 
                                                 BigDecimal portfolioValue, Map<String, BigDecimal> currentPositions) {
        
        // Базовый размер - 2% от портфеля
        BigDecimal baseSize = portfolioValue.multiply(new BigDecimal("0.02"));
        
        // Ограничиваем доступными средствами
        BigDecimal maxByCash = availableCash.multiply(new BigDecimal("0.8")); // Используем 80% доступных средств
        
        // Ограничиваем максимальным размером позиции
        BigDecimal maxByPosition = MAX_POSITION_VALUE;
        
        // Выбираем минимальное из ограничений
        BigDecimal optimalSize = baseSize.min(maxByCash).min(maxByPosition);
        
        // Конвертируем в количество лотов
        BigDecimal lots = optimalSize.divide(price, 0, RoundingMode.DOWN);
        
        // Минимальный размер лота
        if (lots.compareTo(BigDecimal.ONE) < 0) {
            lots = BigDecimal.ONE;
        }
        
        return lots;
    }
    
    /**
     * Проверка дневного лимита убытков
     */
    public boolean checkDailyLossLimit(BigDecimal dailyPnL, BigDecimal portfolioValue) {
        BigDecimal lossPercentage = dailyPnL.divide(portfolioValue, 4, RoundingMode.HALF_UP).abs();
        return lossPercentage.compareTo(MAX_DAILY_LOSS) < 0;
    }
    
    /**
     * Получение рекомендаций по рискам
     */
    public RiskRecommendation getRiskRecommendation(String figi) {
        RiskRecommendation recommendation = new RiskRecommendation();
        recommendation.setFigi(figi);
        recommendation.setMaxPositionSize(MAX_POSITION_VALUE);
        recommendation.setMaxPositionShare(MAX_POSITION_SIZE.multiply(BigDecimal.valueOf(100)));
        recommendation.setRecommendation("Соблюдайте лимиты позиций и диверсифицируйте портфель");
        return recommendation;
    }
    
    /**
     * Проверка риска позиции
     */
    public RiskCheckResult checkPositionRisk(String figi, String accountId, BigDecimal price, int lots) {
        RiskCheckResult result = new RiskCheckResult();
        result.setFigi(figi);
        result.setPrice(price);
        result.setLots(lots);
        result.setPositionValue(price.multiply(BigDecimal.valueOf(lots)));
        
        // Простая проверка - если позиция больше 100,000 рублей, считаем рискованным
        if (result.getPositionValue().compareTo(MAX_POSITION_VALUE) > 0) {
            result.setRiskLevel("HIGH");
            result.setRiskDescription("Позиция превышает максимальный размер");
        } else if (result.getPositionValue().compareTo(MIN_POSITION_VALUE) < 0) {
            result.setRiskLevel("LOW");
            result.setRiskDescription("Позиция меньше минимального размера");
        } else {
            result.setRiskLevel("MEDIUM");
            result.setRiskDescription("Позиция в допустимых пределах");
        }
        
        return result;
    }
    
    /**
     * Расчет стоп-лосса и тейк-профита
     */
    public StopLossTakeProfit calculateStopLossTakeProfit(String figi, BigDecimal currentPrice, String trend) {
        StopLossTakeProfit sltp = new StopLossTakeProfit();
        sltp.setFigi(figi);
        sltp.setCurrentPrice(currentPrice);
        
        // Простой расчет: стоп-лосс 5% ниже, тейк-профит 10% выше
        BigDecimal stopLossPercent = new BigDecimal("0.05");
        BigDecimal takeProfitPercent = new BigDecimal("0.10");
        
        sltp.setStopLoss(currentPrice.multiply(BigDecimal.ONE.subtract(stopLossPercent)));
        sltp.setTakeProfit(currentPrice.multiply(BigDecimal.ONE.add(takeProfitPercent)));
        
        return sltp;
    }
    
    /**
     * Оценка рисков
     */
    public static class RiskAssessment {
        private BigDecimal positionValue;
        private BigDecimal positionShare;
        private final Map<String, String> risks = new HashMap<>();
        private boolean isAcceptable = true;
        
        public void addRisk(String type, String description) {
            risks.put(type, description);
            isAcceptable = false;
        }
        
        public boolean isAcceptable() {
            return isAcceptable;
        }
        
        public Map<String, String> getRisks() {
            return risks;
        }
        
        // Getters and setters
        public BigDecimal getPositionValue() { return positionValue; }
        public void setPositionValue(BigDecimal positionValue) { this.positionValue = positionValue; }
        
        public BigDecimal getPositionShare() { return positionShare; }
        public void setPositionShare(BigDecimal positionShare) { this.positionShare = positionShare; }
    }
    
    /**
     * Рекомендации по рискам
     */
    public static class RiskRecommendation {
        private String figi;
        private BigDecimal maxPositionSize;
        private BigDecimal maxPositionShare;
        private String recommendation;
        private String action = "HOLD";
        private BigDecimal drawdown = BigDecimal.ZERO;
        private BigDecimal dailyLoss = BigDecimal.ZERO;
        
        // Getters and setters
        public String getFigi() { return figi; }
        public void setFigi(String figi) { this.figi = figi; }
        
        public BigDecimal getMaxPositionSize() { return maxPositionSize; }
        public void setMaxPositionSize(BigDecimal maxPositionSize) { this.maxPositionSize = maxPositionSize; }
        
        public BigDecimal getMaxPositionShare() { return maxPositionShare; }
        public void setMaxPositionShare(BigDecimal maxPositionShare) { this.maxPositionShare = maxPositionShare; }
        
        public String getRecommendation() { return recommendation; }
        public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
        
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        
        public BigDecimal getDrawdown() { return drawdown; }
        public void setDrawdown(BigDecimal drawdown) { this.drawdown = drawdown; }
        
        public BigDecimal getDailyLoss() { return dailyLoss; }
        public void setDailyLoss(BigDecimal dailyLoss) { this.dailyLoss = dailyLoss; }
    }
    
    /**
     * Результат проверки риска
     */
    public static class RiskCheckResult {
        private String figi;
        private BigDecimal price;
        private int lots;
        private BigDecimal positionValue;
        private String riskLevel;
        private String riskDescription;
        private boolean approved = true;
        private String reason = "Позиция одобрена";
        
        // Getters and setters
        public String getFigi() { return figi; }
        public void setFigi(String figi) { this.figi = figi; }
        
        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }
        
        public int getLots() { return lots; }
        public void setLots(int lots) { this.lots = lots; }
        
        public BigDecimal getPositionValue() { return positionValue; }
        public void setPositionValue(BigDecimal positionValue) { this.positionValue = positionValue; }
        
        public String getRiskLevel() { return riskLevel; }
        public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
        
        public String getRiskDescription() { return riskDescription; }
        public void setRiskDescription(String riskDescription) { this.riskDescription = riskDescription; }
        
        public boolean isApproved() { return approved; }
        public void setApproved(boolean approved) { this.approved = approved; }
        
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
    
    /**
     * Стоп-лосс и тейк-профит
     */
    public static class StopLossTakeProfit {
        private String figi;
        private BigDecimal currentPrice;
        private BigDecimal stopLoss;
        private BigDecimal takeProfit;
        
        // Getters and setters
        public String getFigi() { return figi; }
        public void setFigi(String figi) { this.figi = figi; }
        
        public BigDecimal getCurrentPrice() { return currentPrice; }
        public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }
        
        public BigDecimal getStopLoss() { return stopLoss; }
        public void setStopLoss(BigDecimal stopLoss) { this.stopLoss = stopLoss; }
        
        public BigDecimal getTakeProfit() { return takeProfit; }
        public void setTakeProfit(BigDecimal takeProfit) { this.takeProfit = takeProfit; }
    }
} 