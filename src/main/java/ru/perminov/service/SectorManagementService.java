package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.perminov.dto.ShareDto;
import ru.tinkoff.piapi.core.models.Position;
import ru.perminov.service.BotLogService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SectorManagementService {
    
    static {
        log.info("🚀 SectorManagementService инициализируется...");
    }
    
    private final BotLogService botLogService;
    
    // Российские ограничения для неквалифицированных инвесторов
    @Value("${position-management.max-sector-exposure-pct:0.15}")
    private BigDecimal maxSectorExposurePct;
    

    
    @Value("${position-management.max-positions-per-sector:3}")
    private int maxPositionsPerSector;
    
    @Value("${position-management.max-total-positions:15}")
    private int maxTotalPositions;
    
    // Российские секторы экономики
    private static final Map<String, String> RUSSIAN_SECTORS = new HashMap<>();
    private static final Map<String, String> SECTOR_CATEGORIES = new HashMap<>();
    
    static {
        // Основные секторы российской экономики
        RUSSIAN_SECTORS.put("BANKS", "Банки и финансы");
        RUSSIAN_SECTORS.put("OIL_GAS", "Нефть и газ");
        RUSSIAN_SECTORS.put("METALS", "Металлургия");
        RUSSIAN_SECTORS.put("TELECOM", "Телекоммуникации");
        RUSSIAN_SECTORS.put("RETAIL", "Розничная торговля");
        RUSSIAN_SECTORS.put("TRANSPORT", "Транспорт");
        RUSSIAN_SECTORS.put("CHEMICALS", "Химическая промышленность");
        RUSSIAN_SECTORS.put("CONSTRUCTION", "Строительство");
        RUSSIAN_SECTORS.put("AGRICULTURE", "Сельское хозяйство");
        RUSSIAN_SECTORS.put("TECH", "Технологии");
        RUSSIAN_SECTORS.put("UTILITIES", "Коммунальные услуги");
        RUSSIAN_SECTORS.put("REAL_ESTATE", "Недвижимость");
        RUSSIAN_SECTORS.put("HEALTHCARE", "Здравоохранение");
        RUSSIAN_SECTORS.put("CONSUMER_GOODS", "Товары народного потребления");
        RUSSIAN_SECTORS.put("OTHER", "Прочие");
        
        // Категории риска для российских секторов
        SECTOR_CATEGORIES.put("BANKS", "HIGH");           // Высокий риск
        SECTOR_CATEGORIES.put("OIL_GAS", "MEDIUM");       // Средний риск
        SECTOR_CATEGORIES.put("METALS", "HIGH");          // Высокий риск
        SECTOR_CATEGORIES.put("TELECOM", "LOW");          // Низкий риск
        SECTOR_CATEGORIES.put("RETAIL", "MEDIUM");        // Средний риск
        SECTOR_CATEGORIES.put("TRANSPORT", "MEDIUM");     // Средний риск
        SECTOR_CATEGORIES.put("CHEMICALS", "HIGH");       // Высокий риск
        SECTOR_CATEGORIES.put("CONSTRUCTION", "HIGH");    // Высокий риск
        SECTOR_CATEGORIES.put("AGRICULTURE", "MEDIUM");   // Средний риск
        SECTOR_CATEGORIES.put("TECH", "HIGH");            // Высокий риск
        SECTOR_CATEGORIES.put("UTILITIES", "LOW");        // Низкий риск
        SECTOR_CATEGORIES.put("REAL_ESTATE", "MEDIUM");   // Средний риск
        SECTOR_CATEGORIES.put("HEALTHCARE", "LOW");       // Низкий риск
        SECTOR_CATEGORIES.put("CONSUMER_GOODS", "LOW");  // Низкий риск
        SECTOR_CATEGORIES.put("OTHER", "MEDIUM");         // Средний риск
    }
    
    // Маппинг FIGI на секторы (основные российские акции)
    private static final Map<String, String> FIGI_TO_SECTOR = new HashMap<>();
    
    static {
        // Банки
        FIGI_TO_SECTOR.put("BBG004730NQ9", "BANKS");      // Сбербанк
        FIGI_TO_SECTOR.put("BBG004730ZJ9", "BANKS");      // ВТБ
        FIGI_TO_SECTOR.put("BBG004S681M1", "BANKS");      // Тинькофф
        FIGI_TO_SECTOR.put("BBG004S681B4", "BANKS");      // Альфа-Банк
        
        // Нефть и газ
        FIGI_TO_SECTOR.put("BBG0047315Y7", "OIL_GAS");   // Газпром
        FIGI_TO_SECTOR.put("BBG004731354", "OIL_GAS");   // Лукойл
        FIGI_TO_SECTOR.put("BBG004S681W1", "OIL_GAS");   // Роснефть
        FIGI_TO_SECTOR.put("BBG004S681B4", "OIL_GAS");   // Новатэк
        
        // Металлургия
        FIGI_TO_SECTOR.put("BBG004S681M1", "METALS");    // НЛМК
        FIGI_TO_SECTOR.put("BBG004S681B4", "METALS");    // Северсталь
        FIGI_TO_SECTOR.put("BBG004S681W1", "METALS");    // ММК
        
        // Телеком
        FIGI_TO_SECTOR.put("BBG004S681M1", "TELECOM");   // МТС
        FIGI_TO_SECTOR.put("BBG004S681B4", "TELECOM");   // МегаФон
        FIGI_TO_SECTOR.put("BBG004S681W1", "TELECOM");   // Ростелеком
        
        // Розничная торговля
        FIGI_TO_SECTOR.put("BBG004S681M1", "RETAIL");    // Магнит
        FIGI_TO_SECTOR.put("BBG004S681B4", "RETAIL");    // X5 Group
        FIGI_TO_SECTOR.put("BBG004S681W1", "RETAIL");    // Лента
        
        // Транспорт
        FIGI_TO_SECTOR.put("BBG004S681M1", "TRANSPORT"); // Аэрофлот
        FIGI_TO_SECTOR.put("BBG004S681B4", "TRANSPORT"); // РЖД
        
        // Химия
        FIGI_TO_SECTOR.put("BBG004S681M1", "CHEMICALS"); // ФосАгро
        FIGI_TO_SECTOR.put("BBG004S681B4", "CHEMICALS"); // Акрон
        
        // Строительство
        FIGI_TO_SECTOR.put("BBG004S681M1", "CONSTRUCTION"); // ПИК
        
        // Сельское хозяйство
        FIGI_TO_SECTOR.put("BBG004S681M1", "AGRICULTURE"); // Русагро
        
        // Технологии
        FIGI_TO_SECTOR.put("BBG004S681M1", "TECH");      // Яндекс
        FIGI_TO_SECTOR.put("BBG004S681B4", "TECH");      // VK
        
        // Коммунальные услуги
        FIGI_TO_SECTOR.put("BBG004S681M1", "UTILITIES"); // Интер РАО
        
        // Недвижимость
        FIGI_TO_SECTOR.put("BBG004S681M1", "REAL_ESTATE"); // AFK Система
        
        // Здравоохранение
        FIGI_TO_SECTOR.put("BBG004S681M1", "HEALTHCARE"); // Фармстандарт
        
        // Товары народного потребления
        FIGI_TO_SECTOR.put("BBG004S681M1", "CONSUMER_GOODS"); // Черкизово
    }
    
    /**
     * Проверка возможности покупки с учетом диверсификации по секторам
     */
    public SectorValidationResult validateSectorDiversification(
            String figi, 
            BigDecimal positionValue, 
            BigDecimal portfolioValue,
            List<Position> currentPositions) {
        
        log.info("🔍 Валидация диверсификации: figi={}, positionValue={}, portfolioValue={}, positions={}", 
            figi, positionValue, portfolioValue, currentPositions.size());
        
        SectorValidationResult result = new SectorValidationResult();
        result.setValid(true);
        
        try {
            // Определяем сектор для инструмента
            String sector = getSectorForInstrument(figi);
            result.setSector(sector);
            result.setSectorName(RUSSIAN_SECTORS.get(sector));
            
            // Анализируем текущее распределение по секторам
            Map<String, SectorAnalysis> sectorAnalysis = analyzeCurrentSectors(currentPositions, portfolioValue);
            
            // Проверяем лимиты для сектора
            SectorAnalysis currentSector = sectorAnalysis.getOrDefault(sector, new SectorAnalysis());
            
            // 1. Проверка максимальной доли сектора
            BigDecimal newSectorValue = currentSector.getTotalValue().add(positionValue);
            BigDecimal newSectorPercentage = newSectorValue.divide(portfolioValue, 4, RoundingMode.HALF_UP);
            
            if (newSectorPercentage.compareTo(maxSectorExposurePct) > 0) {
                result.setValid(false);
                result.addViolation(String.format(
                    "Превышение лимита сектора %s: %.2f%% > %.2f%% (максимум)",
                    RUSSIAN_SECTORS.get(sector),
                    newSectorPercentage.multiply(BigDecimal.valueOf(100)),
                    maxSectorExposurePct.multiply(BigDecimal.valueOf(100))
                ));
                
                botLogService.addLogEntry(
                    BotLogService.LogLevel.WARNING,
                    BotLogService.LogCategory.RISK_MANAGEMENT,
                    "Превышение лимита сектора",
                    String.format("Сектор: %s, Текущая доля: %.2f%%, Новая доля: %.2f%%, Максимум: %.2f%%",
                        RUSSIAN_SECTORS.get(sector),
                        currentSector.getTotalValue().divide(portfolioValue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)),
                        newSectorPercentage.multiply(BigDecimal.valueOf(100)),
                        maxSectorExposurePct.multiply(BigDecimal.valueOf(100))
                    )
                );
            }
            
            // 2. Проверка количества позиций в секторе
            int newPositionsInSector = currentSector.getPositionsCount() + 1;
            if (newPositionsInSector > maxPositionsPerSector) {
                result.setValid(false);
                result.addViolation(String.format(
                    "Превышение лимита позиций в секторе %s: %d > %d (максимум)",
                    RUSSIAN_SECTORS.get(sector),
                    newPositionsInSector,
                    maxPositionsPerSector
                ));
            }
            
            // 3. Проверка общего количества позиций
            int totalPositions = currentPositions.size() + 1;
            if (totalPositions > maxTotalPositions) {
                result.setValid(false);
                result.addViolation(String.format(
                    "Превышение общего лимита позиций: %d > %d (максимум)",
                    totalPositions,
                    maxTotalPositions
                ));
            }
            
            // 4. Проверка концентрации риска в высокорисковых секторах
            String sectorRisk = SECTOR_CATEGORIES.get(sector);
            if ("HIGH".equals(sectorRisk)) {
                BigDecimal highRiskExposure = calculateHighRiskExposure(sectorAnalysis);
                BigDecimal newHighRiskExposure = highRiskExposure.add(positionValue);
                BigDecimal maxHighRiskExposure = portfolioValue.multiply(new BigDecimal("0.30")); // Максимум 30% в высокорисковых
                
                if (newHighRiskExposure.compareTo(maxHighRiskExposure) > 0) {
                    result.setValid(false);
                    result.addViolation(String.format(
                        "Превышение лимита высокорисковых секторов: %.2f%% > 30%% (максимум)",
                        newHighRiskExposure.divide(portfolioValue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                    ));
                }
            }
            
            // 5. Проверка минимальной диверсификации
            if (sectorAnalysis.size() < 3) {
                result.addWarning("Низкая диверсификация: менее 3 секторов");
            }
            
            // Устанавливаем результаты анализа
            result.setCurrentSectorAnalysis(currentSector);
            result.setSectorAnalysis(sectorAnalysis);
            result.setNewSectorPercentage(newSectorPercentage);
            result.setTotalPositions(totalPositions);
            result.setPositionsInSector(newPositionsInSector);
            
        } catch (Exception e) {
            log.error("Ошибка при проверке диверсификации секторов: {}", e.getMessage());
            result.setValid(false);
            result.addViolation("Ошибка анализа: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Анализ текущего распределения по секторам
     */
    public Map<String, SectorAnalysis> analyzeCurrentSectors(List<Position> positions, BigDecimal portfolioValue) {
        Map<String, SectorAnalysis> sectorAnalysis = new HashMap<>();
        
        for (Position position : positions) {
            String sector = getSectorForInstrument(position.getFigi());
            BigDecimal positionValue = position.getCurrentPrice().getValue().multiply(position.getQuantity());
            
            sectorAnalysis.computeIfAbsent(sector, k -> new SectorAnalysis())
                .addPosition(positionValue);
        }
        
        // Рассчитываем проценты
        for (SectorAnalysis analysis : sectorAnalysis.values()) {
            BigDecimal percentage = analysis.getTotalValue().divide(portfolioValue, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
            analysis.setPercentage(percentage);
        }
        
        return sectorAnalysis;
    }
    
    /**
     * Расчет экспозиции высокорисковых секторов
     */
    private BigDecimal calculateHighRiskExposure(Map<String, SectorAnalysis> sectorAnalysis) {
        return sectorAnalysis.entrySet().stream()
            .filter(entry -> "HIGH".equals(SECTOR_CATEGORIES.get(entry.getKey())))
            .map(entry -> entry.getValue().getTotalValue())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * Получение сектора для инструмента
     */
    public String getSectorForInstrument(String figi) {
        // Сначала проверяем маппинг FIGI
        String sector = FIGI_TO_SECTOR.get(figi);
        if (sector != null) {
            return sector;
        }
        
        // Если FIGI не найден, определяем по названию
        // Это можно расширить в будущем
        return "OTHER";
    }
    
    /**
     * Получение названия сектора
     */
    public String getSectorName(String sector) {
        return RUSSIAN_SECTORS.getOrDefault(sector, "Неизвестный сектор");
    }
    
    /**
     * Получение категории риска сектора
     */
    public String getSectorRiskCategory(String sector) {
        return SECTOR_CATEGORIES.getOrDefault(sector, "MEDIUM");
    }
    
    /**
     * Рекомендации по диверсификации
     */
    public List<String> getDiversificationRecommendations(Map<String, SectorAnalysis> sectorAnalysis) {
        List<String> recommendations = new ArrayList<>();
        
        // Проверяем количество секторов
        if (sectorAnalysis.size() < 5) {
            recommendations.add("Добавить позиции в новые сектора для лучшей диверсификации");
        }
        
        // Проверяем концентрацию в высокорисковых секторах
        BigDecimal highRiskExposure = calculateHighRiskExposure(sectorAnalysis);
        if (highRiskExposure.compareTo(BigDecimal.valueOf(0.4)) > 0) {
            recommendations.add("Снизить долю высокорисковых секторов (сейчас > 40%)");
        }
        
        // Проверяем перевес в одном секторе
        for (Map.Entry<String, SectorAnalysis> entry : sectorAnalysis.entrySet()) {
            if (entry.getValue().getPercentage().compareTo(BigDecimal.valueOf(25)) > 0) {
                recommendations.add(String.format("Снизить концентрацию в секторе %s (сейчас %.1f%%)",
                    RUSSIAN_SECTORS.get(entry.getKey()),
                    entry.getValue().getPercentage()));
            }
        }
        
        return recommendations;
    }
    
    // Внутренние классы для анализа
    public static class SectorAnalysis {
        private BigDecimal totalValue = BigDecimal.ZERO;
        private int positionsCount = 0;
        private BigDecimal percentage = BigDecimal.ZERO;
        private List<String> instruments = new ArrayList<>();
        
        public void addPosition(BigDecimal value) {
            this.totalValue = this.totalValue.add(value);
            this.positionsCount++;
        }
        
        // Геттеры и сеттеры
        public BigDecimal getTotalValue() { return totalValue; }
        public void setTotalValue(BigDecimal totalValue) { this.totalValue = totalValue; }
        public int getPositionsCount() { return positionsCount; }
        public void setPositionsCount(int positionsCount) { this.positionsCount = positionsCount; }
        public BigDecimal getPercentage() { return percentage; }
        public void setPercentage(BigDecimal percentage) { this.percentage = percentage; }
        public List<String> getInstruments() { return instruments; }
        public void setInstruments(List<String> instruments) { this.instruments = instruments; }
    }
    
    public static class SectorValidationResult {
        private boolean valid;
        private String sector;
        private String sectorName;
        private BigDecimal newSectorPercentage;
        private int totalPositions;
        private int positionsInSector;
        private SectorAnalysis currentSectorAnalysis;
        private Map<String, SectorAnalysis> sectorAnalysis;
        private List<String> violations = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
        
        // Геттеры и сеттеры
        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }
        public String getSector() { return sector; }
        public void setSector(String sector) { this.sector = sector; }
        public String getSectorName() { return sectorName; }
        public void setSectorName(String sectorName) { this.sectorName = sectorName; }
        public BigDecimal getNewSectorPercentage() { return newSectorPercentage; }
        public void setNewSectorPercentage(BigDecimal newSectorPercentage) { this.newSectorPercentage = newSectorPercentage; }
        public int getTotalPositions() { return totalPositions; }
        public void setTotalPositions(int totalPositions) { this.totalPositions = totalPositions; }
        public int getPositionsInSector() { return positionsInSector; }
        public void setPositionsInSector(int positionsInSector) { this.positionsInSector = positionsInSector; }
        public SectorAnalysis getCurrentSectorAnalysis() { return currentSectorAnalysis; }
        public void setCurrentSectorAnalysis(SectorAnalysis currentSectorAnalysis) { this.currentSectorAnalysis = currentSectorAnalysis; }
        public Map<String, SectorAnalysis> getSectorAnalysis() { return sectorAnalysis; }
        public void setSectorAnalysis(Map<String, SectorAnalysis> sectorAnalysis) { this.sectorAnalysis = sectorAnalysis; }
        public List<String> getViolations() { return violations; }
        public List<String> getWarnings() { return warnings; }
        
        public void addViolation(String violation) {
            this.violations.add(violation);
        }
        
        public void addWarning(String warning) {
            this.warnings.add(warning);
        }
    }
}
