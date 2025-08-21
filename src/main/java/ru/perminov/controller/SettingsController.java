package ru.perminov.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.perminov.service.TradingSettingsService;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final TradingSettingsService settingsService;

    @GetMapping(value = "/get", produces = "text/plain; charset=UTF-8")
    public ResponseEntity<String> get(@RequestParam String key) {
        try {
            String v = settingsService.getString(key, "");
            return ResponseEntity.ok(v == null ? "" : v);
        } catch (Exception e) {
            // Никогда не падаем 500 для UI настроек
            return ResponseEntity.ok("");
        }
    }

    @PostMapping(value = "/set")
    public ResponseEntity<?> set(@RequestParam String key, @RequestParam String value,
                                 @RequestParam(required = false) String description) {
        settingsService.upsert(key, value, description != null ? description : "");
        return ResponseEntity.noContent().build();
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleAny(Exception e) {
        // Возвращаем пустой ответ, чтобы UI не падал на настройках
        return ResponseEntity.ok("");
    }
}


