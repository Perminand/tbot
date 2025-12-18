package ru.perminov.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.perminov.service.TradingSettingsService;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
@Slf4j
public class SettingsController {

    private final TradingSettingsService settingsService;

    @GetMapping(value = "/get", produces = "text/plain; charset=UTF-8")
    public ResponseEntity<String> get(@RequestParam String key) {
        try {
            String v = settingsService.getString(key, "");
            log.debug("GET setting: key={}, value={}", key, v);
            return ResponseEntity.ok(v == null ? "" : v);
        } catch (Exception e) {
            log.error("Error getting setting {}: {}", key, e.getMessage(), e);
            // Никогда не падаем 500 для UI настроек
            return ResponseEntity.ok("");
        }
    }

    @PostMapping(value = "/set")
    public ResponseEntity<?> set(@RequestParam String key, @RequestParam String value,
                                 @RequestParam(required = false) String description) {
        try {
            log.info("SET setting: key={}, value={}, description={}", key, value, description);
            settingsService.upsert(key, value, description != null ? description : "");
            // Проверяем, что значение сохранилось
            String savedValue = settingsService.getString(key, "");
            log.info("SET setting confirmed: key={}, savedValue={}", key, savedValue);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error setting {}={}: {}", key, value, e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleAny(Exception e) {
        log.error("Settings controller error: {}", e.getMessage(), e);
        // Возвращаем пустой ответ, чтобы UI не падал на настройках
        return ResponseEntity.ok("");
    }
}


