package ru.perminov.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.perminov.service.TradingModeService;
import ru.perminov.service.TradingModeProtectionService;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/trading-mode")
@RequiredArgsConstructor
@Slf4j
public class TradingModeController {
    
    private final TradingModeService tradingModeService;
    private final TradingModeProtectionService protectionService;
    
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getTradingModeStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("currentMode", tradingModeService.getCurrentMode());
        status.put("displayName", tradingModeService.getModeDisplayName());
        status.put("badgeClass", tradingModeService.getModeBadgeClass());
        status.put("modeInfo", tradingModeService.getModeInfo());
        status.put("lastUpdate", tradingModeService.getLastUpdateTime());
        status.put("isTradingActive", protectionService.isTradingActive());
        status.put("isModeValid", protectionService.validateTradingMode());
        status.put("protectionStatus", protectionService.getProtectionStatus());
        
        return ResponseEntity.ok(status);
    }
    
    @PostMapping("/switch")
    public ResponseEntity<Map<String, Object>> switchTradingMode(@RequestParam("mode") String mode) {
        Map<String, Object> response = new HashMap<>();
        
        log.info("Запрос на переключение режима торговли: {} -> {}", tradingModeService.getCurrentMode(), mode);
        
        // Проверка защиты от несанкционированного переключения
        if (!protectionService.isModeSwitchSafe(mode)) {
            response.put("success", false);
            response.put("message", "Переключение режима заблокировано системой защиты");
            response.put("reason", "Активная торговля или рассинхронизация режимов");
            response.put("requiresConfirmation", true);
            return ResponseEntity.badRequest().body(response);
        }
        
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
            response.put("protectionStatus", protectionService.getProtectionStatus());
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
        
        log.warn("Принудительное переключение режима торговли: {} -> {}", tradingModeService.getCurrentMode(), mode);
        
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
            response.put("protectionStatus", protectionService.getProtectionStatus());
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
        
        log.info("Сброс режима торговли к значению по умолчанию");
        
        boolean success = tradingModeService.resetToDefault();
        
        if (success) {
            response.put("success", true);
            response.put("message", "Режим торговли сброшен к значению по умолчанию");
            response.put("currentMode", tradingModeService.getCurrentMode());
            response.put("displayName", tradingModeService.getModeDisplayName());
            response.put("badgeClass", tradingModeService.getModeBadgeClass());
            response.put("modeInfo", tradingModeService.getModeInfo());
            response.put("lastUpdate", tradingModeService.getLastUpdateTime());
            response.put("protectionStatus", protectionService.getProtectionStatus());
            return ResponseEntity.ok(response);
        } else {
            response.put("success", false);
            response.put("message", "Ошибка сброса режима торговли");
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    @PostMapping("/synchronize")
    public ResponseEntity<Map<String, Object>> synchronizeTradingModes() {
        Map<String, Object> response = new HashMap<>();
        
        log.info("Запрос на синхронизацию режимов торговли");
        
        boolean success = protectionService.forceSynchronizeModes();
        
        if (success) {
            response.put("success", true);
            response.put("message", "Режимы торговли синхронизированы");
            response.put("currentMode", tradingModeService.getCurrentMode());
            response.put("displayName", tradingModeService.getModeDisplayName());
            response.put("badgeClass", tradingModeService.getModeBadgeClass());
            response.put("modeInfo", tradingModeService.getModeInfo());
            response.put("lastUpdate", tradingModeService.getLastUpdateTime());
            response.put("protectionStatus", protectionService.getProtectionStatus());
            return ResponseEntity.ok(response);
        } else {
            response.put("success", false);
            response.put("message", "Ошибка синхронизации режимов торговли");
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    @PostMapping("/set-trading-active")
    public ResponseEntity<Map<String, Object>> setTradingActive(@RequestParam("active") boolean active) {
        Map<String, Object> response = new HashMap<>();
        
        log.info("Установка флага активной торговли: {}", active);
        
        protectionService.setTradingActive(active);
        
        response.put("success", true);
        response.put("message", "Флаг активной торговли установлен: " + (active ? "АКТИВНА" : "НЕАКТИВНА"));
        response.put("isTradingActive", protectionService.isTradingActive());
        response.put("protectionStatus", protectionService.getProtectionStatus());
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/protection-status")
    public ResponseEntity<Map<String, Object>> getProtectionStatus() {
        Map<String, Object> response = new HashMap<>();
        
        response.put("success", true);
        response.put("protectionStatus", protectionService.getProtectionStatus());
        response.put("isTradingActive", protectionService.isTradingActive());
        response.put("isModeValid", protectionService.validateTradingMode());
        response.put("currentMode", tradingModeService.getCurrentMode());
        
        return ResponseEntity.ok(response);
    }
} 