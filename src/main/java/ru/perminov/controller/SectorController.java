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
            
            var portfolio = portfolioService.getPortfolio(accountId);
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
            
            // Получаем рекомендации
            var recommendations = sectorManagementService.getDiversificationRecommendations(sectorAnalysis);
            
            Map<String, Object> response = new HashMap<>();
            response.put("accountId", accountId);
            response.put("totalValue", totalValue);
            response.put("sectorAnalysis", sectorAnalysis);
            response.put("recommendations", recommendations);
            
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
            var portfolio = portfolioService.getPortfolio(accountId);
            var positions = portfolio.getPositions();
            var totalValue = portfolio.getTotalAmountShares().getValue();
            
            var validation = sectorManagementService.validateSectorDiversification(
                figi, positionValue, totalValue, positions);
            
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
            String sectorName = sectorManagementService.getSectorName(sector);
            String riskCategory = sectorManagementService.getSectorRiskCategory(sector);
            
            Map<String, Object> response = new HashMap<>();
            response.put("sector", sector);
            response.put("sectorName", sectorName);
            response.put("riskCategory", riskCategory);
            response.put("maxExposurePct", "15%");
            response.put("maxPositions", 3);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Ошибка при получении информации о секторе: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body("Ошибка получения информации: " + e.getMessage());
        }
    }
    
    /**
     * Получение списка всех секторов
     */
    @GetMapping("/list")
    public ResponseEntity<?> getAllSectors() {
        try {
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
            response.put("sectors", sectors);
            
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
            
            var portfolio = portfolioService.getPortfolio(accountId);
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
