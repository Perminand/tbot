package ru.perminov.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.tinkoff.piapi.core.InvestApi;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
@Slf4j
public class TestController {
    
    private final InvestApi investApi;
    
    @Value("${tinkoff.api.token}")
    private String token;
    
    @Value("${tinkoff.api.use-sandbox:true}")
    private boolean useSandbox;
    
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("application", "Tinkoff Trading Bot");
        status.put("version", "1.0.0");
        status.put("tokenConfigured", token != null && !token.isEmpty());
        status.put("useSandbox", useSandbox);
        status.put("tokenLength", token != null ? token.length() : 0);
        status.put("tokenPreview", token != null ? token.substring(0, Math.min(10, token.length())) + "..." : "null");
        
        return ResponseEntity.ok(status);
    }
    
    @GetMapping("/connection")
    public ResponseEntity<Map<String, Object>> testConnection() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Попробуем получить аккаунты для проверки подключения
            var accounts = investApi.getUserService().getAccounts().get();
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