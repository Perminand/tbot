package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.core.models.Portfolio;
import ru.tinkoff.piapi.core.models.Position;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RiskManagementService {
    
    private final PortfolioService portfolioService;
    private final BotLogService botLogService;
    
    // Настройки риск-менеджмента
    private final BigDecimal MAX_POSITION_SIZE_PERCENT = new BigDecimal("0.05"); // 5% на позицию
    private final BigDecimal MAX_DAILY_LOSS_PERCENT = new BigDecimal("0.02"); // 2% дневной убыток
    private final BigDecimal MAX_PORTFOLIO_DRAWDOWN = new BigDecimal("0.15"); // 15% максимальная просадка
    private final BigDecimal STOP_LOSS_PERCENT = new BigDecimal("0.05"); // 5% стоп-лосс
    private final BigDecimal TAKE_PROFIT_PERCENT = new BigDecimal("0.15"); // 15% тейк-профит
    
    // Отслеживание состояния портфеля
    private final Map<String, PortfolioState> portfolioStates = new HashMap<>();
    
    /**
     * Проверка рисков перед открытием позиции
     */
    public RiskCheckResult checkPositionRisk(String accountId, String figi, BigDecimal price, int lots) {
        try {
            Portfolio portfolio = portfolioService.getPortfolio(accountId);
            BigDecimal totalValue = calculateTotalPortfolioValue(portfolio);
            BigDecimal positionValue = price.multiply(BigDecimal.valueOf(lots));
            
            // Проверка размера позиции
            BigDecimal positionSizePercent = positionValue.divide(totalValue, 4, RoundingMode.HALF_UP);
            if (positionSizePercent.compareTo(MAX_POSITION_SIZE_PERCENT) > 0) {
                return new RiskCheckResult(false, "Превышен максимальный размер позиции: " + 
                    positionSizePercent.multiply(BigDecimal.valueOf(100)) + "%");
            }
            
            // Проверка дневного убытка
            PortfolioState state = getPortfolioState(accountId);
            if (state.getDailyLoss().compareTo(totalValue.multiply(MAX_DAILY_LOSS_PERCENT)) > 0) {
                return new RiskCheckResult(false, "Превышен дневной лимит убытков");
            }
            
            // Проверка общей просадки
            if (state.getDrawdown().compareTo(MAX_PORTFOLIO_DRAWDOWN) > 0) {
                return new RiskCheckResult(false, "Превышена максимальная просадка портфеля");
            }
            
            return new RiskCheckResult(true, "Риски в допустимых пределах");
            
        } catch (Exception e) {
            log.error("Ошибка при проверке рисков: {}", e.getMessage());
            return new RiskCheckResult(false, "Ошибка при проверке рисков: " + e.getMessage());
        }
    }
    
    /**
     * Расчет стоп-лосса и тейк-профита
     */
    public StopLossTakeProfit calculateStopLossTakeProfit(String figi, BigDecimal entryPrice, String direction) {
        BigDecimal stopLoss = BigDecimal.ZERO;
        BigDecimal takeProfit = BigDecimal.ZERO;
        
        if ("BUY".equals(direction)) {
            stopLoss = entryPrice.multiply(BigDecimal.ONE.subtract(STOP_LOSS_PERCENT));
            takeProfit = entryPrice.multiply(BigDecimal.ONE.add(TAKE_PROFIT_PERCENT));
        } else if ("SELL".equals(direction)) {
            stopLoss = entryPrice.multiply(BigDecimal.ONE.add(STOP_LOSS_PERCENT));
            takeProfit = entryPrice.multiply(BigDecimal.ONE.subtract(TAKE_PROFIT_PERCENT));
        }
        
        return new StopLossTakeProfit(stopLoss, takeProfit);
    }
    
    /**
     * Проверка необходимости закрытия позиции по стоп-лоссу
     */
    public boolean shouldClosePosition(String accountId, String figi, BigDecimal currentPrice, BigDecimal entryPrice, String direction) {
        try {
            BigDecimal stopLoss = calculateStopLossTakeProfit(figi, entryPrice, direction).getStopLoss();
            
            if ("BUY".equals(direction)) {
                return currentPrice.compareTo(stopLoss) <= 0;
            } else if ("SELL".equals(direction)) {
                return currentPrice.compareTo(stopLoss) >= 0;
            }
            
            return false;
        } catch (Exception e) {
            log.error("Ошибка при проверке стоп-лосса: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Проверка необходимости закрытия позиции по тейк-профиту
     */
    public boolean shouldTakeProfit(String accountId, String figi, BigDecimal currentPrice, BigDecimal entryPrice, String direction) {
        try {
            BigDecimal takeProfit = calculateStopLossTakeProfit(figi, entryPrice, direction).getTakeProfit();
            
            if ("BUY".equals(direction)) {
                return currentPrice.compareTo(takeProfit) >= 0;
            } else if ("SELL".equals(direction)) {
                return currentPrice.compareTo(takeProfit) <= 0;
            }
            
            return false;
        } catch (Exception e) {
            log.error("Ошибка при проверке тейк-профита: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Обновление состояния портфеля
     */
    public void updatePortfolioState(String accountId, BigDecimal currentValue, BigDecimal previousValue) {
        PortfolioState state = getPortfolioState(accountId);
        
        // Расчет дневного изменения
        BigDecimal dailyChange = currentValue.subtract(previousValue);
        if (dailyChange.compareTo(BigDecimal.ZERO) < 0) {
            state.addDailyLoss(dailyChange.abs());
        }
        
        // Расчет просадки
        BigDecimal drawdown = calculateDrawdown(currentValue, state.getPeakValue());
        state.setDrawdown(drawdown);
        
        // Обновление пикового значения
        if (currentValue.compareTo(state.getPeakValue()) > 0) {
            state.setPeakValue(currentValue);
        }
        
        state.setCurrentValue(currentValue);
        
        log.info("Обновлено состояние портфеля {}: значение={}, просадка={}%, дневной убыток={}", 
            accountId, currentValue, drawdown.multiply(BigDecimal.valueOf(100)), state.getDailyLoss());
    }
    
    /**
     * Сброс дневных лимитов (вызывается ежедневно)
     */
    public void resetDailyLimits(String accountId) {
        PortfolioState state = getPortfolioState(accountId);
        state.resetDailyLoss();
        log.info("Сброшены дневные лимиты для аккаунта: {}", accountId);
    }
    
    /**
     * Получение рекомендаций по риск-менеджменту
     */
    public RiskRecommendation getRiskRecommendation(String accountId) {
        try {
            PortfolioState state = getPortfolioState(accountId);
            Portfolio portfolio = portfolioService.getPortfolio(accountId);
            BigDecimal totalValue = calculateTotalPortfolioValue(portfolio);
            
            String recommendation = "Нормальный уровень риска";
            String action = "Можно продолжать торговлю";
            
            // Проверка просадки
            if (state.getDrawdown().compareTo(MAX_PORTFOLIO_DRAWDOWN) > 0) {
                recommendation = "Высокий уровень риска - превышена просадка";
                action = "Рекомендуется снизить риски и закрыть убыточные позиции";
            }
            
            // Проверка дневного убытка
            if (state.getDailyLoss().compareTo(totalValue.multiply(MAX_DAILY_LOSS_PERCENT)) > 0) {
                recommendation = "Высокий уровень риска - превышен дневной лимит";
                action = "Рекомендуется прекратить торговлю до следующего дня";
            }
            
            return new RiskRecommendation(recommendation, action, state.getDrawdown(), state.getDailyLoss());
            
        } catch (Exception e) {
            log.error("Ошибка при получении рекомендаций по рискам: {}", e.getMessage());
            return new RiskRecommendation("Ошибка анализа", "Проверьте настройки", BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }
    
    // Вспомогательные методы
    private BigDecimal calculateTotalPortfolioValue(Portfolio portfolio) {
        return portfolio.getPositions().stream()
            .map(position -> {
                BigDecimal quantity = position.getQuantity();
                BigDecimal currentPrice = BigDecimal.ZERO;
                
                if (position.getCurrentPrice() != null) {
                    try {
                        currentPrice = new BigDecimal(position.getCurrentPrice().toString());
                    } catch (Exception e) {
                        log.warn("Не удалось получить цену для позиции {}", position.getFigi());
                    }
                }
                
                return quantity.multiply(currentPrice);
            })
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    private PortfolioState getPortfolioState(String accountId) {
        return portfolioStates.computeIfAbsent(accountId, k -> new PortfolioState());
    }
    
    private BigDecimal calculateDrawdown(BigDecimal currentValue, BigDecimal peakValue) {
        if (peakValue.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        return peakValue.subtract(currentValue).divide(peakValue, 4, RoundingMode.HALF_UP);
    }
    
    // Внутренние классы
    public static class RiskCheckResult {
        private final boolean approved;
        private final String reason;
        
        public RiskCheckResult(boolean approved, String reason) {
            this.approved = approved;
            this.reason = reason;
        }
        
        public boolean isApproved() { return approved; }
        public String getReason() { return reason; }
    }
    
    public static class StopLossTakeProfit {
        private final BigDecimal stopLoss;
        private final BigDecimal takeProfit;
        
        public StopLossTakeProfit(BigDecimal stopLoss, BigDecimal takeProfit) {
            this.stopLoss = stopLoss;
            this.takeProfit = takeProfit;
        }
        
        public BigDecimal getStopLoss() { return stopLoss; }
        public BigDecimal getTakeProfit() { return takeProfit; }
    }
    
    public static class RiskRecommendation {
        private final String recommendation;
        private final String action;
        private final BigDecimal drawdown;
        private final BigDecimal dailyLoss;
        
        public RiskRecommendation(String recommendation, String action, BigDecimal drawdown, BigDecimal dailyLoss) {
            this.recommendation = recommendation;
            this.action = action;
            this.drawdown = drawdown;
            this.dailyLoss = dailyLoss;
        }
        
        public String getRecommendation() { return recommendation; }
        public String getAction() { return action; }
        public BigDecimal getDrawdown() { return drawdown; }
        public BigDecimal getDailyLoss() { return dailyLoss; }
    }
    
    private static class PortfolioState {
        private BigDecimal currentValue = BigDecimal.ZERO;
        private BigDecimal peakValue = BigDecimal.ZERO;
        private BigDecimal drawdown = BigDecimal.ZERO;
        private BigDecimal dailyLoss = BigDecimal.ZERO;
        
        public void addDailyLoss(BigDecimal loss) {
            this.dailyLoss = this.dailyLoss.add(loss);
        }
        
        public void resetDailyLoss() {
            this.dailyLoss = BigDecimal.ZERO;
        }
        
        // Геттеры и сеттеры
        public BigDecimal getCurrentValue() { return currentValue; }
        public void setCurrentValue(BigDecimal currentValue) { this.currentValue = currentValue; }
        
        public BigDecimal getPeakValue() { return peakValue; }
        public void setPeakValue(BigDecimal peakValue) { this.peakValue = peakValue; }
        
        public BigDecimal getDrawdown() { return drawdown; }
        public void setDrawdown(BigDecimal drawdown) { this.drawdown = drawdown; }
        
        public BigDecimal getDailyLoss() { return dailyLoss; }
        public void setDailyLoss(BigDecimal dailyLoss) { this.dailyLoss = dailyLoss; }
    }
} 