package ru.perminov.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.core.InvestApi;
import ru.perminov.repository.TradingSettingsRepository;
import ru.perminov.model.TradingSettings;

import jakarta.annotation.PostConstruct;

@Slf4j
@Service
public class InvestApiManager {
    
    @Value("${tinkoff.api.sandbox-token}")
    private String sandboxToken;
    
    @Value("${tinkoff.api.production-token}")
    private String productionToken;
    
    @Value("${tinkoff.api.default-mode:production}")
    private String defaultMode;
    
    private InvestApi currentInvestApi;
    private String currentMode;
    private static final String TRADING_MODE_KEY = "trading_mode";

    private final TradingSettingsRepository settingsRepository;
    
    public InvestApiManager(TradingSettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    @PostConstruct
    public void init() {
        // Пытаемся прочитать режим из БД, чтобы не путать sandbox/production при рестарте
        try {
            TradingSettings settings = settingsRepository.findByKey(TRADING_MODE_KEY).orElse(null);
            String initialMode = settings != null ? settings.getValue() : defaultMode;
            
            // Проверяем, что токен для сохраненного режима настроен
            if (!isTokenConfigured(initialMode)) {
                log.error("КРИТИЧЕСКАЯ ОШИБКА: Токен для режима {} не настроен!", initialMode);
                log.error("Проверьте настройки токенов в application.yml или переменных окружения");
                
                // НЕ переключаемся автоматически на другой режим!
                // Вместо этого пытаемся использовать режим по умолчанию
                if (isTokenConfigured(defaultMode)) {
                    log.warn("Используем режим по умолчанию: {}", defaultMode);
                    initialMode = defaultMode;
                } else {
                    log.error("Токен для режима по умолчанию {} тоже не настроен!", defaultMode);
                    log.error("Система не может быть инициализирована. Проверьте конфигурацию токенов.");
                    throw new IllegalStateException("Не настроены токены для ни одного режима торговли");
                }
            }
            
            switchToMode(initialMode);
            log.info("✅ InvestApiManager успешно инициализирован в режиме: {}", initialMode);
            
        } catch (Exception e) {
            log.error("КРИТИЧЕСКАЯ ОШИБКА при инициализации InvestApiManager: {}", e.getMessage());
            log.error("Система не может быть запущена. Проверьте:");
            log.error("1. Настройки токенов в application.yml");
            log.error("2. Переменные окружения TINKOFF_SANDBOX_TOKEN и TINKOFF_PRODUCTION_TOKEN");
            log.error("3. Подключение к базе данных");
            log.error("4. Права доступа к API Tinkoff");
            
            // НЕ переключаемся автоматически на sandbox!
            // Вместо этого останавливаем инициализацию
            throw new RuntimeException("Не удалось инициализировать InvestApiManager: " + e.getMessage(), e);
        }
    }
    
    /**
     * Переключение на указанный режим
     */
    public synchronized void switchToMode(String mode) {
        log.info("InvestApiManager.switchToMode() вызван с режимом: {} (текущий: {})", mode, currentMode);
        
        if (currentMode != null && currentMode.equals(mode)) {
            log.debug("Режим {} уже активен", mode);
            return;
        }
        
        // Дополнительная проверка безопасности
        if (currentMode != null && "production".equals(currentMode) && "sandbox".equals(mode)) {
            log.warn("⚠️ ВНИМАНИЕ: Попытка переключения с production на sandbox режим");
            log.warn("Это может быть небезопасно во время активной торговли");
        }
        
        try {
            InvestApi newInvestApi = createInvestApi(mode);
            
            // Просто заменяем предыдущий экземпляр
            if (currentInvestApi != null) {
                log.info("Заменяем предыдущий InvestApi экземпляр (было: {}, стало: {})", currentMode, mode);
            }
            
            currentInvestApi = newInvestApi;
            currentMode = mode;
            
            log.info("✅ Успешно переключен на режим: {}", mode);
            
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
            log.error("InvestApi не инициализирован. Проверьте логи инициализации.");
            throw new IllegalStateException("InvestApi не инициализирован. Проверьте логи инициализации.");
        }
        log.debug("Возвращаем текущий InvestApi для режима: {}", currentMode);
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
