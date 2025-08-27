package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.perminov.model.TradingSettings;
import ru.perminov.repository.TradingSettingsRepository;
import ru.tinkoff.piapi.core.InvestApi;

import java.time.LocalDateTime;

@Service
@Slf4j
public class TradingModeService {
    
    private final TradingSettingsRepository settingsRepository;
    private final InvestApiManager investApiManager;
    
    // Конструктор
    public TradingModeService(TradingSettingsRepository settingsRepository, InvestApiManager investApiManager) {
        this.settingsRepository = settingsRepository;
        this.investApiManager = investApiManager;
    }
    
    @Value("${tinkoff.api.default-mode:production}")
    private String defaultMode;
    
    private static final String TRADING_MODE_KEY = "trading_mode";
    private static final String SANDBOX_MODE = "sandbox";
    private static final String PRODUCTION_MODE = "production";
    
    public String getCurrentMode() {
        // Сначала пытаемся получить из базы данных
        TradingSettings settings = getTradingModeSettings();
        if (settings != null) {
            return settings.getValue();
        }
        
        // Если нет в БД, используем значение из конфигурации
        return defaultMode;
    }
    
    public boolean isSandboxMode() {
        return SANDBOX_MODE.equals(getCurrentMode());
    }
    
    public boolean isProductionMode() {
        return PRODUCTION_MODE.equals(getCurrentMode());
    }
    
    public String getModeDisplayName() {
        return isSandboxMode() ? "Песочница" : "Реальная торговля";
    }
    
    public String getModeBadgeClass() {
        return isSandboxMode() ? "bg-warning" : "bg-danger";
    }
    
    /**
     * Переключение режима торговли
     */
    public boolean switchTradingMode(String mode) {
        log.info("TradingModeService.switchTradingMode() вызван с режимом: {} (текущий: {})", mode, getCurrentMode());
        
        if (!SANDBOX_MODE.equals(mode) && !PRODUCTION_MODE.equals(mode)) {
            log.error("Неверный режим торговли: {}", mode);
            return false;
        }
        
        // Дополнительная проверка безопасности
        String currentMode = getCurrentMode();
        if (PRODUCTION_MODE.equals(currentMode) && SANDBOX_MODE.equals(mode)) {
            log.warn("⚠️ ВНИМАНИЕ: Попытка переключения с production на sandbox режим");
            log.warn("Это может быть небезопасно во время активной торговли");
        }
        
        try {
            // Проверяем, что токен для этого режима настроен
            if (!investApiManager.isTokenConfigured(mode)) {
                log.error("Токен для режима {} не настроен", mode);
                return false;
            }
            
            // Переключаем InvestApi на новый режим
            investApiManager.switchToMode(mode);
            
            // Сохраняем настройку в БД
            TradingSettings settings = getTradingModeSettings();
            if (settings == null) {
                // Создаем новую запись
                settings = new TradingSettings();
                settings.setKey(TRADING_MODE_KEY);
                settings.setDescription("Режим торговли (sandbox/production)");
            }
            
            settings.setValue(mode);
            settingsRepository.save(settings);
            
            log.info("✅ Режим торговли переключен на: {}", mode);
            return true;
        } catch (Exception e) {
            log.error("Ошибка при переключении режима торговли: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Получение настроек режима торговли из БД
     */
    private TradingSettings getTradingModeSettings() {
        try {
            return settingsRepository.findByKey(TRADING_MODE_KEY).orElse(null);
        } catch (Exception e) {
            log.warn("Ошибка при получении настроек режима торговли: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Получение информации о текущем режиме
     */
    public String getModeInfo() {
        if (isSandboxMode()) {
            return "Режим песочницы активен. Все операции выполняются в тестовой среде.";
        } else {
            return "РЕЖИМ РЕАЛЬНОЙ ТОРГОВЛИ АКТИВЕН! Все операции будут выполняться с реальными деньгами.";
        }
    }
    
    /**
     * Проверка безопасности переключения режима
     */
    public boolean isSafeToSwitch(String newMode) {
        String currentMode = getCurrentMode();
        log.info("Проверка безопасности переключения: {} -> {}", currentMode, newMode);
        
        // В песочнице можно переключаться свободно
        if (SANDBOX_MODE.equals(currentMode)) {
            log.info("Переключение из песочницы безопасно");
            return true;
        }
        
        // В продакшене переключение в песочницу требует подтверждения
        if (PRODUCTION_MODE.equals(currentMode) && SANDBOX_MODE.equals(newMode)) {
            log.warn("⚠️ Переключение с production на sandbox требует подтверждения");
            return false;
        }
        
        // Переключение в продакшн требует подтверждения
        if (PRODUCTION_MODE.equals(newMode)) {
            log.warn("⚠️ Переключение в production режим требует подтверждения");
            return false;
        }
        
        return true;
    }
    
    /**
     * Получение времени последнего обновления настроек
     */
    public LocalDateTime getLastUpdateTime() {
        TradingSettings settings = getTradingModeSettings();
        return settings != null ? settings.getUpdatedAt() : null;
    }
    
    /**
     * Сброс настроек к значениям по умолчанию
     */
    public boolean resetToDefault() {
        try {
            log.info("Сброс настроек режима торговли к значению по умолчанию: {}", defaultMode);
            
            TradingSettings settings = getTradingModeSettings();
            if (settings == null) {
                settings = new TradingSettings();
                settings.setKey(TRADING_MODE_KEY);
                settings.setDescription("Режим торговли (sandbox/production)");
            }
            
            settings.setValue(defaultMode);
            settingsRepository.save(settings);
            
            // Переключаем InvestApi на режим по умолчанию
            investApiManager.switchToMode(defaultMode);
            
            log.info("✅ Настройки сброшены к значению по умолчанию: {}", defaultMode);
            return true;
        } catch (Exception e) {
            log.error("Ошибка при сбросе настроек: {}", e.getMessage());
            return false;
        }
    }
} 