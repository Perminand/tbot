package ru.perminov.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.perminov.service.SectorManagementService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sectors")
@RequiredArgsConstructor
@Slf4j
public class SectorController {
    
    private final SectorManagementService sectorManagementService;

    /**
     * Получение сектора для конкретного FIGI
     */
    @GetMapping("/{figi}")
    public ResponseEntity<?> getSectorForInstrument(@PathVariable String figi) {
        try {
            String sector = sectorManagementService.getSectorForInstrument(figi);
            String sectorName = sectorManagementService.getSectorName(sector);
            
            Map<String, Object> response = new HashMap<>();
            response.put("figi", figi);
            response.put("sector", sector);
            response.put("sectorName", sectorName);
            response.put("riskCategory", sectorManagementService.getSectorRiskCategory(sector));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка получения сектора для {}: {}", figi, e.getMessage());
            return ResponseEntity.status(500).body("Ошибка: " + e.getMessage());
        }
    }
    
    /**
     * Принудительное обновление сектора через API
     */
    @PostMapping("/{figi}/refresh")
    public ResponseEntity<?> refreshSector(@PathVariable String figi) {
        try {
            sectorManagementService.refreshSectorFromApi(figi);
            
            String sector = sectorManagementService.getSectorForInstrument(figi);
            String sectorName = sectorManagementService.getSectorName(sector);
            
            Map<String, Object> response = new HashMap<>();
            response.put("figi", figi);
            response.put("sector", sector);
            response.put("sectorName", sectorName);
            response.put("message", "Сектор обновлен через API");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка обновления сектора для {}: {}", figi, e.getMessage());
            return ResponseEntity.status(500).body("Ошибка: " + e.getMessage());
        }
    }
    
    /**
     * Массовое обновление секторов
     */
    @PostMapping("/refresh-batch")
    public ResponseEntity<?> refreshSectors(@RequestBody List<String> figis) {
        try {
            sectorManagementService.refreshSectorsFromApi(figis);
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Обновлено секторов: " + figis.size());
            response.put("figis", figis);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка массового обновления секторов: {}", e.getMessage());
            return ResponseEntity.status(500).body("Ошибка: " + e.getMessage());
        }
    }
    
    /**
     * Обновление секторов для всех инструментов портфеля
     */
    @PostMapping("/refresh-portfolio")
    public ResponseEntity<?> refreshPortfolioSectors(@RequestParam String accountId) {
        try {
            // Здесь можно добавить логику получения всех FIGI из портфеля
            // и их обновления
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Функция в разработке");
            response.put("accountId", accountId);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка обновления секторов портфеля: {}", e.getMessage());
            return ResponseEntity.status(500).body("Ошибка: " + e.getMessage());
        }
    }
}