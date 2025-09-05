package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
// import ru.tinkoff.piapi.core.InvestApi; // unused
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
    @SuppressWarnings("unused")
    private final BotLogService botLogService;
    private final ApiRateLimiter apiRateLimiter;
    private final Map<String, List<HistoricCandle>> candleCache = new ConcurrentHashMap<>();
    // private static final int NANO_SCALE = 9;
    
    /**
     * Получение свечей для анализа
     */
    public List<HistoricCandle> getCandles(String figi, CandleInterval interval, int days) {
        String cacheKey = figi + "_" + interval + "_" + days;
        
        return candleCache.computeIfAbsent(cacheKey, k -> {
            // Ограничим период по правилам API для выбранного интервала
            int safeDays = Math.min(days, getMaxDaysForInterval(interval));
            Instant to = Instant.now();
            Instant from = to.minus(safeDays, ChronoUnit.DAYS);
            
            try {
                apiRateLimiter.acquire();
                return investApiManager.getCurrentInvestApi().getMarketDataService()
                    .getCandlesSync(figi, from, to, interval);
            } catch (Exception e) {
                log.error("Ошибка при получении свечей: {}", e.getMessage());
                return List.of();
            }
        });
    }

    /**
     * Преобразование Quotation в BigDecimal
     */
    private BigDecimal quotationToBigDecimal(ru.tinkoff.piapi.contract.v1.Quotation quotation) {
        return new BigDecimal(quotation.getUnits() + "." + String.format("%09d", quotation.getNano()));
    }

    /**
     * Преобразование Quotation в BigDecimal (публичный метод)
     */
    private BigDecimal toBigDecimal(ru.tinkoff.piapi.contract.v1.Quotation q) {
        if (q == null) return BigDecimal.ZERO;
        String nano = String.format("%09d", q.getNano());
        return new BigDecimal(q.getUnits() + "." + nano);
    }

    /**
     * Расчет Average True Range (ATR)
     * Возвращает абсолютное значение ATR в тех же единицах, что и цена
     */
    public BigDecimal calculateATR(String figi, CandleInterval interval, int period) {
        // Берем запас свечей для корректного TR (нужен prevClose)
        List<HistoricCandle> candles = getCandles(figi, interval, Math.max(period + 5, period * 2));
        if (candles.size() < period + 1) {
            return BigDecimal.ZERO;
        }

        BigDecimal trSum = BigDecimal.ZERO;
        for (int i = 1; i <= period; i++) {
            HistoricCandle cur = candles.get(i);
            HistoricCandle prev = candles.get(i - 1);

            BigDecimal high = toBigDecimal(cur.getHigh());
            BigDecimal low = toBigDecimal(cur.getLow());
            BigDecimal prevClose = toBigDecimal(prev.getClose());

            BigDecimal highLow = high.subtract(low).abs();
            BigDecimal highPrevClose = high.subtract(prevClose).abs();
            BigDecimal lowPrevClose = low.subtract(prevClose).abs();

            BigDecimal tr = highLow.max(highPrevClose).max(lowPrevClose);
            trSum = trSum.add(tr);
        }

        return trSum.divide(BigDecimal.valueOf(period), 6, RoundingMode.HALF_UP);
    }

    // Максимально допустимая глубина периода в днях для каждого интервала (по ограничениям Tinkoff Invest API)
    private int getMaxDaysForInterval(CandleInterval interval) {
        switch (interval) {
            case CANDLE_INTERVAL_1_MIN:
            case CANDLE_INTERVAL_5_MIN:
            case CANDLE_INTERVAL_15_MIN:
                return 7;   // для минутных обычно до 7 дней
            case CANDLE_INTERVAL_HOUR:
                return 365; // до 1 года
            case CANDLE_INTERVAL_DAY:
                return 3650; // до 10 лет
            default:
                return 365;
        }
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
     * Получение актуальной рыночной цены через OrderBook
     */
    public BigDecimal getCurrentMarketPrice(String figi) {
        try {
            apiRateLimiter.acquire();
            var orderBook = investApiManager.getCurrentInvestApi().getMarketDataService()
                .getOrderBookSync(figi, 1); // Глубина 1 для получения лучших цен
            
            if (orderBook != null && !orderBook.getBidsList().isEmpty() && !orderBook.getAsksList().isEmpty()) {
                // Берем среднее между лучшими bid и ask
                var bestBid = orderBook.getBidsList().get(0);
                var bestAsk = orderBook.getAsksList().get(0);
                
                BigDecimal bidPrice = quotationToBigDecimal(bestBid.getPrice());
                BigDecimal askPrice = quotationToBigDecimal(bestAsk.getPrice());
                BigDecimal marketPrice = bidPrice.add(askPrice).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
                
                log.debug("🔍 Рыночная цена через OrderBook для {}: bid={}, ask={}, middle={}", figi, bidPrice, askPrice, marketPrice);
                return marketPrice;
            }
        } catch (Exception e) {
            log.warn("Не удалось получить рыночную цену через OrderBook для {}: {}", figi, e.getMessage());
        }
        return null;
    }

    /**
     * Расчёт относительного спрэда по лучшим котировкам: (ask - bid) / mid
     */
    public BigDecimal getSpreadPct(String figi) {
        try {
            apiRateLimiter.acquire();
            var orderBook = investApiManager.getCurrentInvestApi().getMarketDataService()
                .getOrderBookSync(figi, 1);
            if (orderBook != null && !orderBook.getBidsList().isEmpty() && !orderBook.getAsksList().isEmpty()) {
                var bestBid = orderBook.getBidsList().get(0);
                var bestAsk = orderBook.getAsksList().get(0);
                BigDecimal bid = quotationToBigDecimal(bestBid.getPrice());
                BigDecimal ask = quotationToBigDecimal(bestAsk.getPrice());
                if (bid != null && ask != null && bid.compareTo(BigDecimal.ZERO) > 0 && ask.compareTo(bid) >= 0) {
                    BigDecimal mid = bid.add(ask).divide(BigDecimal.valueOf(2), 6, RoundingMode.HALF_UP);
                    BigDecimal spreadAbs = ask.subtract(bid);
                    return spreadAbs.divide(mid, 6, RoundingMode.HALF_UP);
                }
            }
        } catch (Exception e) {
            log.warn("Не удалось получить спрэд через OrderBook для {}: {}", figi, e.getMessage());
        }
        return BigDecimal.ZERO;
    }

    /**
     * Объём последней дневной свечи (штуки/лоты по данным API)
     */
    public long getLastDailyVolume(String figi) {
        try {
            List<HistoricCandle> candles = getCandles(figi, CandleInterval.CANDLE_INTERVAL_DAY, 3);
            if (candles == null || candles.isEmpty()) return 0L;
            HistoricCandle last = candles.get(candles.size() - 1);
            return last.getVolume();
        } catch (Exception e) {
            log.warn("Не удалось получить дневной объём для {}: {}", figi, e.getMessage());
            return 0L;
        }
    }

    /**
     * Медианный дневной объём за N завершённых торговых дней.
     * По умолчанию исключаем текущий незавершённый день.
     */
    public long getMedianDailyVolume(String figi, int days, boolean excludeCurrentDayIfIncomplete) {
        try {
            int fetch = Math.max(days + 2, days);
            List<HistoricCandle> candles = getCandles(figi, CandleInterval.CANDLE_INTERVAL_DAY, fetch);
            if (candles == null || candles.isEmpty()) return 0L;

            // Собираем объёмы с конца, пропуская текущий незавершённый день при необходимости
            java.util.List<Long> volumes = new java.util.ArrayList<>();
            for (int i = candles.size() - 1; i >= 0 && volumes.size() < days; i--) {
                HistoricCandle c = candles.get(i);
                boolean isComplete = c.getIsComplete();
                if (!isComplete && excludeCurrentDayIfIncomplete) {
                    continue; // пропускаем текущий день, если свеча не завершена
                }
                volumes.add(c.getVolume());
            }

            if (volumes.isEmpty()) {
                return candles.get(candles.size() - 1).getVolume();
            }

            java.util.Collections.sort(volumes);
            int n = volumes.size();
            if (n % 2 == 1) {
                return volumes.get(n / 2);
            } else {
                long a = volumes.get(n / 2 - 1);
                long b = volumes.get(n / 2);
                return (a + b) / 2L;
            }
        } catch (Exception e) {
            log.warn("Не удалось получить медианный дневной объём для {}: {}", figi, e.getMessage());
            return 0L;
        }
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
        
        // Пытаемся получить актуальную рыночную цену
        BigDecimal currentPrice = getCurrentMarketPrice(figi);
        
        // Если не удалось получить рыночную цену, используем последнюю свечу
        if (currentPrice == null) {
            HistoricCandle lastCandle = recentCandles.get(recentCandles.size() - 1);
            currentPrice = new BigDecimal(lastCandle.getClose().getUnits() + "." + 
                String.format("%09d", lastCandle.getClose().getNano()));
            log.debug("🔍 Цена из последней свечи для {}: {} (из {} свечей)", figi, currentPrice, recentCandles.size());
        } else {
            log.debug("🔍 Актуальная рыночная цена для {}: {} (через OrderBook)", figi, currentPrice);
        }
        
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