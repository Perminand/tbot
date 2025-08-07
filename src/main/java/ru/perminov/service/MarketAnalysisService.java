package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.core.InvestApi;
import ru.tinkoff.piapi.contract.v1.HistoricCandle;
import ru.tinkoff.piapi.contract.v1.CandleInterval;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketAnalysisService {
    
    private final InvestApiManager investApiManager;
    private final BotLogService botLogService;
    private final Map<String, List<HistoricCandle>> candleCache = new ConcurrentHashMap<>();
    
    /**
     * Получение свечей для анализа
     */
    public List<HistoricCandle> getCandles(String figi, CandleInterval interval, int days) {
        String cacheKey = figi + "_" + interval + "_" + days;
        
        return candleCache.computeIfAbsent(cacheKey, k -> {
            Instant from = Instant.now().minus(days, ChronoUnit.DAYS);
            Instant to = Instant.now();
            
            try {
                return investApiManager.getCurrentInvestApi().getMarketDataService()
                    .getCandlesSync(figi, from, to, interval);
            } catch (Exception e) {
                log.error("Ошибка при получении свечей для {}: {}", figi, e.getMessage());
                return List.of();
            }
        });
    }
    
    /**
     * Расчет простой скользящей средней (SMA)
     */
    public BigDecimal calculateSMA(String figi, CandleInterval interval, int period) {
        List<HistoricCandle> candles = getCandles(figi, interval, period + 10);
        
        if (candles.size() < period) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal sum = candles.stream()
            .limit(period)
            .map(candle -> new BigDecimal(candle.getClose().getUnits() + "." + 
                String.format("%09d", candle.getClose().getNano())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
            
        return sum.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
    }
    
    /**
     * Расчет относительной силы (RSI)
     */
    public BigDecimal calculateRSI(String figi, CandleInterval interval, int period) {
        List<HistoricCandle> candles = getCandles(figi, interval, period * 2);
        
        if (candles.size() < period + 1) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal gains = BigDecimal.ZERO;
        BigDecimal losses = BigDecimal.ZERO;
        
        for (int i = 1; i <= period; i++) {
            BigDecimal currentPrice = new BigDecimal(candles.get(i).getClose().getUnits() + "." + 
                String.format("%09d", candles.get(i).getClose().getNano()));
            BigDecimal previousPrice = new BigDecimal(candles.get(i-1).getClose().getUnits() + "." + 
                String.format("%09d", candles.get(i-1).getClose().getNano()));
            
            BigDecimal change = currentPrice.subtract(previousPrice);
            if (change.compareTo(BigDecimal.ZERO) > 0) {
                gains = gains.add(change);
            } else {
                losses = losses.add(change.abs());
            }
        }
        
        if (losses.equals(BigDecimal.ZERO)) {
            return BigDecimal.valueOf(100);
        }
        
        BigDecimal avgGain = gains.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
        BigDecimal avgLoss = losses.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
        BigDecimal rs = avgGain.divide(avgLoss, 4, RoundingMode.HALF_UP);
        
        return BigDecimal.valueOf(100).subtract(
            BigDecimal.valueOf(100).divide(BigDecimal.ONE.add(rs), 2, RoundingMode.HALF_UP)
        );
    }
    
    /**
     * Анализ тренда
     */
    public TrendAnalysis analyzeTrend(String figi, CandleInterval interval) {
        BigDecimal sma20 = calculateSMA(figi, interval, 20);
        BigDecimal sma50 = calculateSMA(figi, interval, 50);
        BigDecimal rsi = calculateRSI(figi, interval, 14);
        
        List<HistoricCandle> recentCandles = getCandles(figi, interval, 5);
        if (recentCandles.isEmpty()) {
            return new TrendAnalysis(TrendType.UNKNOWN, BigDecimal.ZERO, "Недостаточно данных");
        }
        
        BigDecimal currentPrice = new BigDecimal(recentCandles.get(0).getClose().getUnits() + "." + 
            String.format("%09d", recentCandles.get(0).getClose().getNano()));
        
        TrendType trend;
        String signal;
        
        if (sma20.compareTo(sma50) > 0 && rsi.compareTo(BigDecimal.valueOf(30)) > 0 && rsi.compareTo(BigDecimal.valueOf(70)) < 0) {
            trend = TrendType.BULLISH;
            signal = "Восходящий тренд";
        } else if (sma20.compareTo(sma50) < 0 && rsi.compareTo(BigDecimal.valueOf(70)) > 0) {
            trend = TrendType.BEARISH;
            signal = "Нисходящий тренд";
        } else {
            trend = TrendType.SIDEWAYS;
            signal = "Боковой тренд";
        }
        
        return new TrendAnalysis(trend, currentPrice, signal);
    }
    
    public enum TrendType {
        BULLISH, BEARISH, SIDEWAYS, UNKNOWN
    }
    
    public static class TrendAnalysis {
        private final TrendType trend;
        private final BigDecimal currentPrice;
        private final String signal;
        
        public TrendAnalysis(TrendType trend, BigDecimal currentPrice, String signal) {
            this.trend = trend;
            this.currentPrice = currentPrice;
            this.signal = signal;
        }
        
        // Getters
        public TrendType getTrend() { return trend; }
        public BigDecimal getCurrentPrice() { return currentPrice; }
        public String getSignal() { return signal; }
    }
} 