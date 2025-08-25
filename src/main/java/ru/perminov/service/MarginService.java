package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.perminov.model.TradingSettings;
import ru.perminov.repository.TradingSettingsRepository;
import ru.tinkoff.piapi.contract.v1.Share;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarginService {

    private final TradingSettingsRepository settingsRepository;
    private final InstrumentService instrumentService;
    private final InvestApiManager investApiManager;

    // Settings keys
    public static final String KEY_MARGIN_ENABLED = "margin_enabled";
    public static final String KEY_MARGIN_ALLOW_SHORT = "margin_allow_short";
    public static final String KEY_MARGIN_MAX_UTILIZATION_PCT = "margin_max_utilization_pct"; // 0..1
    public static final String KEY_MARGIN_MAX_SHORT_PCT = "margin_max_short_pct"; // 0..1 of portfolio per short leg
    public static final String KEY_MARGIN_MAX_LEVERAGE = "margin_max_leverage"; // informational
    public static final String KEY_MARGIN_SAFETY_PCT = "margin_safety_pct"; // 0..1, доля для безопасного использования

    public boolean isMarginEnabled() {
        return getBooleanSetting(KEY_MARGIN_ENABLED, false);
    }

    public boolean isShortAllowed() {
        return getBooleanSetting(KEY_MARGIN_ALLOW_SHORT, false);
    }

    public BigDecimal getMaxUtilizationPct() {
        return getDecimalSetting(KEY_MARGIN_MAX_UTILIZATION_PCT, new BigDecimal("0.30"));
    }

    public BigDecimal getMaxShortPct() {
        return getDecimalSetting(KEY_MARGIN_MAX_SHORT_PCT, new BigDecimal("0.10"));
    }

    public BigDecimal getMaxLeverage() {
        return getDecimalSetting(KEY_MARGIN_MAX_LEVERAGE, new BigDecimal("1.30"));
    }

    public BigDecimal getSafetyPct() {
        return getDecimalSetting(KEY_MARGIN_SAFETY_PCT, new BigDecimal("0.50"));
    }

    /**
     * Проверка, доступна ли маржинальная торговля для текущего режима/аккаунта.
     * Возвращает false в песочнице или если API не отдает маржинальные атрибуты.
     */
    public boolean isMarginOperationalForAccount(String accountId) {
        if (!isMarginEnabled()) return false;
        if ("sandbox".equalsIgnoreCase(investApiManager.getCurrentMode())) return false;
        try {
            var attrs = investApiManager.getCurrentInvestApi().getUserService().getMarginAttributesSync(accountId);
            return attrs != null;
        } catch (Exception e) {
            log.warn("Не удалось получить маржинальные атрибуты для {}: {}", accountId, e.getMessage());
            return false;
        }
    }

    /**
     * Возвращает реальные маржинальные атрибуты аккаунта из API.
     * Если недоступно/ошибка — возвращает null.
     */
    public ru.tinkoff.piapi.contract.v1.GetMarginAttributesResponse getAccountMarginAttributes(String accountId) {
        if ("sandbox".equalsIgnoreCase(investApiManager.getCurrentMode())) return null;
        try {
            return investApiManager.getCurrentInvestApi().getUserService().getMarginAttributesSync(accountId);
        } catch (Exception e) {
            log.warn("Ошибка получения маржинальных атрибутов: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Calculates available buying power for long positions: cash + allowed margin portion of portfolio.
     */
    public BigDecimal getAvailableBuyingPower(String accountId, PortfolioManagementService.PortfolioAnalysis analysis) {
        BigDecimal cash = extractCashFromPortfolio(analysis);
        // Если кэш отрицательный, возвращаем 0 - покупки невозможны
        if (cash.compareTo(BigDecimal.ZERO) < 0) {
            log.warn("Отрицательные средства: {}, покупательная способность = 0", cash);
            return BigDecimal.ZERO;
        }
        
        if (!isMarginEnabled()) return cash;
        // Если можем получить реальные маржинальные атрибуты – используем их
        if (isMarginOperationalForAccount(accountId)) {
            var attrs = getAccountMarginAttributes(accountId);
            if (attrs != null) {
                BigDecimal liquid = toBigDecimal(attrs.getLiquidPortfolio());
                BigDecimal starting = toBigDecimal(attrs.getStartingMargin());
                BigDecimal minimal = toBigDecimal(attrs.getMinimalMargin());
                BigDecimal missing = toBigDecimal(attrs.getAmountOfMissingFunds());
                BigDecimal safety = getSafetyPct();
                // Свободная маржа = max(liquid - minimal, 0). Если есть недостающие средства, вычтем их
                BigDecimal freeMargin = liquid.subtract(minimal).subtract(missing.max(BigDecimal.ZERO));
                if (freeMargin.signum() < 0) freeMargin = BigDecimal.ZERO;
                BigDecimal bp = cash.add(freeMargin.multiply(safety)).setScale(2, RoundingMode.DOWN);
                log.info("Покупательная способность (реальная маржа): liquid={}, starting={}, minimal={}, missing={}, safety={}, freeMargin={}, bp={}",
                        liquid, starting, minimal, missing, safety, freeMargin, bp);
                return bp.max(BigDecimal.ZERO);
            }
        }
        // Фоллбек на конфиг
        BigDecimal portfolioValue = analysis.getTotalValue();
        BigDecimal additional = portfolioValue.multiply(getMaxUtilizationPct());
        BigDecimal buyingPower = cash.add(additional).setScale(2, RoundingMode.DOWN);
        log.info("Покупательная способность (по настройке): cash={}, extra={}, total={}", cash, additional, buyingPower);
        return buyingPower.max(BigDecimal.ZERO);
    }

    /**
     * Determines if we can open a short for this instrument under current settings and exchange flags.
     */
    public boolean canOpenShort(String figi) {
        if (!isMarginEnabled() || !isShortAllowed()) {
            return false;
        }
        try {
            Share share = instrumentService.getShareByFigi(figi);
            if (share == null) {
                log.warn("Не удалось получить Share по FIGI {} для проверки шорта", figi);
                return false;
            }
            // If API exposes short flag, check it; otherwise, assume false
            boolean shortEnabled = share.getShortEnabledFlag();
            if (!shortEnabled) {
                log.warn("Шорт недоступен для FIGI {} по флагу инструмента", figi);
            }
            return shortEnabled;
        } catch (Exception e) {
            log.error("Ошибка при проверке доступности шорта для {}: {}", figi, e.getMessage());
            return false;
        }
    }

    /**
     * Calculates target short amount (in currency) for a new short position based on portfolio value and settings.
     */
    public BigDecimal calculateTargetShortAmount(String accountId, PortfolioManagementService.PortfolioAnalysis analysis) {
        if (isMarginOperationalForAccount(accountId)) {
            var attrs = getAccountMarginAttributes(accountId);
            if (attrs != null) {
                BigDecimal liquid = toBigDecimal(attrs.getLiquidPortfolio());
                BigDecimal minimal = toBigDecimal(attrs.getMinimalMargin());
                BigDecimal safety = getSafetyPct();
                BigDecimal room = liquid.subtract(minimal);
                if (room.signum() < 0) room = BigDecimal.ZERO;
                BigDecimal target = room.multiply(safety).setScale(2, RoundingMode.DOWN);
                log.info("Лимит для шорта (реальная маржа): liquid={}, minimal={}, room={}, safety={}, target={}",
                        liquid, minimal, room, safety, target);
                return target.max(BigDecimal.ZERO);
            }
        }
        BigDecimal portfolioValue = analysis.getTotalValue();
        BigDecimal target = portfolioValue.multiply(getMaxShortPct()).multiply(getSafetyPct()).setScale(2, RoundingMode.DOWN);
        log.info("Лимит для шорта (по настройке): target={}", target);
        return target.max(BigDecimal.ZERO);
    }

    private BigDecimal extractCashFromPortfolio(PortfolioManagementService.PortfolioAnalysis analysis) {
        return analysis.getPositions().stream()
                .filter(p -> "currency".equals(p.getInstrumentType()))
                .map(ru.tinkoff.piapi.core.models.Position::getQuantity)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private boolean getBooleanSetting(String key, boolean defaultValue) {
        return settingsRepository.findByKey(key)
                .map(TradingSettings::getValue)
                .map(String::trim)
                .map(String::toLowerCase)
                .map(v -> v.equals("true") || v.equals("1") || v.equals("yes") || v.equals("on"))
                .orElse(defaultValue);
    }

    private BigDecimal getDecimalSetting(String key, BigDecimal defaultValue) {
        return settingsRepository.findByKey(key)
                .map(TradingSettings::getValue)
                .map(String::trim)
                .map(v -> {
                    try { return new BigDecimal(v); } catch (Exception e) { return defaultValue; }
                })
                .orElse(defaultValue);
    }

    public BigDecimal toBigDecimal(ru.tinkoff.piapi.contract.v1.MoneyValue m) {
        if (m == null) return BigDecimal.ZERO;
        return BigDecimal.valueOf(m.getUnits()).add(BigDecimal.valueOf(m.getNano(), 9));
    }
}


