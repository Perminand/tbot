package ru.perminov.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.perminov.service.AccountService;
import ru.perminov.service.PortfolioManagementService;
import ru.perminov.service.TradingSettingsService;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class StartupInitializer {

    private final TradingSettingsService tradingSettingsService;
    private final PortfolioManagementService portfolioManagementService;
    private final AccountService accountService;

    @Bean
    public ApplicationRunner initDefaultsAndAutoMonitoring() {
        return args -> {
            // Defaults for risk and signals
            setDefaultIfMissing("atr.period", "14", "ATR period");
            setDefaultIfMissing("atr.min.pct", "0.002", "Min ATR percent");
            setDefaultIfMissing("atr.max.pct", "0.08", "Max ATR percent");
            setDefaultIfMissing("signal.min.strength", "50", "Min advanced signal strength");
            setDefaultIfMissing("risk_default_sl_pct", "0.05", "Default stop loss percent");
            setDefaultIfMissing("risk_default_tp_pct", "0.10", "Default take profit percent");
            setDefaultIfMissing("risk_default_trailing_pct", "0.05", "Default trailing stop percent");
            setDefaultIfMissing("risk_per_trade_pct", "0.01", "Risk per trade percent of portfolio");
            setDefaultIfMissing("hard_stops.enabled", "true", "Enable hard OCO stops in production");
            setDefaultIfMissing("hard_stops.trailing.enabled", "true", "Enable trailing with OCO re-posting");

            // Auto monitoring control
            setDefaultIfMissing("auto_monitor.enable", "true", "Enable auto monitoring at startup");
            String enableStr = tradingSettingsService.getString("auto_monitor.enable", "false");
            boolean enable = Boolean.parseBoolean(enableStr);
            log.info("🔍 Автоматический мониторинг: enable={}, значение из настроек: {}", enable, enableStr);
            if (enable) {
                log.info("🚀 Включаем автоматический мониторинг...");
                final String[] accHolder = { tradingSettingsService.getString("auto_monitor.account_id", "") };
                try {
                    var accounts = accountService.getAccounts();
                    log.info("🔍 Найдено аккаунтов: {}", accounts.size());
                    if (accounts.isEmpty()) {
                        log.warn("❌ Автоматический мониторинг включен, но аккаунты не найдены. Пропускаем запуск.");
                        return;
                    }
                    boolean accountMatches = accounts.stream().anyMatch(a -> a.getId().equals(accHolder[0]));
                    if (accHolder[0] == null || accHolder[0].isBlank() || !accountMatches) {
                        accHolder[0] = accounts.get(0).getId();
                        tradingSettingsService.upsert("auto_monitor.account_id", accHolder[0], "Selected first account automatically (validated by mode)");
                        log.info("✅ Аккаунт для автоматического мониторинга скорректирован на первый аккаунт текущего режима: {}", accHolder[0]);
                    }
                } catch (Exception e) {
                    log.warn("❌ Не удалось получить аккаунты для автоматического мониторинга: {}", e.getMessage());
                    return;
                }
                try {
                    portfolioManagementService.startAutoMonitoring(accHolder[0]);
                    log.info("✅ Автоматический мониторинг запущен для аккаунта {}", accHolder[0]);
                } catch (Exception e) {
                    log.warn("❌ Не удалось запустить автоматический мониторинг для {}: {}", accHolder[0], e.getMessage());
                }
            } else {
                log.info("⏹️ Автоматический мониторинг отключен в настройках");
            }
        };
    }

    private void setDefaultIfMissing(String key, String value, String description) {
        String existing = tradingSettingsService.getString(key, null);
        if (existing == null) {
            tradingSettingsService.upsert(key, value, description);
        }
    }
}


