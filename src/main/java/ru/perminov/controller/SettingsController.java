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
            String defaultValue = "";
            String v = settingsService.getString(key, defaultValue);
            String result = v == null ? "" : v.trim();
            log.info("GET setting: key={}, value={}, result={}, isEmpty={}", key, v, result, result.isEmpty());
            return ResponseEntity.ok(result);
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
            String trimmedValue = value != null ? value.trim() : "";
            log.info("SET setting: key={}, value={} (trimmed: {}), description={}", key, value, trimmedValue, description);
            settingsService.upsert(key, trimmedValue, description != null ? description : "");
            // Проверяем, что значение сохранилось
            String savedValue = settingsService.getString(key, "");
            log.info("SET setting confirmed: key={}, savedValue={}, matches={}", key, savedValue, savedValue.equals(trimmedValue));
            if (!savedValue.equals(trimmedValue)) {
                log.warn("⚠️ WARNING: Saved value '{}' does not match requested value '{}' for key '{}'", savedValue, trimmedValue, key);
            }
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


