package ru.perminov.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.perminov.model.TradingSettings;
import ru.perminov.repository.TradingSettingsRepository;
import ru.perminov.service.MarginService;
import ru.perminov.service.PortfolioManagementService;
import ru.tinkoff.piapi.core.models.Position;
import ru.tinkoff.piapi.contract.v1.GetMarginAttributesResponse;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/margin")
@RequiredArgsConstructor
public class MarginController {

    private final MarginService marginService;
    private final PortfolioManagementService portfolioManagementService;
    private final TradingSettingsRepository settingsRepository;

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", marginService.isMarginEnabled());
        status.put("allowShort", marginService.isShortAllowed());
        status.put("maxUtilizationPct", marginService.getMaxUtilizationPct());
        status.put("maxShortPct", marginService.getMaxShortPct());
        status.put("maxLeverage", marginService.getMaxLeverage());
        return status;
    }

    @PostMapping("/settings")
    public ResponseEntity<Map<String, Object>> updateSettings(
            @RequestParam(value = "enabled", required = false) Boolean enabled,
            @RequestParam(value = "allowShort", required = false) Boolean allowShort,
            @RequestParam(value = "maxUtilizationPct", required = false) BigDecimal maxUtilizationPct,
            @RequestParam(value = "maxShortPct", required = false) BigDecimal maxShortPct,
            @RequestParam(value = "maxLeverage", required = false) BigDecimal maxLeverage
    ) {
        if (enabled != null) upsert(MarginService.KEY_MARGIN_ENABLED, enabled.toString(), "Включить маржинальную торговлю");
        if (allowShort != null) upsert(MarginService.KEY_MARGIN_ALLOW_SHORT, allowShort.toString(), "Разрешить открытие коротких позиций");
        if (maxUtilizationPct != null) upsert(MarginService.KEY_MARGIN_MAX_UTILIZATION_PCT, maxUtilizationPct.toPlainString(), "Доля портфеля на маржинальные покупки (0..1)");
        if (maxShortPct != null) upsert(MarginService.KEY_MARGIN_MAX_SHORT_PCT, maxShortPct.toPlainString(), "Доля портфеля на шорт (0..1)");
        if (maxLeverage != null) upsert(MarginService.KEY_MARGIN_MAX_LEVERAGE, maxLeverage.toPlainString(), "Максимальное кредитное плечо (инфо)");

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("status", getStatus());
        return ResponseEntity.ok(result);
    }

    private void upsert(String key, String value, String description) {
        TradingSettings settings = settingsRepository.findByKey(key).orElseGet(TradingSettings::new);
        settings.setKey(key);
        settings.setValue(value);
        settings.setDescription(description);
        settingsRepository.save(settings);
    }

    @GetMapping("/attributes")
    public ResponseEntity<?> getMarginAttributes(@RequestParam("accountId") String accountId) {
        try {
            GetMarginAttributesResponse attrs = marginService.getAccountMarginAttributes(accountId);
            if (attrs == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Маржинальные атрибуты недоступны для текущего режима/аккаунта"));
            }
            Map<String, Object> body = new HashMap<>();
            body.put("liquidPortfolio", toDecimal(attrs.getLiquidPortfolio()));
            body.put("startingMargin", toDecimal(attrs.getStartingMargin()));
            body.put("minimalMargin", toDecimal(attrs.getMinimalMargin()));
            body.put("fundsSufficiencyLevel", toQuotation(attrs.getFundsSufficiencyLevel()));
            body.put("amountOfMissingFunds", toDecimal(attrs.getAmountOfMissingFunds()));
            body.put("correctedMargin", toDecimal(attrs.getCorrectedMargin()));
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Получение покупательной способности (BP) на основе портфеля и настроек маржи
     */
    @GetMapping("/buying-power")
    public ResponseEntity<?> getBuyingPower(@RequestParam("accountId") String accountId) {
        try {
            var analysis = portfolioManagementService.analyzePortfolio(accountId);
            java.math.BigDecimal buyingPower = marginService.getAvailableBuyingPower(accountId, analysis);
            java.math.BigDecimal cash = analysis.getPositions().stream()
                    .filter(p -> "currency".equals(p.getInstrumentType()))
                    .map(Position::getQuantity)
                    .findFirst()
                    .orElse(java.math.BigDecimal.ZERO);

            Map<String, Object> body = new HashMap<>();
            body.put("accountId", accountId);
            body.put("cash", cash);
            body.put("buyingPower", buyingPower);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    private static String toDecimal(ru.tinkoff.piapi.contract.v1.MoneyValue m) {
        if (m == null) return "0";
        long units = m.getUnits();
        int nano = m.getNano();
        String nanoStr = String.format("%09d", nano);
        // Удаляем лишние нули справа
        nanoStr = nanoStr.replaceFirst("0+$", "");
        return nanoStr.isEmpty() ? Long.toString(units) : (units + "." + nanoStr);
    }

    private static String toQuotation(ru.tinkoff.piapi.contract.v1.Quotation q) {
        if (q == null) return "0";
        long units = q.getUnits();
        int nano = q.getNano();
        String nanoStr = String.format("%09d", nano).replaceFirst("0+$", "");
        return nanoStr.isEmpty() ? Long.toString(units) : (units + "." + nanoStr);
    }
}


