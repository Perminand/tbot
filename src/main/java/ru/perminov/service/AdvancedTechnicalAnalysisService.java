package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.contract.v1.HistoricCandle;
import ru.tinkoff.piapi.contract.v1.CandleInterval;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdvancedTechnicalAnalysisService {
    
    private final MarketAnalysisService marketAnalysisService;
    
    /**
     * MACD (Moving Average Convergence Divergence)
     */
    public MACDResult calculateMACD(String figi, CandleInterval interval) {
        List<HistoricCandle> candles = marketAnalysisService.getCandles(figi, interval, 50);
        
        if (candles.size() < 26) {
            return new MACDResult(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        
        BigDecimal ema12 = calculateEMA(candles, 12);
        BigDecimal ema26 = calculateEMA(candles, 26);
        BigDecimal macdLine = ema12.subtract(ema26);
        BigDecimal signalLine = calculateSignalLine(candles, macdLine);
        BigDecimal histogram = macdLine.subtract(signalLine);
        
        return new MACDResult(macdLine, signalLine, histogram);
    }
    
    /**
     * Bollinger Bands
     */
    public BollingerBandsResult calculateBollingerBands(String figi, CandleInterval interval, int period) {
        List<HistoricCandle> candles = marketAnalysisService.getCandles(figi, interval, period + 10);
        
        if (candles.size() < period) {
            return new BollingerBandsResult(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        
        BigDecimal sma = marketAnalysisService.calculateSMA(figi, interval, period);
        BigDecimal standardDeviation = calculateStandardDeviation(candles, sma, period);
        
        BigDecimal upperBand = sma.add(standardDeviation.multiply(BigDecimal.valueOf(2)));
        BigDecimal lowerBand = sma.subtract(standardDeviation.multiply(BigDecimal.valueOf(2)));
        
        return new BollingerBandsResult(upperBand, sma, lowerBand);
    }
    
    /**
     * Stochastic Oscillator
     */
    public StochasticResult calculateStochastic(String figi, CandleInterval interval, int period) {
        List<HistoricCandle> candles = marketAnalysisService.getCandles(figi, interval, period + 10);
        
        if (candles.size() < period) {
            return new StochasticResult(BigDecimal.ZERO, BigDecimal.ZERO);
        }
        
        BigDecimal highestHigh = getHighestHigh(candles, period);
        BigDecimal lowestLow = getLowestLow(candles, period);
        BigDecimal currentClose = getCurrentPrice(candles);
        
        BigDecimal kPercent = currentClose.subtract(lowestLow)
            .divide(highestHigh.subtract(lowestLow), 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
        
        BigDecimal dPercent = calculateDPercent(candles, period);
        
        return new StochasticResult(kPercent, dPercent);
    }
    
    /**
     * Volume Analysis
     */
    public VolumeAnalysisResult analyzeVolume(String figi, CandleInterval interval) {
        List<HistoricCandle> candles = marketAnalysisService.getCandles(figi, interval, 20);
        
        if (candles.size() < 10) {
            return new VolumeAnalysisResult(BigDecimal.ZERO, BigDecimal.ZERO, "NORMAL");
        }
        
        BigDecimal avgVolume = calculateAverageVolume(candles, 10);
        BigDecimal currentVolume = BigDecimal.valueOf(candles.get(0).getVolume());
        BigDecimal volumeRatio = currentVolume.divide(avgVolume, 4, RoundingMode.HALF_UP);
        
        String volumeSignal = "NORMAL";
        if (volumeRatio.compareTo(BigDecimal.valueOf(1.5)) > 0) {
            volumeSignal = "HIGH";
        } else if (volumeRatio.compareTo(BigDecimal.valueOf(0.5)) < 0) {
            volumeSignal = "LOW";
        }
        
        return new VolumeAnalysisResult(currentVolume, volumeRatio, volumeSignal);
    }
    
    /**
     * Support and Resistance Levels
     */
    public SupportResistanceResult findSupportResistance(String figi, CandleInterval interval) {
        List<HistoricCandle> candles = marketAnalysisService.getCandles(figi, interval, 50);
        
        if (candles.size() < 20) {
            return new SupportResistanceResult(BigDecimal.ZERO, BigDecimal.ZERO);
        }
        
        BigDecimal resistance = findResistanceLevel(candles);
        BigDecimal support = findSupportLevel(candles);
        
        return new SupportResistanceResult(resistance, support);
    }
    
    // Вспомогательные методы
    private BigDecimal calculateEMA(List<HistoricCandle> candles, int period) {
        BigDecimal multiplier = BigDecimal.valueOf(2.0 / (period + 1));
        BigDecimal ema = getCurrentPrice(candles);
        
        for (int i = 1; i < Math.min(period, candles.size()); i++) {
            BigDecimal price = getPrice(candles.get(i));
            ema = price.multiply(multiplier).add(ema.multiply(BigDecimal.ONE.subtract(multiplier)));
        }
        
        return ema;
    }
    
    private BigDecimal calculateSignalLine(List<HistoricCandle> candles, BigDecimal macdLine) {
        // Упрощенная реализация сигнальной линии
        return macdLine.multiply(BigDecimal.valueOf(0.8));
    }
    
    private BigDecimal calculateStandardDeviation(List<HistoricCandle> candles, BigDecimal mean, int period) {
        BigDecimal sum = BigDecimal.ZERO;
        
        for (int i = 0; i < Math.min(period, candles.size()); i++) {
            BigDecimal price = getPrice(candles.get(i));
            BigDecimal diff = price.subtract(mean);
            sum = sum.add(diff.multiply(diff));
        }
        
        BigDecimal variance = sum.divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);
        return BigDecimal.valueOf(Math.sqrt(variance.doubleValue()));
    }
    
    private BigDecimal getHighestHigh(List<HistoricCandle> candles, int period) {
        return candles.stream()
            .limit(period)
            .map(this::getHighPrice)
            .max(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);
    }
    
    private BigDecimal getLowestLow(List<HistoricCandle> candles, int period) {
        return candles.stream()
            .limit(period)
            .map(this::getLowPrice)
            .min(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);
    }
    
    private BigDecimal calculateDPercent(List<HistoricCandle> candles, int period) {
        // Упрощенная реализация %D
        return BigDecimal.valueOf(50.0);
    }
    
    private BigDecimal calculateAverageVolume(List<HistoricCandle> candles, int period) {
        return candles.stream()
            .limit(period)
            .map(candle -> BigDecimal.valueOf(candle.getVolume()))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
    }
    
    private BigDecimal findResistanceLevel(List<HistoricCandle> candles) {
        return getHighestHigh(candles, 20);
    }
    
    private BigDecimal findSupportLevel(List<HistoricCandle> candles) {
        return getLowestLow(candles, 20);
    }
    
    private BigDecimal getCurrentPrice(List<HistoricCandle> candles) {
        return getPrice(candles.get(0));
    }
    
    private BigDecimal getPrice(HistoricCandle candle) {
        return new BigDecimal(candle.getClose().getUnits() + "." + 
            String.format("%09d", candle.getClose().getNano()));
    }
    
    private BigDecimal getHighPrice(HistoricCandle candle) {
        return new BigDecimal(candle.getHigh().getUnits() + "." + 
            String.format("%09d", candle.getHigh().getNano()));
    }
    
    private BigDecimal getLowPrice(HistoricCandle candle) {
        return new BigDecimal(candle.getLow().getUnits() + "." + 
            String.format("%09d", candle.getLow().getNano()));
    }
    
    // Результаты анализа
    public static class MACDResult {
        private final BigDecimal macdLine;
        private final BigDecimal signalLine;
        private final BigDecimal histogram;
        
        public MACDResult(BigDecimal macdLine, BigDecimal signalLine, BigDecimal histogram) {
            this.macdLine = macdLine;
            this.signalLine = signalLine;
            this.histogram = histogram;
        }
        
        public BigDecimal getMacdLine() { return macdLine; }
        public BigDecimal getSignalLine() { return signalLine; }
        public BigDecimal getHistogram() { return histogram; }
    }
    
    public static class BollingerBandsResult {
        private final BigDecimal upperBand;
        private final BigDecimal middleBand;
        private final BigDecimal lowerBand;
        
        public BollingerBandsResult(BigDecimal upperBand, BigDecimal middleBand, BigDecimal lowerBand) {
            this.upperBand = upperBand;
            this.middleBand = middleBand;
            this.lowerBand = lowerBand;
        }
        
        public BigDecimal getUpperBand() { return upperBand; }
        public BigDecimal getMiddleBand() { return middleBand; }
        public BigDecimal getLowerBand() { return lowerBand; }
    }
    
    public static class StochasticResult {
        private final BigDecimal kPercent;
        private final BigDecimal dPercent;
        
        public StochasticResult(BigDecimal kPercent, BigDecimal dPercent) {
            this.kPercent = kPercent;
            this.dPercent = dPercent;
        }
        
        public BigDecimal getKPercent() { return kPercent; }
        public BigDecimal getDPercent() { return dPercent; }
    }
    
    public static class VolumeAnalysisResult {
        private final BigDecimal currentVolume;
        private final BigDecimal volumeRatio;
        private final String volumeSignal;
        
        public VolumeAnalysisResult(BigDecimal currentVolume, BigDecimal volumeRatio, String volumeSignal) {
            this.currentVolume = currentVolume;
            this.volumeRatio = volumeRatio;
            this.volumeSignal = volumeSignal;
        }
        
        public BigDecimal getCurrentVolume() { return currentVolume; }
        public BigDecimal getVolumeRatio() { return volumeRatio; }
        public String getVolumeSignal() { return volumeSignal; }
    }
    
    public static class SupportResistanceResult {
        private final BigDecimal resistance;
        private final BigDecimal support;
        
        public SupportResistanceResult(BigDecimal resistance, BigDecimal support) {
            this.resistance = resistance;
            this.support = support;
        }
        
        public BigDecimal getResistance() { return resistance; }
        public BigDecimal getSupport() { return support; }
    }
} 