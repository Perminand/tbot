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
            // Проверяем входные параметры
            if (figi == null || figi.isEmpty()) {
                result.setValid(false);
                result.addViolation("FIGI инструмента не указан");
                return result;
            }
            
            if (positionValue == null || positionValue.compareTo(BigDecimal.ZERO) <= 0) {
                result.setValid(false);
                result.addViolation("Некорректная стоимость позиции: " + positionValue);
                return result;
            }
            
            if (portfolioValue == null || portfolioValue.compareTo(BigDecimal.ZERO) <= 0) {
                result.setValid(false);
                result.addViolation("Некорректная стоимость портфеля: " + portfolioValue);
                return result;
            }
            
            if (currentPositions == null) {
                currentPositions = new ArrayList<>();
            }
            
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
        log.info("🔍 Анализ секторов: positions={}, portfolioValue={}", positions.size(), portfolioValue);
        
        Map<String, SectorAnalysis> sectorAnalysis = new HashMap<>();
        
        if (positions == null || positions.isEmpty()) {
            log.warn("⚠️ Пустой список позиций");
            return sectorAnalysis;
        }
        
        for (Position position : positions) {
            try {
                if (position == null) {
                    log.warn("⚠️ Позиция null, пропускаем");
                    continue;
                }
                
                String figi = position.getFigi();
                if (figi == null || figi.isEmpty()) {
                    log.warn("⚠️ FIGI позиции пустой, пропускаем");
                    continue;
                }
                
                if (position.getCurrentPrice() == null || position.getQuantity() == null) {
                    log.warn("⚠️ Позиция {} не имеет цены или количества", figi);
                    continue;
                }
                
                String sector = getSectorForInstrument(figi);
                BigDecimal positionValue = position.getCurrentPrice().getValue().multiply(position.getQuantity());
                
                log.debug("🔍 Позиция: figi={}, sector={}, value={}", figi, sector, positionValue);
                
                sectorAnalysis.computeIfAbsent(sector, k -> new SectorAnalysis())
                    .addPosition(positionValue);
                    
            } catch (Exception e) {
                log.error("❌ Ошибка обработки позиции: {}", e.getMessage(), e);
            }
        }
        
        // Рассчитываем проценты
        for (SectorAnalysis analysis : sectorAnalysis.values()) {
            try {
                if (analysis.getTotalValue() == null || analysis.getTotalValue().compareTo(BigDecimal.ZERO) <= 0) {
                    log.warn("⚠️ Нулевая стоимость сектора, пропускаем расчет процента");
                    continue;
                }
                
                BigDecimal percentage = analysis.getTotalValue().divide(portfolioValue, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
                analysis.setPercentage(percentage);
                log.debug("🔍 Процент сектора рассчитан: {}%", percentage);
            } catch (Exception e) {
                log.error("❌ Ошибка расчета процента сектора: {}", e.getMessage(), e);
            }
        }
        
        return sectorAnalysis;
    }
    
    /**
     * Расчет экспозиции высокорисковых секторов
     */
    private BigDecimal calculateHighRiskExposure(Map<String, SectorAnalysis> sectorAnalysis) {
        try {
            return sectorAnalysis.entrySet().stream()
                .filter(entry -> "HIGH".equals(SECTOR_CATEGORIES.get(entry.getKey())))
                .map(entry -> entry.getValue().getTotalValue())
                .filter(value -> value != null && value.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        } catch (Exception e) {
            log.error("❌ Ошибка расчета высокорисковой экспозиции: {}", e.getMessage(), e);
            return BigDecimal.ZERO;
        }
    }
    
    /**
     * Получение сектора для инструмента
     */
    public String getSectorForInstrument(String figi) {
        if (figi == null || figi.isEmpty()) {
            log.warn("⚠️ FIGI пустой, возвращаем OTHER");
            return "OTHER";
        }
        
        // Сначала проверяем маппинг FIGI
        String sector = FIGI_TO_SECTOR.get(figi);
        if (sector != null) {
            log.debug("🔍 FIGI {} найден в маппинге: {}", figi, sector);
            return sector;
        }
        
        log.debug("🔍 FIGI {} не найден в маппинге, возвращаем OTHER", figi);
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
        
        try {
            if (sectorAnalysis == null || sectorAnalysis.isEmpty()) {
                recommendations.add("Нет данных для анализа диверсификации");
                return recommendations;
            }
            
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
                try {
                    SectorAnalysis analysis = entry.getValue();
                    if (analysis != null && analysis.getPercentage() != null) {
                        if (analysis.getPercentage().compareTo(BigDecimal.valueOf(25)) > 0) {
                            recommendations.add(String.format("Снизить концентрацию в секторе %s (сейчас %.1f%%)",
                                RUSSIAN_SECTORS.get(entry.getKey()),
                                analysis.getPercentage()));
                        }
                    }
                } catch (Exception e) {
                    log.warn("⚠️ Ошибка анализа сектора {}: {}", entry.getKey(), e.getMessage());
                }
            }
            
        } catch (Exception e) {
            log.error("❌ Ошибка получения рекомендаций: {}", e.getMessage(), e);
            recommendations.add("Ошибка анализа диверсификации: " + e.getMessage());
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
            if (value == null) {
                log.warn("⚠️ Попытка добавить null значение позиции");
                return;
            }
            this.totalValue = this.totalValue.add(value);
            this.positionsCount++;
            log.debug("🔍 Позиция добавлена: value={}, total={}, count={}", value, this.totalValue, this.positionsCount);
        }
        
        // Геттеры и сеттеры
        public BigDecimal getTotalValue() { return totalValue; }
        public void setTotalValue(BigDecimal totalValue) { 
            if (totalValue != null && totalValue.compareTo(BigDecimal.ZERO) >= 0) {
                this.totalValue = totalValue;
            } else {
                log.warn("⚠️ Попытка установить некорректную стоимость: {}", totalValue);
                this.totalValue = BigDecimal.ZERO;
            }
        }
        public int getPositionsCount() { return positionsCount; }
        public void setPositionsCount(int positionsCount) { 
            if (positionsCount >= 0) {
                this.positionsCount = positionsCount;
            } else {
                log.warn("⚠️ Попытка установить некорректное количество позиций: {}", positionsCount);
                this.positionsCount = 0;
            }
        }
        public BigDecimal getPercentage() { return percentage; }
        public void setPercentage(BigDecimal percentage) { 
            if (percentage != null && percentage.compareTo(BigDecimal.ZERO) >= 0) {
                this.percentage = percentage;
            } else {
                log.warn("⚠️ Попытка установить некорректный процент: {}", percentage);
                this.percentage = BigDecimal.ZERO;
            }
        }
        public List<String> getInstruments() { return instruments; }
        public void setInstruments(List<String> instruments) { 
            if (instruments != null) {
                this.instruments = instruments;
                log.debug("🔍 Инструменты сектора установлены: {}", instruments.size());
            } else {
                log.warn("⚠️ Попытка установить null список инструментов");
                this.instruments = new ArrayList<>();
            }
        }
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
        public void setValid(boolean valid) { 
            this.valid = valid;
            log.debug("🔍 Валидность установлена: {}", valid);
        }
        public String getSector() { return sector; }
        public void setSector(String sector) { 
            if (sector != null && !sector.isEmpty()) {
                this.sector = sector;
                log.debug("🔍 Сектор установлен: {}", sector);
            } else {
                log.warn("⚠️ Попытка установить пустой сектор");
            }
        }
        public String getSectorName() { return sectorName; }
        public void setSectorName(String sectorName) { 
            if (sectorName != null && !sectorName.isEmpty()) {
                this.sectorName = sectorName;
                log.debug("🔍 Название сектора установлено: {}", sectorName);
            } else {
                log.warn("⚠️ Попытка установить пустое название сектора");
            }
        }
        public BigDecimal getNewSectorPercentage() { return newSectorPercentage; }
        public void setNewSectorPercentage(BigDecimal newSectorPercentage) { 
            if (newSectorPercentage != null && newSectorPercentage.compareTo(BigDecimal.ZERO) >= 0) {
                this.newSectorPercentage = newSectorPercentage;
                log.debug("🔍 Новый процент сектора установлен: {}%", newSectorPercentage.multiply(BigDecimal.valueOf(100)));
            } else {
                log.warn("⚠️ Попытка установить некорректный процент сектора: {}", newSectorPercentage);
            }
        }
        public int getTotalPositions() { return totalPositions; }
        public void setTotalPositions(int totalPositions) { 
            if (totalPositions >= 0) {
                this.totalPositions = totalPositions;
                log.debug("🔍 Общее количество позиций установлено: {}", totalPositions);
            } else {
                log.warn("⚠️ Попытка установить некорректное общее количество позиций: {}", totalPositions);
            }
        }
        public int getPositionsInSector() { return positionsInSector; }
        public void setPositionsInSector(int positionsInSector) { 
            if (positionsInSector >= 0) {
                this.positionsInSector = positionsInSector;
                log.debug("🔍 Количество позиций в секторе установлено: {}", positionsInSector);
            } else {
                log.warn("⚠️ Попытка установить некорректное количество позиций в секторе: {}", positionsInSector);
            }
        }
        public SectorAnalysis getCurrentSectorAnalysis() { return currentSectorAnalysis; }
        public void setCurrentSectorAnalysis(SectorAnalysis currentSectorAnalysis) { 
            this.currentSectorAnalysis = currentSectorAnalysis;
            log.debug("🔍 Текущий анализ сектора установлен: {}", 
                currentSectorAnalysis != null ? "данные" : "null");
        }
        public Map<String, SectorAnalysis> getSectorAnalysis() { return sectorAnalysis; }
        public void setSectorAnalysis(Map<String, SectorAnalysis> sectorAnalysis) { 
            this.sectorAnalysis = sectorAnalysis;
            log.debug("🔍 Анализ секторов установлен: {}", 
                sectorAnalysis != null ? sectorAnalysis.size() + " секторов" : "null");
        }
        public List<String> getViolations() { return violations; }
        public List<String> getWarnings() { return warnings; }
        
        public void addViolation(String violation) {
            if (violation != null && !violation.isEmpty()) {
                this.violations.add(violation);
                log.debug("🔍 Нарушение добавлено: {}", violation);
            } else {
                log.warn("⚠️ Попытка добавить пустое нарушение");
            }
        }
        
        public void addWarning(String warning) {
            if (warning != null && !warning.isEmpty()) {
                this.warnings.add(warning);
                log.debug("🔍 Предупреждение добавлено: {}", warning);
            } else {
                log.warn("⚠️ Попытка добавить пустое предупреждение");
            }
        }
    }
}
