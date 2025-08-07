package ru.perminov.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.perminov.service.TradingModeService;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/trading-mode")
@RequiredArgsConstructor
public class TradingModeController {
    
    private final TradingModeService tradingModeService;
    
    @GetMapping("/status")
    public Map<String, Object> getTradingModeStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("mode", tradingModeService.getCurrentMode());
        status.put("isSandbox", tradingModeService.isSandboxMode());
        status.put("isProduction", tradingModeService.isProductionMode());
        status.put("displayName", tradingModeService.getModeDisplayName());
        status.put("badgeClass", tradingModeService.getModeBadgeClass());
        status.put("modeInfo", tradingModeService.getModeInfo());
        status.put("lastUpdate", tradingModeService.getLastUpdateTime());
        return status;
    }
    
    @PostMapping("/switch")
    public ResponseEntity<Map<String, Object>> switchTradingMode(@RequestParam("mode") String mode) {
        Map<String, Object> response = new HashMap<>();
        
        // Проверка безопасности переключения
        if (!tradingModeService.isSafeToSwitch(mode)) {
            response.put("success", false);
            response.put("message", "Небезопасное переключение режима. Требуется подтверждение.");
            response.put("requiresConfirmation", true);
            return ResponseEntity.badRequest().body(response);
        }
        
        // Переключение режима
        boolean success = tradingModeService.switchTradingMode(mode);
        
        if (success) {
            response.put("success", true);
            response.put("message", "Режим успешно переключен на: " + 
                (mode.equals("sandbox") ? "Песочница" : "Реальная торговля"));
            response.put("currentMode", tradingModeService.getCurrentMode());
            response.put("displayName", tradingModeService.getModeDisplayName());
            response.put("badgeClass", tradingModeService.getModeBadgeClass());
            response.put("modeInfo", tradingModeService.getModeInfo());
            response.put("lastUpdate", tradingModeService.getLastUpdateTime());
            return ResponseEntity.ok(response);
        } else {
            response.put("success", false);
            response.put("message", "Неверный режим: " + mode);
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    @PostMapping("/switch-confirmed")
    public ResponseEntity<Map<String, Object>> switchTradingModeConfirmed(@RequestParam("mode") String mode) {
        Map<String, Object> response = new HashMap<>();
        
        // Принудительное переключение режима
        boolean success = tradingModeService.switchTradingMode(mode);
        
        if (success) {
            response.put("success", true);
            response.put("message", "Режим переключен на: " + 
                (mode.equals("sandbox") ? "Песочница" : "Реальная торговля"));
            response.put("currentMode", tradingModeService.getCurrentMode());
            response.put("displayName", tradingModeService.getModeDisplayName());
            response.put("badgeClass", tradingModeService.getModeBadgeClass());
            response.put("modeInfo", tradingModeService.getModeInfo());
            response.put("lastUpdate", tradingModeService.getLastUpdateTime());
            if (mode.equals("production")) {
                response.put("warning", "ВНИМАНИЕ! Режим реальной торговли активен. Все операции будут выполняться с реальными деньгами!");
            }
            return ResponseEntity.ok(response);
        } else {
            response.put("success", false);
            response.put("message", "Ошибка переключения режима");
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> resetTradingMode() {
        Map<String, Object> response = new HashMap<>();
        
        boolean success = tradingModeService.resetToDefault();
        
        if (success) {
            response.put("success", true);
            response.put("message", "Настройки режима торговли сброшены к значениям по умолчанию");
            response.put("currentMode", tradingModeService.getCurrentMode());
            response.put("displayName", tradingModeService.getModeDisplayName());
            response.put("badgeClass", tradingModeService.getModeBadgeClass());
            response.put("modeInfo", tradingModeService.getModeInfo());
            response.put("lastUpdate", tradingModeService.getLastUpdateTime());
            return ResponseEntity.ok(response);
        } else {
            response.put("success", false);
            response.put("message", "Ошибка сброса настроек");
            return ResponseEntity.badRequest().body(response);
        }
    }
} 