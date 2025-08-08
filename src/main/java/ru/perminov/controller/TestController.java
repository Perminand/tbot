package ru.perminov.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.perminov.service.InvestApiManager;
import ru.tinkoff.piapi.core.InvestApi;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
@Slf4j
public class TestController {
    private final InvestApiManager investApiManager;
    
    @GetMapping("/invest-api")
    public ResponseEntity<?> testInvestApi() {
        try {
            log.info("Тестирование InvestApi...");
            
            InvestApi api = investApiManager.getCurrentInvestApi();
            if (api == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "InvestApi не инициализирован"));
            }
            
            String currentMode = investApiManager.getCurrentMode();
            String availableModes = investApiManager.getAvailableModesInfo();
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("currentMode", currentMode);
            response.put("availableModes", availableModes);
            response.put("message", "InvestApi работает корректно");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Ошибка при тестировании InvestApi", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Ошибка InvestApi: " + e.getMessage());
            error.put("errorType", e.getClass().getSimpleName());
            return ResponseEntity.status(500).body(error);
        }
    }

    @GetMapping("/accounts")
    public ResponseEntity<?> testAccounts() {
        try {
            log.info("Тестирование получения счетов...");
            
            InvestApi api = investApiManager.getCurrentInvestApi();
            log.info("InvestApi получен: {}", api != null ? "OK" : "NULL");
            
            if (api == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "InvestApi не инициализирован"));
            }
            
            log.info("Получаем счета через API...");
            var accountsFuture = api.getUserService().getAccounts();
            log.info("Future создан, ожидаем результат...");
            
            var accounts = accountsFuture.join();
            log.info("Счета получены, количество: {}", accounts.size());
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("accountsCount", accounts.size());
            
            // Добавляем информацию о каждом счете
            var accountsInfo = accounts.stream()
                .map(account -> {
                    Map<String, Object> accountInfo = new HashMap<>();
                    accountInfo.put("id", account.getId());
                    accountInfo.put("name", account.getName());
                    accountInfo.put("type", account.getType());
                    accountInfo.put("status", account.getStatus());
                    return accountInfo;
                })
                .collect(java.util.stream.Collectors.toList());
            
            response.put("accounts", accountsInfo);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Ошибка при получении счетов", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Ошибка получения счетов: " + e.getMessage());
            error.put("errorType", e.getClass().getSimpleName());
            error.put("stackTrace", e.getStackTrace());
            return ResponseEntity.status(500).body(error);
        }
    }

    @PostMapping("/switch-mode/{mode}")
    public ResponseEntity<?> switchMode(@PathVariable("mode") String mode) {
        try {
            log.info("Переключение на режим: {}", mode);
            
            investApiManager.switchToMode(mode);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("currentMode", investApiManager.getCurrentMode());
            response.put("message", "Успешно переключен на режим: " + mode);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Ошибка при переключении режима на {}", mode, e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Ошибка переключения режима: " + e.getMessage());
            error.put("errorType", e.getClass().getSimpleName());
            return ResponseEntity.status(500).body(error);
        }
    }

    @GetMapping("/tokens")
    public ResponseEntity<?> checkTokens() {
        try {
            log.info("Проверка токенов...");
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("sandboxConfigured", investApiManager.isTokenConfigured("sandbox"));
            response.put("productionConfigured", investApiManager.isTokenConfigured("production"));
            response.put("currentMode", investApiManager.getCurrentMode());
            response.put("availableModes", investApiManager.getAvailableModesInfo());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Ошибка при проверке токенов", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Ошибка проверки токенов: " + e.getMessage());
            error.put("errorType", e.getClass().getSimpleName());
            return ResponseEntity.status(500).body(error);
        }
    }
} 