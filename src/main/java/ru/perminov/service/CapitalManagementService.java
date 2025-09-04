package ru.perminov.service;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class CapitalManagementService {

    private final BotLogService botLogService;

    @Value("${capital-management.first-buy-pct:0.005}")
    private BigDecimal firstBuyPct;

    @Value("${capital-management.add-buy-pct:0.003}")
    private BigDecimal addBuyPct;

    @Value("${capital-management.min-position-value:1000}")
    private BigDecimal minPositionValue;

    @Value("${capital-management.max-position-size-pct:0.05}")
    private BigDecimal maxPositionSizePct;

    @Value("${capital-management.risk-per-trade-pct:0.005}")
    private BigDecimal riskPerTradePct;

    @Value("${capital-management.enable-atr-cap:true}")
    private boolean enableAtrCap;

    @Data
    @Builder
    public static class SizingResult {
        private boolean blocked;
        private String blockReason;
        private int lots;
        private BigDecimal buyAmount;
        private BigDecimal finalPositionValue;
        @Builder.Default
        private List<String> warnings = new ArrayList<>();
        @Builder.Default
        private List<String> capsApplied = new ArrayList<>();
    }

    public SizingResult computeSizing(
            String accountId,
            String figi,
            String instrumentDisplay,
            boolean hasPosition,
            BigDecimal price,
            BigDecimal buyingPower,
            PortfolioManagementService.PortfolioAnalysis analysis,
            BigDecimal atr
    ) {
        try {
            Objects.requireNonNull(price, "price");
            Objects.requireNonNull(buyingPower, "buyingPower");
            Objects.requireNonNull(analysis, "analysis");

            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                return SizingResult.builder()
                        .blocked(true)
                        .blockReason("Недопустимая цена")
                        .build();
            }

            BigDecimal portfolioValue = analysis.getTotalValue() != null ? analysis.getTotalValue() : BigDecimal.ZERO;
            BigDecimal currentPosValue = analysis.getPositionValues().getOrDefault(figi, BigDecimal.ZERO);
            // currentFigi может не быть задан — не полагаемся на него
            if (currentPosValue == null) currentPosValue = BigDecimal.ZERO;

            // 1) Базовый размер покупки от buyingPower
            BigDecimal pct = hasPosition ? addBuyPct : firstBuyPct;
            BigDecimal buyAmount = buyingPower.multiply(pct);

            // Минимум — 1 лот
            if (buyAmount.compareTo(price) < 0) {
                buyAmount = price;
            }

            // Минимальная стоимость позиции (RUB)
            if (buyAmount.compareTo(minPositionValue) < 0) {
                buyAmount = minPositionValue.min(buyingPower);
            }

            int lots = buyAmount.divide(price, 0, RoundingMode.DOWN).intValue();
            if (lots <= 0) {
                return SizingResult.builder()
                        .blocked(true)
                        .blockReason("Недостаточно средств для 1 лота")
                        .build();
            }

            buyAmount = price.multiply(BigDecimal.valueOf(lots));

            List<String> caps = new ArrayList<>();
            List<String> warns = new ArrayList<>();

            // 2) Ограничение максимального размера позиции относительно портфеля
            if (portfolioValue != null && portfolioValue.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal maxValueForInstrument = portfolioValue.multiply(maxPositionSizePct);
                BigDecimal newPositionValue = currentPosValue.add(price.multiply(BigDecimal.valueOf(lots)));
                if (newPositionValue.compareTo(maxValueForInstrument) > 0) {
                    BigDecimal allowedAdd = maxValueForInstrument.subtract(currentPosValue);
                    int capLots = allowedAdd.compareTo(BigDecimal.ZERO) > 0
                            ? allowedAdd.divide(price, 0, RoundingMode.DOWN).intValue()
                            : 0;
                    if (capLots < lots) {
                        lots = capLots;
                        caps.add(String.format("Ограничено по maxPositionSizePct=%.2f%%", maxPositionSizePct.multiply(BigDecimal.valueOf(100))));
                    }
                }
            }

            // 3) ATR-кап по риску на сделку
            if (enableAtrCap && atr != null && atr.compareTo(BigDecimal.ZERO) > 0 && portfolioValue != null) {
                BigDecimal maxRiskPerTrade = portfolioValue.multiply(riskPerTradePct);
                int allowedByAtr = maxRiskPerTrade.divide(atr, 0, RoundingMode.DOWN).intValue();
                if (allowedByAtr < lots) {
                    lots = Math.max(allowedByAtr, 0);
                    caps.add(String.format("Ограничено по ATR: риск %.2f%%", riskPerTradePct.multiply(BigDecimal.valueOf(100))));
                }
            }

            if (lots <= 0) {
                return SizingResult.builder()
                        .blocked(true)
                        .blockReason("Ограничения капитала свели лоты к 0")
                        .capsApplied(caps)
                        .warnings(warns)
                        .build();
            }

            BigDecimal finalValue = price.multiply(BigDecimal.valueOf(lots));
            SizingResult result = SizingResult.builder()
                    .blocked(false)
                    .lots(lots)
                    .buyAmount(finalValue)
                    .finalPositionValue(finalValue)
                    .capsApplied(caps)
                    .warnings(warns)
                    .build();

            if (!caps.isEmpty()) {
                log.info("Капитал: применены ограничения {} для {} (acc={}) -> лоты {} стоимость {}", caps, instrumentDisplay, accountId, lots, finalValue);
            }
            return result;
        } catch (Exception e) {
            log.error("Ошибка расчета размера позиции: {}", e.getMessage(), e);
            botLogService.addLogEntry(BotLogService.LogLevel.ERROR, BotLogService.LogCategory.RISK_MANAGEMENT,
                    "Ошибка CapitalManagementService", e.getMessage());
            return SizingResult.builder().blocked(true).blockReason("Ошибка расчета размера позиции").build();
        }
    }

    public BigDecimal getMaxPositionSizePct() {
        return maxPositionSizePct;
    }
}


