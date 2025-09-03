package ru.perminov.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.perminov.service.SectorManagementService;
import ru.perminov.service.PortfolioService;
import ru.perminov.service.BotLogService;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sectors")
@RequiredArgsConstructor
@Slf4j
public class SectorController {
    
    private final SectorManagementService sectorManagementService;
    private final PortfolioService portfolioService;
    private final BotLogService botLogService;
    
    /**
     * Получение анализа диверсификации по секторам
     */
    @GetMapping("/diversification/{accountId}")
    public ResponseEntity<?> getSectorDiversification(@PathVariable String accountId) {
        try {
            log.info("🔍 Получение анализа диверсификации для аккаунта: {}", accountId);
            
            // Проверяем, что сервисы не null
            if (portfolioService == null) {
                log.error("❌ PortfolioService is null!");
                return ResponseEntity.internalServerError()
                    .body("PortfolioService не инициализирован");
            }
            
            if (sectorManagementService == null) {
                log.error("❌ SectorManagementService is null!");
                return ResponseEntity.internalServerError()
                    .body("SectorManagementService не инициализирован");
            }
            
            var portfolio = portfolioService.getPortfolio(accountId);
            if (portfolio == null) {
                log.error("❌ Портфель не найден для аккаунта: {}", accountId);
                return ResponseEntity.status(404)
                    .body("Портфель не найден для аккаунта: " + accountId);
            }
            
            // Проверяем структуру портфеля
            if (portfolio.getPositions() == null) {
                log.error("❌ Позиции портфеля null для аккаунта: {}", accountId);
                return ResponseEntity.internalServerError()
                    .body("Позиции портфеля не инициализированы");
            }
            
            if (portfolio.getTotalAmountShares() == null) {
                log.error("❌ TotalAmountShares null для аккаунта: {}", accountId);
                return ResponseEntity.internalServerError()
                    .body("Общая стоимость портфеля не инициализирована");
            }
            
            log.info("🔍 Портфель получен: positions={}, totalValue={}", 
                portfolio.getPositions().size(), portfolio.getTotalAmountShares().getValue());
            
            var positions = portfolio.getPositions();
            var totalValue = portfolio.getTotalAmountShares().getValue();
            
            if (totalValue == null || totalValue.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("⚠️ Нулевая или отрицательная стоимость портфеля: {}", totalValue);
                return ResponseEntity.badRequest()
                    .body("Нулевая стоимость портфеля: " + totalValue);
            }
            
            // Анализируем текущее распределение по секторам
            var sectorAnalysis = sectorManagementService.analyzeCurrentSectors(positions, totalValue);
            log.info("🔍 Анализ секторов выполнен: {}", sectorAnalysis.size());
            
            // Проверяем результат анализа
            if (sectorAnalysis == null) {
                log.error("❌ Анализ секторов вернул null");
                return ResponseEntity.internalServerError()
                    .body("Ошибка анализа секторов");
            }
            
            // Получаем рекомендации
            var recommendations = sectorManagementService.getDiversificationRecommendations(sectorAnalysis);
            
            // Проверяем результат рекомендаций
            if (recommendations == null) {
                log.error("❌ Рекомендации вернули null");
                return ResponseEntity.internalServerError()
                    .body("Ошибка получения рекомендаций");
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("accountId", accountId);
            response.put("totalValue", totalValue);
            response.put("sectorAnalysis", sectorAnalysis);
            response.put("recommendations", recommendations);
            
            // Проверяем результат
            if (response.isEmpty()) {
                log.error("❌ Ответ пуст");
                return ResponseEntity.internalServerError()
                    .body("Ошибка формирования ответа");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Ошибка при анализе диверсификации секторов: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body("Ошибка анализа: " + e.getMessage());
        }
    }
    
    /**
     * Проверка возможности покупки с учетом диверсификации
     */
    @PostMapping("/validate-purchase")
    public ResponseEntity<?> validatePurchase(
            @RequestParam String accountId,
            @RequestParam String figi,
            @RequestParam BigDecimal positionValue) {
        
        try {
            // Проверяем, что сервисы не null
            if (portfolioService == null) {
                log.error("❌ PortfolioService is null!");
                return ResponseEntity.internalServerError()
                    .body("PortfolioService не инициализирован");
            }
            
            if (sectorManagementService == null) {
                log.error("❌ SectorManagementService is null!");
                return ResponseEntity.internalServerError()
                    .body("SectorManagementService не инициализирован");
            }
            
            var portfolio = portfolioService.getPortfolio(accountId);
            if (portfolio == null) {
                log.error("❌ Портфель не найден для аккаунта: {}", accountId);
                return ResponseEntity.status(404)
                    .body("Портфель не найден для аккаунта: " + accountId);
            }
            
            // Проверяем структуру портфеля
            if (portfolio.getPositions() == null) {
                log.error("❌ Позиции портфеля null для аккаунта: {}", accountId);
                return ResponseEntity.internalServerError()
                    .body("Позиции портфеля не инициализированы");
            }
            
            if (portfolio.getTotalAmountShares() == null) {
                log.error("❌ TotalAmountShares null для аккаунта: {}", accountId);
                return ResponseEntity.internalServerError()
                    .body("Общая стоимость портфеля не инициализирована");
            }
            
            var positions = portfolio.getPositions();
            var totalValue = portfolio.getTotalAmountShares().getValue();
            
            var validation = sectorManagementService.validateSectorDiversification(
                figi, positionValue, totalValue, positions);
            
            // Проверяем результат валидации
            if (validation == null) {
                log.error("❌ Валидация секторов вернула null");
                return ResponseEntity.internalServerError()
                    .body("Ошибка валидации секторов");
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("valid", validation.isValid());
            response.put("sector", validation.getSector());
            response.put("sectorName", validation.getSectorName());
            response.put("violations", validation.getViolations());
            response.put("warnings", validation.getWarnings());
            response.put("newSectorPercentage", validation.getNewSectorPercentage());
            response.put("totalPositions", validation.getTotalPositions());
            response.put("positionsInSector", validation.getPositionsInSector());
            
            if (validation.isValid()) {
                response.put("message", "Покупка разрешена с учетом диверсификации");
            } else {
                response.put("message", "Покупка заблокирована: " + String.join("; ", validation.getViolations()));
            }
            
            // Проверяем результат
            if (response.isEmpty()) {
                log.error("❌ Ответ валидации пуст");
                return ResponseEntity.internalServerError()
                    .body("Ошибка формирования ответа валидации");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Ошибка при валидации покупки: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body("Ошибка валидации: " + e.getMessage());
        }
    }
    
    /**
     * Получение информации о секторе
     */
    @GetMapping("/info/{sector}")
    public ResponseEntity<?> getSectorInfo(@PathVariable String sector) {
        try {
            // Проверяем, что сервис не null
            if (sectorManagementService == null) {
                log.error("❌ SectorManagementService is null!");
                return ResponseEntity.internalServerError()
                    .body("SectorManagementService не инициализирован");
            }
            
            String sectorName = sectorManagementService.getSectorName(sector);
            String riskCategory = sectorManagementService.getSectorRiskCategory(sector);
            
            // Проверяем результат
            if (sectorName == null || riskCategory == null) {
                log.error("❌ Информация о секторе вернула null: sectorName={}, riskCategory={}", sectorName, riskCategory);
                return ResponseEntity.internalServerError()
                    .body("Ошибка получения информации о секторе");
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("sector", sector);
            response.put("sectorName", sectorName);
            response.put("riskCategory", riskCategory);
            response.put("maxExposurePct", "15%");
            response.put("maxPositions", 3);
            
            // Проверяем результат
            if (response.isEmpty()) {
                log.error("❌ Ответ информации о секторе пуст");
                return ResponseEntity.internalServerError()
                    .body("Ошибка формирования ответа информации о секторе");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Ошибка при получении информации о секторе: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body("Ошибка получения информации: " + e.getMessage());
        }
    }
    
    /**
     * Простой тестовый endpoint для проверки работы сервиса
     */
    @GetMapping("/test")
    public ResponseEntity<?> testService() {
        try {
            log.info("🧪 Тестирование SectorManagementService...");
            
            // Проверяем, что сервис не null
            if (sectorManagementService == null) {
                log.error("❌ SectorManagementService is null!");
                return ResponseEntity.internalServerError()
                    .body("SectorManagementService не инициализирован");
            }
            
            // Проверяем базовые методы
            String testSector = sectorManagementService.getSectorName("BANKS");
            String testRisk = sectorManagementService.getSectorRiskCategory("BANKS");
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "SectorManagementService работает!");
            response.put("testSector", testSector);
            response.put("testRisk", testRisk);
            response.put("timestamp", System.currentTimeMillis());
            
            log.info("✅ SectorManagementService тест пройден успешно");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Ошибка тестирования SectorManagementService: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body("Ошибка тестирования: " + e.getMessage());
        }
    }
    
    /**
     * Получение списка всех секторов
     */
    @GetMapping("/list")
    public ResponseEntity<?> getAllSectors() {
        try {
            // Проверяем, что сервис не null
            if (sectorManagementService == null) {
                log.error("❌ SectorManagementService is null!");
                return ResponseEntity.internalServerError()
                    .body("SectorManagementService не инициализирован");
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Список российских секторов экономики");
            Map<String, String> sectors = new HashMap<>();
            sectors.put("BANKS", "Банки и финансы (HIGH риск)");
            sectors.put("OIL_GAS", "Нефть и газ (MEDIUM риск)");
            sectors.put("METALS", "Металлургия (HIGH риск)");
            sectors.put("TELECOM", "Телекоммуникации (LOW риск)");
            sectors.put("RETAIL", "Розничная торговля (MEDIUM риск)");
            sectors.put("TRANSPORT", "Транспорт (MEDIUM риск)");
            sectors.put("CHEMICALS", "Химическая промышленность (HIGH риск)");
            sectors.put("CONSTRUCTION", "Строительство (HIGH риск)");
            sectors.put("AGRICULTURE", "Сельское хозяйство (MEDIUM риск)");
            sectors.put("TECH", "Технологии (HIGH риск)");
            sectors.put("UTILITIES", "Коммунальные услуги (LOW риск)");
            sectors.put("REAL_ESTATE", "Недвижимость (MEDIUM риск)");
            sectors.put("HEALTHCARE", "Здравоохранение (LOW риск)");
            sectors.put("CONSUMER_GOODS", "Товары народного потребления (LOW риск)");
            sectors.put("OTHER", "Прочие (MEDIUM риск)");
            
            // Проверяем результат
            if (sectors.isEmpty()) {
                log.error("❌ Список секторов пуст");
                return ResponseEntity.internalServerError()
                    .body("Ошибка получения списка секторов");
            }
            
            response.put("sectors", sectors);
            
            // Проверяем результат
            if (response.isEmpty()) {
                log.error("❌ Ответ списка секторов пуст");
                return ResponseEntity.internalServerError()
                    .body("Ошибка формирования ответа списка секторов");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Ошибка при получении списка секторов: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body("Ошибка получения списка: " + e.getMessage());
        }
    }
    
    /**
     * Получение статистики по секторам
     */
    @GetMapping("/stats/{accountId}")
    public ResponseEntity<?> getSectorStats(@PathVariable String accountId) {
        try {
            log.info("📊 Получение статистики секторов для аккаунта: {}", accountId);
            
            // Проверяем, что сервисы не null
            if (portfolioService == null) {
                log.error("❌ PortfolioService is null!");
                return ResponseEntity.internalServerError()
                    .body("PortfolioService не инициализирован");
            }
            
            if (sectorManagementService == null) {
                log.error("❌ SectorManagementService is null!");
                return ResponseEntity.internalServerError()
                    .body("SectorManagementService не инициализирован");
            }
            
            var portfolio = portfolioService.getPortfolio(accountId);
            if (portfolio == null) {
                log.error("❌ Портфель не найден для аккаунта: {}", accountId);
                return ResponseEntity.status(404)
                    .body("Портфель не найден для аккаунта: " + accountId);
            }
            
            // Проверяем структуру портфеля
            if (portfolio.getPositions() == null) {
                log.error("❌ Позиции портфеля null для аккаунта: {}", accountId);
                return ResponseEntity.internalServerError()
                    .body("Позиции портфеля не инициализированы");
            }
            
            if (portfolio.getTotalAmountShares() == null) {
                log.error("❌ TotalAmountShares null для аккаунта: {}", accountId);
                return ResponseEntity.internalServerError()
                    .body("Общая стоимость портфеля не инициализирована");
            }
            
            log.info("📊 Портфель получен: positions={}, totalValue={}", 
                portfolio.getPositions().size(), portfolio.getTotalAmountShares().getValue());
            
            var positions = portfolio.getPositions();
            var totalValue = portfolio.getTotalAmountShares().getValue();
            
            if (totalValue == null || totalValue.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("⚠️ Нулевая или отрицательная стоимость портфеля: {}", totalValue);
                return ResponseEntity.badRequest()
                    .body("Нулевая стоимость портфеля: " + totalValue);
            }
            
            var sectorAnalysis = sectorManagementService.analyzeCurrentSectors(positions, totalValue);
            log.info("📊 Анализ секторов выполнен: {}", sectorAnalysis.size());
            
            // Проверяем результат анализа
            if (sectorAnalysis == null) {
                log.error("❌ Анализ секторов вернул null");
                return ResponseEntity.internalServerError()
                    .body("Ошибка анализа секторов");
            }
            
            // Подсчитываем статистику
            int totalSectors = sectorAnalysis.size();
            int highRiskSectors = 0;
            int mediumRiskSectors = 0;
            int lowRiskSectors = 0;
            
            BigDecimal highRiskExposure = BigDecimal.ZERO;
            BigDecimal mediumRiskExposure = BigDecimal.ZERO;
            BigDecimal lowRiskExposure = BigDecimal.ZERO;
            
            for (var entry : sectorAnalysis.entrySet()) {
                String sector = entry.getKey();
                var analysis = entry.getValue();
                String riskCategory = sectorManagementService.getSectorRiskCategory(sector);
                
                switch (riskCategory) {
                    case "HIGH":
                        highRiskSectors++;
                        highRiskExposure = highRiskExposure.add(analysis.getTotalValue());
                        break;
                    case "MEDIUM":
                        mediumRiskSectors++;
                        mediumRiskExposure = mediumRiskExposure.add(analysis.getTotalValue());
                        break;
                    case "LOW":
                        lowRiskSectors++;
                        lowRiskExposure = lowRiskExposure.add(analysis.getTotalValue());
                        break;
                }
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("accountId", accountId);
            response.put("totalValue", totalValue);
            response.put("totalSectors", totalSectors);
            Map<String, Object> sectorDistribution = new HashMap<>();
            
            Map<String, Object> highRisk = new HashMap<>();
            highRisk.put("count", highRiskSectors);
            highRisk.put("exposure", highRiskExposure);
            highRisk.put("percentage", highRiskExposure.divide(totalValue, 4, java.math.RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)));
            
            Map<String, Object> mediumRisk = new HashMap<>();
            mediumRisk.put("count", mediumRiskSectors);
            mediumRisk.put("exposure", mediumRiskExposure);
            mediumRisk.put("percentage", mediumRiskExposure.divide(totalValue, 4, java.math.RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)));
            
            Map<String, Object> lowRisk = new HashMap<>();
            lowRisk.put("count", lowRiskSectors);
            lowRisk.put("exposure", lowRiskExposure);
            lowRisk.put("percentage", lowRiskExposure.divide(totalValue, 4, java.math.RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)));
            
            sectorDistribution.put("highRisk", highRisk);
            sectorDistribution.put("mediumRisk", mediumRisk);
            sectorDistribution.put("lowRisk", lowRisk);
            
            // Проверяем результат
            if (sectorDistribution.isEmpty()) {
                log.error("❌ Распределение секторов пусто");
                return ResponseEntity.internalServerError()
                    .body("Ошибка расчета распределения секторов");
            }
            
            response.put("sectorDistribution", sectorDistribution);
            response.put("sectorAnalysis", sectorAnalysis);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Ошибка при получении статистики секторов: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body("Ошибка получения статистики: " + e.getMessage());
        }
    }
}
