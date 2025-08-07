package ru.perminov.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.core.InvestApi;

import jakarta.annotation.PostConstruct;

@Slf4j
@Service
public class InvestApiManager {
    
    @Value("${tinkoff.api.sandbox-token}")
    private String sandboxToken;
    
    @Value("${tinkoff.api.production-token}")
    private String productionToken;
    
    @Value("${tinkoff.api.default-mode:sandbox}")
    private String defaultMode;
    
    private InvestApi currentInvestApi;
    private String currentMode;
    
    @PostConstruct
    public void init() {
        // Инициализируем с режимом по умолчанию
        switchToMode(defaultMode);
    }
    
    /**
     * Переключение на указанный режим
     */
    public synchronized void switchToMode(String mode) {
        if (currentMode != null && currentMode.equals(mode)) {
            log.debug("Режим {} уже активен", mode);
            return;
        }
        
        try {
            InvestApi newInvestApi = createInvestApi(mode);
            
            // Просто заменяем предыдущий экземпляр
            if (currentInvestApi != null) {
                log.info("Заменяем предыдущий InvestApi экземпляр");
            }
            
            currentInvestApi = newInvestApi;
            currentMode = mode;
            
            log.info("Успешно переключен на режим: {}", mode);
            
        } catch (Exception e) {
            log.error("Ошибка при переключении на режим {}: {}", mode, e.getMessage());
            throw new RuntimeException("Не удалось переключить режим: " + e.getMessage(), e);
        }
    }
    
    /**
     * Создание InvestApi для указанного режима
     */
    private InvestApi createInvestApi(String mode) {
        switch (mode.toLowerCase()) {
            case "sandbox":
                if (sandboxToken == null || sandboxToken.trim().isEmpty()) {
                    throw new IllegalArgumentException("Токен для песочницы не настроен");
                }
                log.info("Создание InvestApi для песочницы с токеном: {}...", 
                    sandboxToken.substring(0, Math.min(10, sandboxToken.length())));
                return InvestApi.createSandbox(sandboxToken);
                
            case "production":
                if (productionToken == null || productionToken.trim().isEmpty()) {
                    throw new IllegalArgumentException("Токен для реальной торговли не настроен");
                }
                log.info("Создание InvestApi для реальной торговли с токеном: {}...", 
                    productionToken.substring(0, Math.min(10, productionToken.length())));
                return InvestApi.create(productionToken);
                
            default:
                throw new IllegalArgumentException("Неизвестный режим: " + mode);
        }
    }
    
    /**
     * Получение текущего InvestApi
     */
    public synchronized InvestApi getCurrentInvestApi() {
        if (currentInvestApi == null) {
            throw new IllegalStateException("InvestApi не инициализирован");
        }
        return currentInvestApi;
    }
    
    /**
     * Получение текущего режима
     */
    public String getCurrentMode() {
        return currentMode;
    }
    
    /**
     * Проверка, что токен для указанного режима настроен
     */
    public boolean isTokenConfigured(String mode) {
        switch (mode.toLowerCase()) {
            case "sandbox":
                return sandboxToken != null && !sandboxToken.trim().isEmpty();
            case "production":
                return productionToken != null && !productionToken.trim().isEmpty();
            default:
                return false;
        }
    }
    
    /**
     * Получение информации о доступных режимах
     */
    public String getAvailableModesInfo() {
        StringBuilder info = new StringBuilder();
        info.append("Доступные режимы:\n");
        info.append("- Песочница: ").append(isTokenConfigured("sandbox") ? "✅" : "❌").append("\n");
        info.append("- Реальная торговля: ").append(isTokenConfigured("production") ? "✅" : "❌");
        return info.toString();
    }
}
