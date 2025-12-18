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
    public ResponseEntity<String> get(@RequestParam("key") String key) {
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
    public ResponseEntity<?> set(@RequestParam("key") String key, 
                                 @RequestParam("value") String value,
                                 @RequestParam(value = "description", required = false) String description) {
        try {
            String trimmedValue = value != null ? value.trim() : "";
            log.info("🔵 SET setting START: key={}, value={} (trimmed: '{}'), description={}", key, value, trimmedValue, description);
            
            // Сохраняем значение
            settingsService.upsert(key, trimmedValue, description != null ? description : "");
            
            log.info("🔵 SET setting SUCCESS: key={}, value='{}'", key, trimmedValue);
            
            // Возвращаем успешный ответ с сохраненным значением для подтверждения
            return ResponseEntity.ok(trimmedValue);
        } catch (Exception e) {
            log.error("❌ Error setting {}={}: {}", key, value, e.getMessage(), e);
            e.printStackTrace(); // Выводим stack trace для отладки
            // Возвращаем детальную ошибку для отладки
            return ResponseEntity.status(500).body("Error: " + e.getMessage() + " | Class: " + e.getClass().getSimpleName());
        }
    }

    /**
     * Тестовый endpoint для проверки значения напрямую из БД
     */
    @GetMapping(value = "/debug", produces = "application/json")
    public ResponseEntity<?> debug(@RequestParam("key") String key) {
        try {
            // Читаем напрямую из репозитория через сервис
            var opt = settingsService.getSetting(key);
            if (opt.isPresent()) {
                var setting = opt.get();
                return ResponseEntity.ok(java.util.Map.of(
                    "key", key,
                    "found", true,
                    "id", setting.getId(),
                    "value", setting.getValue() != null ? setting.getValue() : "NULL",
                    "valueLength", setting.getValue() != null ? setting.getValue().length() : 0,
                    "description", setting.getDescription() != null ? setting.getDescription() : ""
                ));
            } else {
                return ResponseEntity.ok(java.util.Map.of(
                    "key", key,
                    "found", false
                ));
            }
        } catch (Exception e) {
            log.error("Debug error for key {}: {}", key, e.getMessage(), e);
            return ResponseEntity.status(500).body(java.util.Map.of("error", e.getMessage()));
        }
    }

    // Убираем глобальный ExceptionHandler - пусть ошибки обрабатываются Spring'ом
    // @org.springframework.web.bind.annotation.ExceptionHandler(Exception.class)
    // public ResponseEntity<String> handleAny(Exception e) {
    //     log.error("Settings controller error: {}", e.getMessage(), e);
    //     // Возвращаем пустой ответ, чтобы UI не падал на настройках
    //     return ResponseEntity.ok("");
    // }
}


