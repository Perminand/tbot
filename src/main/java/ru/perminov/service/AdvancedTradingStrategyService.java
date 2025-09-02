package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.contract.v1.CandleInterval;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdvancedTradingStrategyService {
    
    private final MarketAnalysisService marketAnalysisService;
    private final AdvancedTechnicalAnalysisService advancedAnalysisService;
    private final RiskManagementService riskManagementService;
    private final BotLogService botLogService;
    
    /**
     * Комплексный анализ торговых сигналов
     */
    public TradingSignal analyzeTradingSignal(String figi, String accountId) {
        try {
            // Базовые индикаторы
            BigDecimal sma20 = marketAnalysisService.calculateSMA(figi, CandleInterval.CANDLE_INTERVAL_DAY, 20);
            BigDecimal sma50 = marketAnalysisService.calculateSMA(figi, CandleInterval.CANDLE_INTERVAL_DAY, 50);
            BigDecimal rsi = marketAnalysisService.calculateRSI(figi, CandleInterval.CANDLE_INTERVAL_DAY, 14);
            
            // Продвинутые индикаторы
            AdvancedTechnicalAnalysisService.MACDResult macd = 
                advancedAnalysisService.calculateMACD(figi, CandleInterval.CANDLE_INTERVAL_DAY);
            AdvancedTechnicalAnalysisService.BollingerBandsResult bb = 
                advancedAnalysisService.calculateBollingerBands(figi, CandleInterval.CANDLE_INTERVAL_DAY, 20);
            AdvancedTechnicalAnalysisService.StochasticResult stoch = 
                advancedAnalysisService.calculateStochastic(figi, CandleInterval.CANDLE_INTERVAL_DAY, 14);
            AdvancedTechnicalAnalysisService.VolumeAnalysisResult volume = 
                advancedAnalysisService.analyzeVolume(figi, CandleInterval.CANDLE_INTERVAL_DAY);
            AdvancedTechnicalAnalysisService.SupportResistanceResult sr = 
                advancedAnalysisService.findSupportResistance(figi, CandleInterval.CANDLE_INTERVAL_DAY);
            
            // Анализ тренда
            MarketAnalysisService.TrendAnalysis trend = 
                marketAnalysisService.analyzeTrend(figi, CandleInterval.CANDLE_INTERVAL_DAY);
            
            // Расчет общего сигнала
            TradingSignal signal = calculateCombinedSignal(
                sma20, sma50, rsi, macd, bb, stoch, volume, sr, trend
            );
            
            // Проверка рисков
            RiskManagementService.RiskRecommendation riskRec = 
                riskManagementService.getRiskRecommendation(accountId);
            
            signal.setRiskLevel(riskRec.getRecommendation());
            signal.setRiskAction(riskRec.getAction());
            
            log.info("Анализ сигнала для {}: действие={}, сила={}, риск={}", 
                figi, signal.getAction(), signal.getStrength(), signal.getRiskLevel());
            
            return signal;
            
        } catch (Exception e) {
            log.error("Ошибка при анализе торгового сигнала: {}", e.getMessage());
            return new TradingSignal("HOLD", "ERROR", BigDecimal.ZERO, "Ошибка анализа");
        }
    }
    
    /**
     * Расчет комбинированного сигнала на основе всех индикаторов
     */
    private TradingSignal calculateCombinedSignal(
            BigDecimal sma20, BigDecimal sma50, BigDecimal rsi,
            AdvancedTechnicalAnalysisService.MACDResult macd,
            AdvancedTechnicalAnalysisService.BollingerBandsResult bb,
            AdvancedTechnicalAnalysisService.StochasticResult stoch,
            AdvancedTechnicalAnalysisService.VolumeAnalysisResult volume,
            AdvancedTechnicalAnalysisService.SupportResistanceResult sr,
            MarketAnalysisService.TrendAnalysis trend) {
        
        BigDecimal buyScore = BigDecimal.ZERO;
        BigDecimal sellScore = BigDecimal.ZERO;
        Map<String, String> signals = new HashMap<>();
        
        // 1. Анализ тренда (вес: 25%)
        if (trend.getTrend() == MarketAnalysisService.TrendType.BULLISH) {
            buyScore = buyScore.add(BigDecimal.valueOf(25));
            signals.put("trend", "BULLISH");
        } else if (trend.getTrend() == MarketAnalysisService.TrendType.BEARISH) {
            sellScore = sellScore.add(BigDecimal.valueOf(25));
            signals.put("trend", "BEARISH");
        }
        
        // 2. Анализ скользящих средних (вес: 20%)
        if (sma20.compareTo(sma50) > 0) {
            buyScore = buyScore.add(BigDecimal.valueOf(20));
            signals.put("sma", "BULLISH");
        } else {
            sellScore = sellScore.add(BigDecimal.valueOf(20));
            signals.put("sma", "BEARISH");
        }
        
        // 3. RSI анализ (вес: 15%)
        if (rsi.compareTo(BigDecimal.valueOf(30)) < 0) {
            buyScore = buyScore.add(BigDecimal.valueOf(15));
            signals.put("rsi", "OVERSOLD");
        } else if (rsi.compareTo(BigDecimal.valueOf(70)) > 0) {
            sellScore = sellScore.add(BigDecimal.valueOf(15));
            signals.put("rsi", "OVERBOUGHT");
        } else {
            buyScore = buyScore.add(BigDecimal.valueOf(7.5));
            sellScore = sellScore.add(BigDecimal.valueOf(7.5));
            signals.put("rsi", "NEUTRAL");
        }
        
        // 4. MACD анализ (вес: 15%)
        if (macd.getMacdLine().compareTo(macd.getSignalLine()) > 0 && 
            macd.getHistogram().compareTo(BigDecimal.ZERO) > 0) {
            buyScore = buyScore.add(BigDecimal.valueOf(15));
            signals.put("macd", "BULLISH");
        } else if (macd.getMacdLine().compareTo(macd.getSignalLine()) < 0 && 
                   macd.getHistogram().compareTo(BigDecimal.ZERO) < 0) {
            sellScore = sellScore.add(BigDecimal.valueOf(15));
            signals.put("macd", "BEARISH");
        }
        
        // 5. Bollinger Bands анализ (вес: 10%)
        BigDecimal currentPrice = trend.getCurrentPrice();
        if (currentPrice.compareTo(bb.getLowerBand()) < 0) {
            buyScore = buyScore.add(BigDecimal.valueOf(10));
            signals.put("bb", "OVERSOLD");
        } else if (currentPrice.compareTo(bb.getUpperBand()) > 0) {
            sellScore = sellScore.add(BigDecimal.valueOf(10));
            signals.put("bb", "OVERBOUGHT");
        }
        
        // 6. Stochastic анализ (вес: 10%)
        if (stoch.getKPercent().compareTo(BigDecimal.valueOf(20)) < 0) {
            buyScore = buyScore.add(BigDecimal.valueOf(10));
            signals.put("stoch", "OVERSOLD");
        } else if (stoch.getKPercent().compareTo(BigDecimal.valueOf(80)) > 0) {
            sellScore = sellScore.add(BigDecimal.valueOf(10));
            signals.put("stoch", "OVERBOUGHT");
        }
        
        // 7. Volume анализ (вес: 5%)
        if ("HIGH".equals(volume.getVolumeSignal())) {
            if (buyScore.compareTo(sellScore) > 0) {
                buyScore = buyScore.add(BigDecimal.valueOf(5));
            } else {
                sellScore = sellScore.add(BigDecimal.valueOf(5));
            }
            signals.put("volume", "HIGH");
        }
        
        // Определение действия
        String action = "HOLD";
        BigDecimal strength = BigDecimal.ZERO;
        
        if (buyScore.compareTo(sellScore) > 0 && buyScore.compareTo(BigDecimal.valueOf(50)) > 0) {
            action = "BUY";
            strength = buyScore;
        } else if (sellScore.compareTo(buyScore) > 0 && sellScore.compareTo(BigDecimal.valueOf(50)) > 0) {
            action = "SELL";
            strength = sellScore;
        }
        
        return new TradingSignal(action, strength, signals);
    }
    
    /**
     * Стратегия следования за трендом
     */
    public TrendFollowingSignal analyzeTrendFollowing(String figi) {
        try {
            // Анализ тренда на разных таймфреймах
            MarketAnalysisService.TrendAnalysis dailyTrend = 
                marketAnalysisService.analyzeTrend(figi, CandleInterval.CANDLE_INTERVAL_DAY);
            MarketAnalysisService.TrendAnalysis weeklyTrend = 
                marketAnalysisService.analyzeTrend(figi, CandleInterval.CANDLE_INTERVAL_WEEK);
            
            // Анализ объема
            AdvancedTechnicalAnalysisService.VolumeAnalysisResult volume = 
                advancedAnalysisService.analyzeVolume(figi, CandleInterval.CANDLE_INTERVAL_DAY);
            
            String signal = "HOLD";
            String reason = "Недостаточно данных";
            
            // Сильный сигнал на покупку
            if (dailyTrend.getTrend() == MarketAnalysisService.TrendType.BULLISH && 
                weeklyTrend.getTrend() == MarketAnalysisService.TrendType.BULLISH &&
                "HIGH".equals(volume.getVolumeSignal())) {
                signal = "BUY";
                reason = "Сильный восходящий тренд с высоким объемом";
            }
            // Сильный сигнал на продажу
            else if (dailyTrend.getTrend() == MarketAnalysisService.TrendType.BEARISH && 
                     weeklyTrend.getTrend() == MarketAnalysisService.TrendType.BEARISH &&
                     "HIGH".equals(volume.getVolumeSignal())) {
                signal = "SELL";
                reason = "Сильный нисходящий тренд с высоким объемом";
            }
            // Слабый сигнал
            else if (dailyTrend.getTrend() == MarketAnalysisService.TrendType.BULLISH) {
                signal = "BUY";
                reason = "Восходящий тренд на дневном графике";
            } else if (dailyTrend.getTrend() == MarketAnalysisService.TrendType.BEARISH) {
                signal = "SELL";
                reason = "Нисходящий тренд на дневном графике";
            }
            
            return new TrendFollowingSignal(signal, reason, dailyTrend.getTrend(), weeklyTrend.getTrend());
            
        } catch (Exception e) {
            log.error("Ошибка при анализе следования за трендом: {}", e.getMessage());
            return new TrendFollowingSignal("HOLD", "Ошибка анализа", null, null);
        }
    }
    
    /**
     * Стратегия отскока от уровней поддержки/сопротивления
     */
    public SupportResistanceSignal analyzeSupportResistance(String figi) {
        try {
            AdvancedTechnicalAnalysisService.SupportResistanceResult sr = 
                advancedAnalysisService.findSupportResistance(figi, CandleInterval.CANDLE_INTERVAL_DAY);
            
            BigDecimal currentPrice = marketAnalysisService.analyzeTrend(figi, CandleInterval.CANDLE_INTERVAL_DAY).getCurrentPrice();
            
            String signal = "HOLD";
            String reason = "Цена в нейтральной зоне";
            
            // Отскок от поддержки
            if (currentPrice.compareTo(sr.getSupport().multiply(BigDecimal.valueOf(1.02))) < 0) {
                signal = "BUY";
                reason = "Отскок от уровня поддержки";
            }
            // Отскок от сопротивления
            else if (currentPrice.compareTo(sr.getResistance().multiply(BigDecimal.valueOf(0.98))) > 0) {
                signal = "SELL";
                reason = "Отскок от уровня сопротивления";
            }
            
            return new SupportResistanceSignal(signal, reason, sr.getSupport(), sr.getResistance(), currentPrice);
            
        } catch (Exception e) {
            log.error("Ошибка при анализе уровней поддержки/сопротивления: {}", e.getMessage());
            return new SupportResistanceSignal("HOLD", "Ошибка анализа", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }
    
    // Внутренние классы
    public static class TradingSignal {
        private String action;
        private BigDecimal strength;
        private Map<String, String> signals;
        private String riskLevel;
        private String riskAction;
        
        public TradingSignal(String action, BigDecimal strength, Map<String, String> signals) {
            this.action = action;
            this.strength = strength;
            this.signals = signals;
        }
        
        public TradingSignal(String action, String strength, BigDecimal strengthValue, String riskLevel) {
            this.action = action;
            this.strength = strengthValue;
            this.riskLevel = riskLevel;
        }
        
        // Геттеры и сеттеры
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        
        public BigDecimal getStrength() { return strength; }
        public void setStrength(BigDecimal strength) { this.strength = strength; }
        
        public Map<String, String> getSignals() { return signals; }
        public void setSignals(Map<String, String> signals) { this.signals = signals; }
        
        public String getRiskLevel() { return riskLevel; }
        public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
        
        public String getRiskAction() { return riskAction; }
        public void setRiskAction(String riskAction) { this.riskAction = riskAction; }
    }
    
    public static class TrendFollowingSignal {
        private final String signal;
        private final String reason;
        private final MarketAnalysisService.TrendType dailyTrend;
        private final MarketAnalysisService.TrendType weeklyTrend;
        
        public TrendFollowingSignal(String signal, String reason, MarketAnalysisService.TrendType dailyTrend, MarketAnalysisService.TrendType weeklyTrend) {
            this.signal = signal;
            this.reason = reason;
            this.dailyTrend = dailyTrend;
            this.weeklyTrend = weeklyTrend;
        }
        
        public String getSignal() { return signal; }
        public String getReason() { return reason; }
        public MarketAnalysisService.TrendType getDailyTrend() { return dailyTrend; }
        public MarketAnalysisService.TrendType getWeeklyTrend() { return weeklyTrend; }
    }
    
    public static class SupportResistanceSignal {
        private final String signal;
        private final String reason;
        private final BigDecimal support;
        private final BigDecimal resistance;
        private final BigDecimal currentPrice;
        
        public SupportResistanceSignal(String signal, String reason, BigDecimal support, BigDecimal resistance, BigDecimal currentPrice) {
            this.signal = signal;
            this.reason = reason;
            this.support = support;
            this.resistance = resistance;
            this.currentPrice = currentPrice;
        }
        
        public String getSignal() { return signal; }
        public String getReason() { return reason; }
        public BigDecimal getSupport() { return support; }
        public BigDecimal getResistance() { return resistance; }
        public BigDecimal getCurrentPrice() { return currentPrice; }
    }
} 