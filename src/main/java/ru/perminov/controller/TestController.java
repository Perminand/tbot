package ru.perminov.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.perminov.service.InvestApiManager;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
@Slf4j
public class TestController {
    
    private final InvestApiManager investApiManager;
    
    @Value("${tinkoff.api.sandbox-token}")
    private String sandboxToken;
    
    @Value("${tinkoff.api.production-token}")
    private String productionToken;
    
    @Value("${tinkoff.api.default-mode:sandbox}")
    private String defaultMode;
    
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("application", "Tinkoff Trading Bot");
        status.put("version", "1.0.0");
        status.put("sandboxTokenConfigured", sandboxToken != null && !sandboxToken.isEmpty());
        status.put("productionTokenConfigured", productionToken != null && !productionToken.isEmpty());
        status.put("defaultMode", defaultMode);
        status.put("sandboxTokenLength", sandboxToken != null ? sandboxToken.length() : 0);
        status.put("productionTokenLength", productionToken != null ? productionToken.length() : 0);
        status.put("sandboxTokenPreview", sandboxToken != null ? sandboxToken.substring(0, Math.min(10, sandboxToken.length())) + "..." : "null");
        status.put("productionTokenPreview", productionToken != null ? productionToken.substring(0, Math.min(10, productionToken.length())) + "..." : "null");
        
        return ResponseEntity.ok(status);
    }
    
    @GetMapping("/connection")
    public ResponseEntity<Map<String, Object>> testConnection() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Попробуем получить аккаунты для проверки подключения
            var accounts = investApiManager.getCurrentInvestApi().getUserService().getAccounts().get();
            result.put("success", true);
            result.put("accountsCount", accounts.size());
            result.put("message", "Successfully connected to Tinkoff API");
            
            if (!accounts.isEmpty()) {
                result.put("firstAccountId", accounts.get(0).getId());
                result.put("firstAccountName", accounts.get(0).getName());
            }
            
        } catch (Exception e) {
            log.error("Connection test failed", e);
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("errorType", e.getClass().getSimpleName());
        }
        
        return ResponseEntity.ok(result);
    }
} 