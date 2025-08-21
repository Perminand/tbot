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

    // Периодический контроль позиций: SL/TP/трейлинг
    @Scheduled(fixedRate = 15000) // каждые 15 секунд
    public void watchPositions() {
        try {
            List<String> accountIds = accountService.getAccounts().stream().map(a -> a.getId()).toList();
            for (String accountId : accountIds) {
                Portfolio portfolio = portfolioService.getPortfolio(accountId);
                for (Position p : portfolio.getPositions()) {
                    if ("currency".equals(p.getInstrumentType())) continue;
                    if (p.getQuantity() == null || p.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;

                    String figi = p.getFigi();
                    final BigDecimal currentPrice;
                    try {
                        var analysis = marketAnalysisService.analyzeTrend(figi, ru.tinkoff.piapi.contract.v1.CandleInterval.CANDLE_INTERVAL_DAY);
                        currentPrice = analysis.getCurrentPrice();
                    } catch (Exception e) {
                        log.warn("Не удалось получить текущую цену для {}: {}", figi, e.getMessage());
                        continue;
                    }

                    {
                        RiskRule rule = riskRuleService.findByFigi(figi).orElseGet(() -> {
                            // Автосоздание дефолтных правил для старых позиций
                            double sl = riskRuleService.getDefaultStopLossPct();
                            double tp = riskRuleService.getDefaultTakeProfitPct();
                            return riskRuleService.upsert(figi, sl, tp, true);
                        });
                        if (Boolean.FALSE.equals(rule.getActive())) return;
                        // Базовый SL/TP
                        Double slPct = rule.getStopLossPct() != null ? rule.getStopLossPct() : riskRuleService.getDefaultStopLossPct();
                        Double tpPct = rule.getTakeProfitPct() != null ? rule.getTakeProfitPct() : riskRuleService.getDefaultTakeProfitPct();

                        BigDecimal avgPrice = extractPrice(p.getAveragePositionPrice());
                        if (avgPrice == null || avgPrice.compareTo(BigDecimal.ZERO) <= 0) {
                            avgPrice = currentPrice;
                        }
                        if (avgPrice == null || avgPrice.compareTo(BigDecimal.ZERO) <= 0) return;

                        BigDecimal stopLossLevel = avgPrice.multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(slPct))).setScale(4, RoundingMode.HALF_UP);
                        BigDecimal takeProfitLevel = avgPrice.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(tpPct))).setScale(4, RoundingMode.HALF_UP);

                        // Long-позиции: SL ниже средней, TP выше средней
                        if (currentPrice.compareTo(stopLossLevel) <= 0) {
                            int lots = p.getQuantity().intValue();
                            log.warn("Срабатывание SL по {}: price={} <= SL={} — закрываем {} лотов", figi, currentPrice, stopLossLevel, lots);
                            orderService.placeMarketOrder(figi, lots, ru.tinkoff.piapi.contract.v1.OrderDirection.ORDER_DIRECTION_SELL, accountId);
                            return;
                        }
                        if (currentPrice.compareTo(takeProfitLevel) >= 0) {
                            // Частичные тейки: 50% на TP1, остальное держим до TP2
                            String key = "tp.stage." + accountId + "." + figi;
                            int stage = tradingSettingsService.getInt(key, 0);
                            int lots = p.getQuantity().intValue();
                            if (stage == 0) {
                                int qty = Math.max(1, lots / 2);
                                log.info("TP1 по {}: продаем {} из {} лотов", figi, qty, lots);
                                orderService.placeMarketOrder(figi, qty, ru.tinkoff.piapi.contract.v1.OrderDirection.ORDER_DIRECTION_SELL, accountId);
                                tradingSettingsService.upsert(key, "1", "TP1 hit");
                                return;
                            } else {
                                log.info("TP2 по {}: закрываем остаток {} лотов", figi, lots);
                                orderService.placeMarketOrder(figi, lots, ru.tinkoff.piapi.contract.v1.OrderDirection.ORDER_DIRECTION_SELL, accountId);
                                tradingSettingsService.upsert(key, "0", "TP cycle done");
                                return;
                            }
                        }
                        // Примитивный трейлинг: подтягиваем SL до max(avg, price*(1-trailing))
                        double trailingPct = riskRuleService.getDefaultTrailingStopPct();
                        BigDecimal trailingLevel = currentPrice.multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(trailingPct)));
                        if (trailingLevel.compareTo(stopLossLevel) > 0) {
                            // В реальной системе тут должно быть обновление правила в БД, чтобы фиксировать новый SL
                            log.info("Трейлинг SL по {}: {} -> {}", figi, stopLossLevel, trailingLevel);
                            try {
                                riskRuleService.upsert(figi, trailingPct, rule.getTakeProfitPct(), true);
                            } catch (Exception ex) {
                                log.debug("Не удалось подтянуть трейлинг SL по {}: {}", figi, ex.getMessage());
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


