package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 🚀 НОВЫЙ СЕРВИС: Адаптивная диверсификация в зависимости от размера портфеля
 * 
 * Логика:
 * - Малый портфель (< 50k): Минимальная диверсификация, фокус на росте
 * - Средний портфель (50k-200k): Умеренная диверсификация  
 * - Большой портфель (> 200k): Строгая диверсификация
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdaptiveDiversificationService {
    
    private final TradingSettingsService tradingSettingsService;
    
    // Пороги размера портфеля (в рублях)
    private static final BigDecimal SMALL_PORTFOLIO_THRESHOLD = new BigDecimal("50000");   // 50k
    private static final BigDecimal MEDIUM_PORTFOLIO_THRESHOLD = new BigDecimal("200000"); // 200k
    
    /**
     * Определение уровня портфеля
     */
    public PortfolioLevel getPortfolioLevel(BigDecimal portfolioValue) {
        if (portfolioValue.compareTo(SMALL_PORTFOLIO_THRESHOLD) < 0) {
            return PortfolioLevel.SMALL;
        } else if (portfolioValue.compareTo(MEDIUM_PORTFOLIO_THRESHOLD) < 0) {
            return PortfolioLevel.MEDIUM;
        } else {
            return PortfolioLevel.LARGE;
        }
    }
    
    /**
     * Получение адаптивных настроек диверсификации
     */
    public DiversificationSettings getDiversificationSettings(BigDecimal portfolioValue) {
        PortfolioLevel level = getPortfolioLevel(portfolioValue);
        DiversificationSettings settings = new DiversificationSettings();
        
        switch (level) {
            case SMALL:
                // Малый портфель: Минимальная диверсификация
                settings.setMaxSectorExposurePct(new BigDecimal("0.50"));  // 50% на сектор
                settings.setMaxPositionsPerSector(5);                       // 5 позиций в секторе
                settings.setMaxTotalPositions(8);                          // Максимум 8 позиций
                settings.setMaxPositionSizePct(new BigDecimal("0.25"));    // 25% на позицию
                settings.setDiversificationEnabled(false);                 // Отключаем строгую диверсификацию
                // 🚀 АДАПТИВНЫЕ ЛИМИТЫ ПО КЛАССАМ АКТИВОВ для малого портфеля
                settings.setMaxBondsPercentage(new BigDecimal("0.70"));    // 70% облигаций (консервативно)
                settings.setMaxStocksPercentage(new BigDecimal("0.80"));   // 80% акций (рост)
                settings.setMaxEtfPercentage(new BigDecimal("0.60"));      // 60% ETF
                settings.setReason("Малый портфель: фокус на росте, минимальные ограничения");
                break;
                
            case MEDIUM:
                // Средний портфель: Умеренная диверсификация
                settings.setMaxSectorExposurePct(new BigDecimal("0.30"));  // 30% на сектор
                settings.setMaxPositionsPerSector(4);                       // 4 позиции в секторе
                settings.setMaxTotalPositions(12);                         // Максимум 12 позиций
                settings.setMaxPositionSizePct(new BigDecimal("0.15"));    // 15% на позицию
                settings.setDiversificationEnabled(true);                  // Умеренная диверсификация
                // 🚀 АДАПТИВНЫЕ ЛИМИТЫ ПО КЛАССАМ АКТИВОВ для среднего портфеля
                settings.setMaxBondsPercentage(new BigDecimal("0.50"));    // 50% облигаций
                settings.setMaxStocksPercentage(new BigDecimal("0.60"));   // 60% акций
                settings.setMaxEtfPercentage(new BigDecimal("0.40"));      // 40% ETF
                settings.setReason("Средний портфель: баланс роста и защиты");
                break;
                
            case LARGE:
                // Большой портфель: Строгая диверсификация
                settings.setMaxSectorExposurePct(new BigDecimal("0.15"));  // 15% на сектор
                settings.setMaxPositionsPerSector(3);                       // 3 позиции в секторе
                settings.setMaxTotalPositions(20);                         // Максимум 20 позиций
                settings.setMaxPositionSizePct(new BigDecimal("0.08"));    // 8% на позицию
                settings.setDiversificationEnabled(true);                  // Строгая диверсификация
                // 🚀 АДАПТИВНЫЕ ЛИМИТЫ ПО КЛАССАМ АКТИВОВ для большого портфеля
                settings.setMaxBondsPercentage(new BigDecimal("0.30"));    // 30% облигаций (строго)
                settings.setMaxStocksPercentage(new BigDecimal("0.40"));   // 40% акций (консервативно)
                settings.setMaxEtfPercentage(new BigDecimal("0.25"));      // 25% ETF
                settings.setReason("Большой портфель: приоритет защиты капитала");
                break;
        }
        
        log.info("🎯 Адаптивная диверсификация: портфель {} ({}₽) → {}", 
            level, portfolioValue, settings.getReason());
        
        return settings;
    }
    
    /**
     * Проверка нужна ли диверсификация для данного портфеля
     */
    public boolean isDiversificationRequired(BigDecimal portfolioValue) {
        DiversificationSettings settings = getDiversificationSettings(portfolioValue);
        return settings.isDiversificationEnabled();
    }
    
    /**
     * Получение адаптивного лимита на количество позиций
     */
    public int getMaxPositionsLimit(BigDecimal portfolioValue) {
        return getDiversificationSettings(portfolioValue).getMaxTotalPositions();
    }
    
    /**
     * Получение адаптивного лимита на сектор
     */
    public BigDecimal getMaxSectorExposure(BigDecimal portfolioValue) {
        return getDiversificationSettings(portfolioValue).getMaxSectorExposurePct();
    }
    
    /**
     * Получение адаптивного лимита на размер позиции
     */
    public BigDecimal getMaxPositionSize(BigDecimal portfolioValue) {
        return getDiversificationSettings(portfolioValue).getMaxPositionSizePct();
    }
    
    /**
     * 🚀 НОВЫЕ МЕТОДЫ: Получение адаптивных лимитов по классам активов
     */
    public BigDecimal getMaxBondsPercentage(BigDecimal portfolioValue) {
        return getDiversificationSettings(portfolioValue).getMaxBondsPercentage();
    }
    
    public BigDecimal getMaxStocksPercentage(BigDecimal portfolioValue) {
        return getDiversificationSettings(portfolioValue).getMaxStocksPercentage();
    }
    
    public BigDecimal getMaxEtfPercentage(BigDecimal portfolioValue) {
        return getDiversificationSettings(portfolioValue).getMaxEtfPercentage();
    }
    
    /**
     * Универсальный метод получения лимита по типу инструмента
     */
    public BigDecimal getMaxAssetClassPercentage(BigDecimal portfolioValue, String instrumentType) {
        DiversificationSettings settings = getDiversificationSettings(portfolioValue);
        
        switch (instrumentType.toLowerCase()) {
            case "bond":
                return settings.getMaxBondsPercentage();
            case "share":
            case "stock":
                return settings.getMaxStocksPercentage();
            case "etf":
                return settings.getMaxEtfPercentage();
            default:
                // Для неизвестных типов используем лимит акций
                return settings.getMaxStocksPercentage();
        }
    }
    
    /**
     * Уровни портфеля
     */
    public enum PortfolioLevel {
        SMALL("Малый"),
        MEDIUM("Средний"), 
        LARGE("Большой");
        
        private final String displayName;
        
        PortfolioLevel(String displayName) {
            this.displayName = displayName;
        }
        
        @Override
        public String toString() {
            return displayName;
        }
    }
    
    /**
     * Настройки диверсификации
     */
    public static class DiversificationSettings {
        private BigDecimal maxSectorExposurePct;
        private int maxPositionsPerSector;
        private int maxTotalPositions;
        private BigDecimal maxPositionSizePct;
        private boolean diversificationEnabled;
        private String reason;
        
        // 🚀 НОВОЕ: Адаптивные лимиты по классам активов
        private BigDecimal maxBondsPercentage;
        private BigDecimal maxStocksPercentage;
        private BigDecimal maxEtfPercentage;
        
        // Getters and Setters
        public BigDecimal getMaxSectorExposurePct() { return maxSectorExposurePct; }
        public void setMaxSectorExposurePct(BigDecimal maxSectorExposurePct) { this.maxSectorExposurePct = maxSectorExposurePct; }
        
        public int getMaxPositionsPerSector() { return maxPositionsPerSector; }
        public void setMaxPositionsPerSector(int maxPositionsPerSector) { this.maxPositionsPerSector = maxPositionsPerSector; }
        
        public int getMaxTotalPositions() { return maxTotalPositions; }
        public void setMaxTotalPositions(int maxTotalPositions) { this.maxTotalPositions = maxTotalPositions; }
        
        public BigDecimal getMaxPositionSizePct() { return maxPositionSizePct; }
        public void setMaxPositionSizePct(BigDecimal maxPositionSizePct) { this.maxPositionSizePct = maxPositionSizePct; }
        
        public boolean isDiversificationEnabled() { return diversificationEnabled; }
        public void setDiversificationEnabled(boolean diversificationEnabled) { this.diversificationEnabled = diversificationEnabled; }
        
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        
        // 🚀 НОВЫЕ ГЕТТЕРЫ/СЕТТЕРЫ для классов активов
        public BigDecimal getMaxBondsPercentage() { return maxBondsPercentage; }
        public void setMaxBondsPercentage(BigDecimal maxBondsPercentage) { this.maxBondsPercentage = maxBondsPercentage; }
        
        public BigDecimal getMaxStocksPercentage() { return maxStocksPercentage; }
        public void setMaxStocksPercentage(BigDecimal maxStocksPercentage) { this.maxStocksPercentage = maxStocksPercentage; }
        
        public BigDecimal getMaxEtfPercentage() { return maxEtfPercentage; }
        public void setMaxEtfPercentage(BigDecimal maxEtfPercentage) { this.maxEtfPercentage = maxEtfPercentage; }
    }
}
