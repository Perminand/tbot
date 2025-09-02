package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.core.models.Portfolio;
import ru.tinkoff.piapi.core.models.Position;
import ru.tinkoff.piapi.core.models.Money;
import ru.perminov.model.RiskRule;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PositionWatcherService {

    private final PortfolioService portfolioService;
    private final AccountService accountService;
    private final OrderService orderService;
    private final RiskRuleService riskRuleService;
    private final MarketAnalysisService marketAnalysisService;
    private final TradingSettingsService tradingSettingsService;
    private final BotLogService botLogService;

    // Периодический контроль позиций: SL/TP/трейлинг
    @Scheduled(fixedRate = 15000) // каждые 15 секунд
    public void watchPositions() {
        try {
            List<String> accountIds = accountService.getAccounts().stream().map(a -> a.getId()).toList();
            for (String accountId : accountIds) {
                Portfolio portfolio = portfolioService.getPortfolio(accountId);
                for (Position p : portfolio.getPositions()) {
                    if ("currency".equals(p.getInstrumentType())) continue;
                    // Обрабатываем как лонги (>0), так и шорты (<0). Пропускаем только нулевые позиции
                    if (p.getQuantity() == null || p.getQuantity().compareTo(BigDecimal.ZERO) == 0) continue;

                    String figi = p.getFigi();
                    final BigDecimal currentPrice;
                    try {
                        var analysis = marketAnalysisService.analyzeTrend(figi, ru.tinkoff.piapi.contract.v1.CandleInterval.CANDLE_INTERVAL_DAY);
                        currentPrice = analysis.getCurrentPrice();
                    } catch (Exception e) {
                        log.warn("Не удалось получить текущую цену: {}", e.getMessage());
                        continue;
                    }

                    {
                        RiskRule rule = riskRuleService.findByFigi(figi).orElseGet(() -> {
                            // Автосоздание дефолтных правил для старых позиций
                            double sl = riskRuleService.getDefaultStopLossPct();
                            double tp = riskRuleService.getDefaultTakeProfitPct();
                            return riskRuleService.upsert(figi, sl, tp, true);
                        });
                        if (Boolean.FALSE.equals(rule.getActive())) continue;
                        // Базовый SL/TP
                        Double slPct = rule.getStopLossPct() != null ? rule.getStopLossPct() : riskRuleService.getDefaultStopLossPct();
                        Double tpPct = rule.getTakeProfitPct() != null ? rule.getTakeProfitPct() : riskRuleService.getDefaultTakeProfitPct();

                        BigDecimal avgPrice = extractPrice(p.getAveragePositionPrice());
                        if (avgPrice == null || avgPrice.compareTo(BigDecimal.ZERO) <= 0) {
                            avgPrice = currentPrice;
                        }
                        if (avgPrice == null || avgPrice.compareTo(BigDecimal.ZERO) <= 0) continue;

                        boolean isShort = p.getQuantity().compareTo(BigDecimal.ZERO) < 0;
                        int lotsAbs = Math.abs(p.getQuantity().intValue());

                        // Уровни SL/TP для лонга и шорта (зеркально)
                        BigDecimal longStopLossLevel = avgPrice.multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(slPct))).setScale(4, RoundingMode.HALF_UP);
                        BigDecimal longTakeProfitLevel = avgPrice.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(tpPct))).setScale(4, RoundingMode.HALF_UP);

                        BigDecimal shortStopLossLevel = avgPrice.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(slPct))).setScale(4, RoundingMode.HALF_UP);
                        BigDecimal shortTakeProfitLevel = avgPrice.multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(tpPct))).setScale(4, RoundingMode.HALF_UP);

                        if (!isShort) {
                            // Лонг‑позиции: SL ниже средней, TP выше средней
                            if (currentPrice.compareTo(longStopLossLevel) <= 0) {
                                int lots = p.getQuantity().intValue();
                                log.warn("Срабатывание SL (лонг): price={} <= SL={} — продаем {} лотов", currentPrice, longStopLossLevel, lots);
                                orderService.placeMarketOrder(figi, lots, ru.tinkoff.piapi.contract.v1.OrderDirection.ORDER_DIRECTION_SELL, accountId);
                                continue;
                            }
                            if (currentPrice.compareTo(longTakeProfitLevel) >= 0) {
                                // Частичные тейки: 50% на TP1, остаток — TP2
                                String key = "tp.stage." + accountId + "." + figi;
                                int stage = tradingSettingsService.getInt(key, 0);
                                int lots = p.getQuantity().intValue();
                                if (stage == 0) {
                                    int qty = Math.max(1, lots / 2);
                                    log.info("TP1 (лонг): продаем {} из {} лотов", qty, lots);
                                    orderService.placeMarketOrder(figi, qty, ru.tinkoff.piapi.contract.v1.OrderDirection.ORDER_DIRECTION_SELL, accountId);
                                    tradingSettingsService.upsert(key, "1", "TP1 hit (long)");
                                    continue;
                                } else {
                                    log.info("TP2 (лонг): продаем остаток {} лотов", lots);
                                    orderService.placeMarketOrder(figi, lots, ru.tinkoff.piapi.contract.v1.OrderDirection.ORDER_DIRECTION_SELL, accountId);
                                    tradingSettingsService.upsert(key, "0", "TP cycle done (long)");
                                    continue;
                                }
                            }
                        } else {
                            // Шорт‑позиции: SL выше средней, TP ниже средней. Закрываем шорт покупкой
                            if (currentPrice.compareTo(shortStopLossLevel) >= 0) {
                                botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT,
                                    "SL (шорт) сработал",
                                    String.format("FIGI: %s, price=%s >= SL=%s, avg=%s, qty=%d, account=%s", figi, currentPrice, shortStopLossLevel, avgPrice, lotsAbs, accountId));
                                log.warn("Срабатывание SL (шорт): price={} >= SL={} — закрываем {} лотов покупкой", currentPrice, shortStopLossLevel, lotsAbs);
                                orderService.placeMarketOrder(figi, lotsAbs, ru.tinkoff.piapi.contract.v1.OrderDirection.ORDER_DIRECTION_BUY, accountId);
                                botLogService.addLogEntry(BotLogService.LogLevel.TRADE, BotLogService.LogCategory.AUTOMATIC_TRADING,
                                    "Закрыт шорт по SL",
                                    String.format("FIGI: %s, buy %d, price=%s", figi, lotsAbs, currentPrice));
                                continue;
                            }
                            if (currentPrice.compareTo(shortTakeProfitLevel) <= 0) {
                                String key = "tp.stage.short." + accountId + "." + figi;
                                int stage = tradingSettingsService.getInt(key, 0);
                                if (stage == 0) {
                                    int qty = Math.max(1, lotsAbs / 2);
                                    botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.RISK_MANAGEMENT,
                                        "TP1 (шорт) сработал",
                                        String.format("FIGI: %s, price=%s <= TP=%s, avg=%s, close qty=%d/%d", figi, currentPrice, shortTakeProfitLevel, avgPrice, qty, lotsAbs));
                                    log.info("TP1 (шорт): покупаем {} из {} лотов для частичного закрытия", qty, lotsAbs);
                                    orderService.placeMarketOrder(figi, qty, ru.tinkoff.piapi.contract.v1.OrderDirection.ORDER_DIRECTION_BUY, accountId);
                                    botLogService.addLogEntry(BotLogService.LogLevel.TRADE, BotLogService.LogCategory.AUTOMATIC_TRADING,
                                        "Частично закрыт шорт по TP1",
                                        String.format("FIGI: %s, buy %d, price=%s", figi, qty, currentPrice));
                                    tradingSettingsService.upsert(key, "1", "TP1 hit (short)");
                                    continue;
                                } else {
                                    botLogService.addLogEntry(BotLogService.LogLevel.SUCCESS, BotLogService.LogCategory.RISK_MANAGEMENT,
                                        "TP2 (шорт) сработал — закрываем остаток",
                                        String.format("FIGI: %s, price=%s <= TP=%s, avg=%s, qty=%d", figi, currentPrice, shortTakeProfitLevel, avgPrice, lotsAbs));
                                    log.info("TP2 (шорт): покупаем остаток {} лотов для полного закрытия", lotsAbs);
                                    orderService.placeMarketOrder(figi, lotsAbs, ru.tinkoff.piapi.contract.v1.OrderDirection.ORDER_DIRECTION_BUY, accountId);
                                    botLogService.addLogEntry(BotLogService.LogLevel.TRADE, BotLogService.LogCategory.AUTOMATIC_TRADING,
                                        "Полностью закрыт шорт по TP2",
                                        String.format("FIGI: %s, buy %d, price=%s", figi, lotsAbs, currentPrice));
                                    tradingSettingsService.upsert(key, "0", "TP cycle done (short)");
                                    continue;
                                }
                            }
                        }

                        // Примитивный трейлинг: подтягиваем SL
                        double trailingPct = riskRuleService.getDefaultTrailingStopPct();
                        if (!isShort) {
                            BigDecimal trailingLevel = currentPrice.multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(trailingPct)));
                            if (trailingLevel.compareTo(longStopLossLevel) > 0) {
                                log.info("Трейлинг SL (лонг): {} -> {}", longStopLossLevel, trailingLevel);
                                try { riskRuleService.upsert(figi, trailingPct, rule.getTakeProfitPct(), true); } catch (Exception ex) { log.debug("Не удалось подтянуть трейлинг SL: {}", ex.getMessage()); }
                            }
                        } else {
                            BigDecimal trailingLevel = currentPrice.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(trailingPct)));
                            if (trailingLevel.compareTo(shortStopLossLevel) < 0) {
                                log.info("Трейлинг SL (шорт): {} -> {}", shortStopLossLevel, trailingLevel);
                                try { riskRuleService.upsert(figi, trailingPct, rule.getTakeProfitPct(), true); } catch (Exception ex) { log.debug("Не удалось подтянуть трейлинг SL (шорт): {}", ex.getMessage()); }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Ошибка позиционного надзора: {}", e.getMessage());
        }
    }

    private BigDecimal extractPrice(Object priceObj) {
        try {
            if (priceObj == null) return null;
            if (priceObj instanceof Money) {
                return ((Money) priceObj).getValue();
            }
            String priceStr = priceObj.toString();
            if (priceStr.contains("value=")) {
                String valuePart = priceStr.substring(priceStr.indexOf("value=") + 6);
                valuePart = valuePart.substring(0, valuePart.indexOf(","));
                return new BigDecimal(valuePart);
            }
            String[] parts = priceStr.split("[^0-9.]");
            for (String part : parts) {
                if (!part.isEmpty() && part.matches("\\d+\\.?\\d*")) {
                    return new BigDecimal(part);
                }
            }
        } catch (Exception ignore) {
        }
        return null;
    }
}


