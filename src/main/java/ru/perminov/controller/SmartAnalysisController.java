package ru.perminov.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import ru.perminov.service.SmartAnalysisService;

@RestController
@RequestMapping("/api/smart-analysis")
@RequiredArgsConstructor
@Slf4j
public class SmartAnalysisController {
    
    private final SmartAnalysisService smartAnalysisService;
    
    /**
     * Получение статистики умного анализа
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getAnalysisStats() {
        try {
            log.info("Получение статистики умного анализа");
            
            Map<String, Object> stats = smartAnalysisService.getAnalysisStats();
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("data", stats);
            response.put("message", "Статистика умного анализа получена");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при получении статистики умного анализа: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Ошибка при получении статистики: " + e.getMessage());
            error.put("errorType", e.getClass().getSimpleName());
            return ResponseEntity.status(500).body(error);
        }
    }
    
    /**
     * Проверка состояния резервного режима
     */
    @GetMapping("/fallback-status")
    public ResponseEntity<?> getFallbackStatus() {
        try {
            log.info("Проверка состояния резервного режима");
            
            Map<String, Object> fallbackInfo = smartAnalysisService.getFallbackModeInfo();
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("data", fallbackInfo);
            response.put("message", fallbackInfo.get("isInFallbackMode").equals(true) ? 
                "Система работает в резервном режиме" : "Система работает в нормальном режиме");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при проверке резервного режима: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Ошибка при проверке резервного режима: " + e.getMessage());
            error.put("errorType", e.getClass().getSimpleName());
            return ResponseEntity.status(500).body(error);
        }
    }
    
    /**
     * Получение инструментов для быстрого анализа
     */
    @GetMapping("/quick-instruments")
    public ResponseEntity<?> getQuickAnalysisInstruments(@RequestParam("accountId") String accountId) {
        try {
            log.info("Получение инструментов для быстрого анализа, accountId: {}", accountId);
            
            List<ru.perminov.dto.ShareDto> instruments = smartAnalysisService.getInstrumentsForQuickAnalysis(accountId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("accountId", accountId);
            response.put("instrumentsCount", instruments.size());
            response.put("instruments", instruments);
            response.put("message", "Инструменты для быстрого анализа получены");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при получении инструментов для быстрого анализа: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Ошибка при получении инструментов: " + e.getMessage());
            error.put("errorType", e.getClass().getSimpleName());
            return ResponseEntity.status(500).body(error);
        }
    }
    
    /**
     * Получение инструментов для полного анализа
     */
    @GetMapping("/full-instruments")
    public ResponseEntity<?> getFullAnalysisInstruments(@RequestParam("accountId") String accountId) {
        try {
            log.info("Получение инструментов для полного анализа, accountId: {}", accountId);
            
            List<ru.perminov.dto.ShareDto> instruments = smartAnalysisService.getInstrumentsForFullAnalysis(accountId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("accountId", accountId);
            response.put("instrumentsCount", instruments.size());
            response.put("instruments", instruments);
            response.put("message", "Инструменты для полного анализа получены");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при получении инструментов для полного анализа: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Ошибка при получении инструментов: " + e.getMessage());
            error.put("errorType", e.getClass().getSimpleName());
            return ResponseEntity.status(500).body(error);
        }
    }
    
    /**
     * Обновление приоритета инструмента
     */
    @PostMapping("/update-priority")
    public ResponseEntity<?> updateInstrumentPriority(
            @RequestParam("figi") String figi,
            @RequestParam("priority") int priority) {
        try {
            log.info("Обновление приоритета инструмента: {} -> {}", figi, priority);
            
            smartAnalysisService.updateInstrumentPriority(figi, priority);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("figi", figi);
            response.put("priority", priority);
            response.put("message", "Приоритет инструмента обновлен");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при обновлении приоритета: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Ошибка при обновлении приоритета: " + e.getMessage());
            error.put("errorType", e.getClass().getSimpleName());
            return ResponseEntity.status(500).body(error);
        }
    }
    
    /**
     * Получение информации о резервном списке
     */
    @GetMapping("/fallback-info")
    public ResponseEntity<?> getFallbackInfo() {
        try {
            log.info("Получение информации о резервном списке");
            
            Map<String, Object> info = new HashMap<>();
            info.put("description", "Резервный список инструментов используется при ошибках API");
            info.put("purpose", "Обеспечение непрерывной работы бота");
            info.put("activationConditions", new String[]{
                "Ошибка получения инструментов от API Tinkoff",
                "Пустой кэш инструментов",
                "Сетевые проблемы",
                "Превышение лимитов API"
            });
            info.put("instruments", new String[]{
                "Акции: TCS, SBER, GAZP, LKOH, NVTK, ROSN, MGNT, YNDX, VKUS, OZON",
                "Облигации: ОФЗ-26238, ОФЗ-26239, ОФЗ-26240, Сбербанк-001Р, Газпром-001Р",
                "ETF: FXRL, FXUS, FXDE, FXCN, FXGD",
                "Валюты: USD000UTSTOM, EUR_RUB__TOM"
            });
            info.put("totalCount", 20);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("data", info);
            response.put("message", "Информация о резервном списке получена");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при получении информации о резервном списке: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Ошибка при получении информации: " + e.getMessage());
            error.put("errorType", e.getClass().getSimpleName());
            return ResponseEntity.status(500).body(error);
        }
    }
}
