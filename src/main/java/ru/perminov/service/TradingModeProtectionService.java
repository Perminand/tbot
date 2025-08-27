package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.perminov.model.TradingSettings;
import ru.perminov.repository.TradingSettingsRepository;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradingModeProtectionService {
    
    private final TradingSettingsRepository settingsRepository;
    private final InvestApiManager investApiManager;
    private final TradingModeService tradingModeService;
    
    private static final String TRADING_MODE_KEY = "trading_mode";
    private static final String SANDBOX_MODE = "sandbox";
    private static final String PRODUCTION_MODE = "production";
    
    // Флаг для отслеживания активной торговли
    private final AtomicBoolean isTradingActive = new AtomicBoolean(false);
    
    /**
     * Проверка целостности режима торговли
     */
    public boolean validateTradingMode() {
        try {
            String currentMode = tradingModeService.getCurrentMode();
            String investApiMode = investApiManager.getCurrentMode();
            TradingSettings dbSettings = getTradingModeSettings();
            String dbMode = dbSettings != null ? dbSettings.getValue() : null;
            
            log.info("Проверка целостности режима торговли:");
            log.info("- TradingModeService: {}", currentMode);
            log.info("- InvestApiManager: {}", investApiMode);
            log.info("- База данных: {}", dbMode);
            
            // Проверяем синхронизацию между всеми компонентами
            boolean isSynchronized = currentMode.equals(investApiMode) && 
                                   (dbMode == null || currentMode.equals(dbMode));
            
            if (!isSynchronized) {
                log.error("❌ ОБНАРУЖЕНА РАССИНХРОНИЗАЦИЯ РЕЖИМОВ ТОРГОВЛИ!");
                log.error("TradingModeService: {} != InvestApiManager: {}", currentMode, investApiMode);
                if (dbMode != null) {
                    log.error("База данных: {}", dbMode);
                }
                return false;
            }
            
            log.info("✅ Режимы торговли синхронизированы: {}", currentMode);
            return true;
            
        } catch (Exception e) {
            log.error("Ошибка при проверке целостности режима торговли: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Защита от несанкционированного переключения в production режиме
     */
    public boolean isProductionModeProtected() {
        String currentMode = tradingModeService.getCurrentMode();
        
        if (PRODUCTION_MODE.equals(currentMode)) {
            log.warn("⚠️ PRODUCTION РЕЖИМ АКТИВЕН - включена защита от несанкционированного переключения");
            
            // Проверяем целостность режима
            if (!validateTradingMode()) {
                log.error("❌ КРИТИЧЕСКАЯ ОШИБКА: Обнаружена рассинхронизация в production режиме!");
                return false;
            }
            
            return true;
        }
        
        return true;
    }
    
    /**
     * Установка флага активной торговли
     */
    public void setTradingActive(boolean active) {
        boolean previousState = isTradingActive.getAndSet(active);
        if (previousState != active) {
            if (active) {
                log.warn("🚀 АКТИВНАЯ ТОРГОВЛЯ ВКЛЮЧЕНА - режим заблокирован от изменений");
            } else {
                log.info("✅ Активная торговля остановлена - режим разблокирован");
            }
        }
    }
    
    /**
     * Проверка, активна ли торговля
     */
    public boolean isTradingActive() {
        return isTradingActive.get();
    }
    
    /**
     * Проверка безопасности переключения режима
     */
    public boolean isModeSwitchSafe(String newMode) {
        String currentMode = tradingModeService.getCurrentMode();
        
        // Если торговля активна, запрещаем переключение
        if (isTradingActive.get()) {
            log.error("❌ ПЕРЕКЛЮЧЕНИЕ РЕЖИМА ЗАПРЕЩЕНО: Активная торговля в режиме {}", currentMode);
            return false;
        }
        
        // Проверяем целостность текущего режима
        if (!validateTradingMode()) {
            log.error("❌ ПЕРЕКЛЮЧЕНИЕ РЕЖИМА ЗАПРЕЩЕНО: Обнаружена рассинхронизация режимов");
            return false;
        }
        
        // Дополнительные проверки безопасности
        if (PRODUCTION_MODE.equals(currentMode) && SANDBOX_MODE.equals(newMode)) {
            log.warn("⚠️ Попытка переключения с production на sandbox");
            log.warn("Это действие требует дополнительного подтверждения");
        }
        
        return true;
    }
    
    /**
     * Принудительная синхронизация режимов
     */
    public boolean forceSynchronizeModes() {
        try {
            log.warn("🔄 Принудительная синхронизация режимов торговли");
            
            String currentMode = tradingModeService.getCurrentMode();
            String investApiMode = investApiManager.getCurrentMode();
            
            if (!currentMode.equals(investApiMode)) {
                log.warn("Синхронизация InvestApiManager с TradingModeService: {} -> {}", investApiMode, currentMode);
                investApiManager.switchToMode(currentMode);
            }
            
            // Обновляем настройки в БД
            TradingSettings settings = getTradingModeSettings();
            if (settings == null) {
                settings = new TradingSettings();
                settings.setKey(TRADING_MODE_KEY);
                settings.setDescription("Режим торговли (sandbox/production)");
            }
            
            if (!currentMode.equals(settings.getValue())) {
                log.warn("Синхронизация БД с текущим режимом: {} -> {}", settings.getValue(), currentMode);
                settings.setValue(currentMode);
                settingsRepository.save(settings);
            }
            
            log.info("✅ Синхронизация режимов завершена: {}", currentMode);
            return true;
            
        } catch (Exception e) {
            log.error("Ошибка при синхронизации режимов: {}", e.getMessage());
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
     * Получение статуса защиты
     */
    public String getProtectionStatus() {
        StringBuilder status = new StringBuilder();
        status.append("Статус защиты режима торговли:\n");
        status.append("- Текущий режим: ").append(tradingModeService.getCurrentMode()).append("\n");
        status.append("- Торговля активна: ").append(isTradingActive.get() ? "ДА" : "НЕТ").append("\n");
        status.append("- Целостность режима: ").append(validateTradingMode() ? "ОК" : "ОШИБКА").append("\n");
        status.append("- Защита включена: ").append(isProductionModeProtected() ? "ДА" : "НЕТ");
        
        return status.toString();
    }
}
