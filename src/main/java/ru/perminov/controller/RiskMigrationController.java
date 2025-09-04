package ru.perminov.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.perminov.service.RiskRuleMigrationService;

import java.util.Map;

/**
 * 🚀 КОНТРОЛЛЕР МИГРАЦИИ: REST API для обновления SL/TP правил
 */
@RestController
@RequestMapping("/api/migration")
@RequiredArgsConstructor
@Slf4j
public class RiskMigrationController {
    
    private final RiskRuleMigrationService migrationService;
    
    /**
     * 🎯 МАССОВАЯ МИГРАЦИЯ: Обновить все существующие SL/TP правила
     * GET /api/migration/update-all-sltp
     */
    @GetMapping("/update-all-sltp")
    public ResponseEntity<Map<String, Object>> updateAllSLTP() {
        log.info("🚀 API запрос: Массовая миграция всех SL/TP правил");
        
        try {
            RiskRuleMigrationService.MigrationResult result = migrationService.migrateAllRulesToNewValues();
            
            if (result.isSuccess()) {
                return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Миграция завершена успешно",
                    "riskRulesUpdated", result.riskRulesUpdated,
                    "positionStatesUpdated", result.positionStatesUpdated,
                    "virtualOrdersUpdated", result.virtualOrdersUpdated,
                    "newValues", Map.of(
                        "stopLoss", "2%",
                        "takeProfit", "6%", 
                        "trailing", "3%"
                    )
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Ошибка миграции: " + result.error
                ));
            }
            
        } catch (Exception e) {
            log.error("❌ Ошибка API миграции: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "Внутренняя ошибка: " + e.getMessage()
            ));
        }
    }
    
    /**
     * 🎯 ВЫБОРОЧНАЯ МИГРАЦИЯ: Обновить SL/TP для конкретного инструмента
     * POST /api/migration/update-instrument-sltp
     */
    @PostMapping("/update-instrument-sltp")
    public ResponseEntity<Map<String, Object>> updateInstrumentSLTP(@RequestBody Map<String, String> request) {
        String figi = request.get("figi");
        
        if (figi == null || figi.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "FIGI инструмента обязателен"
            ));
        }
        
        log.info("🎯 API запрос: Миграция SL/TP для инструмента {}", figi);
        
        try {
            boolean success = migrationService.migrateRulesForInstrument(figi);
            
            if (success) {
                return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Миграция для " + figi + " завершена успешно",
                    "figi", figi,
                    "newValues", Map.of(
                        "stopLoss", "2%",
                        "takeProfit", "6%",
                        "trailing", "3%"
                    )
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Ошибка миграции для " + figi
                ));
            }
            
        } catch (Exception e) {
            log.error("❌ Ошибка API миграции для {}: {}", figi, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "Внутренняя ошибка: " + e.getMessage()
            ));
        }
    }
    
    /**
     * 📊 СТАТУС МИГРАЦИИ: Показать текущие значения по умолчанию
     * GET /api/migration/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getMigrationStatus() {
        return ResponseEntity.ok(Map.of(
            "status", "ready",
            "message", "Сервис миграции готов к работе",
            "currentDefaults", Map.of(
                "stopLoss", "2% (было 5%)",
                "takeProfit", "6% (было 10%)",
                "trailing", "3% (было 5%)"
            ),
            "endpoints", Map.of(
                "massUpdate", "GET /api/migration/update-all-sltp",
                "instrumentUpdate", "POST /api/migration/update-instrument-sltp",
                "status", "GET /api/migration/status"
            )
        ));
    }
}
