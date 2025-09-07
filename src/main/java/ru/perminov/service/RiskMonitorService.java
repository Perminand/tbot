package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.perminov.model.RiskRule;
import ru.tinkoff.piapi.core.models.Portfolio;
import ru.tinkoff.piapi.core.models.Position;
import ru.tinkoff.piapi.contract.v1.OrderDirection;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RiskMonitorService {

    private final AccountService accountService;
    private final PortfolioService portfolioService;
    private final OrderService orderService;
    private final LotSizeService lotSizeService;
    private final RiskRuleService riskRuleService;
    private final BotControlService botControlService;

    private static final String INSTRUMENT_TYPE_CURRENCY = "currency";

    // Каждые 60 секунд проверяем триггеры SL/TP
    @Scheduled(fixedRate = 60000)
    public void monitorStops() {
        try {
            if (botControlService.isPanic()) {
                log.warn("PANIC-STOP активен: мониторинг SL/TP пропущен");
                return;
            }

            List<ru.tinkoff.piapi.contract.v1.Account> accounts = accountService.getAccounts();
            for (ru.tinkoff.piapi.contract.v1.Account acc : accounts) {
                String accountId = acc.getId();
                checkAccountStops(accountId);
                // лёгкая пауза между аккаунтами
                Thread.sleep(100);
            }
        } catch (Exception e) {
            log.error("Ошибка мониторинга стопов: {}", e.getMessage());
        }
    }

    private void checkAccountStops(String accountId) {
        Portfolio portfolio = portfolioService.getPortfolio(accountId);
        for (Position p : portfolio.getPositions()) {
            // пропускаем валюту и нулевые позиции
            if (INSTRUMENT_TYPE_CURRENCY.equals(p.getInstrumentType())) continue;
            if (p.getQuantity() == null || p.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;

            String figi = p.getFigi();
            // Переводим количество акций в лоты
            int lotSize = lotSizeService.getLotSize(figi, p.getInstrumentType());
            BigDecimal qtyLots = p.getQuantity().divide(new BigDecimal(Math.max(1, lotSize)), 0, java.math.RoundingMode.DOWN);

            BigDecimal currentPrice = extractPrice(p.getCurrentPrice());
            BigDecimal avgPrice = BigDecimal.ZERO;
            // пытаемся взять среднюю цену по доступным полям
            avgPrice = maxBigDecimal(avgPrice, moneyToBigDecimalSafe(p.getAveragePositionPrice()));
            avgPrice = maxBigDecimal(avgPrice, moneyToBigDecimalSafe(p.getAveragePositionPriceFifo()));
            // В некоторых моделях поля NoNkd может не быть
            if (avgPrice.compareTo(BigDecimal.ZERO) <= 0) {
                // если нет средней — пропускаем инструмент
                continue;
            }

            double sl = riskRuleService.findByFigi(figi)
                    .map(RiskRule::getStopLossPct)
                    .orElse(riskRuleService.getDefaultStopLossPct());
            double tp = riskRuleService.findByFigi(figi)
                    .map(RiskRule::getTakeProfitPct)
                    .orElse(riskRuleService.getDefaultTakeProfitPct());

            BigDecimal stopLevel = avgPrice.multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(sl)));
            BigDecimal takeLevel = avgPrice.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(tp)));

            log.debug("Мониторинг {}: цена={}, средняя={}, SL={} ({}%), TP={} ({}%)", 
                figi, currentPrice, avgPrice, stopLevel, sl*100, takeLevel, tp*100);

            tryTrigger(accountId, figi, qtyLots, currentPrice, stopLevel, takeLevel);
        }
    }

    private void tryTrigger(String accountId, String figi, BigDecimal qtyLots, BigDecimal current, BigDecimal slLevel, BigDecimal tpLevel) {
        try {
            if (current.compareTo(BigDecimal.ZERO) <= 0) return;
            int lots = qtyLots.intValue();
            if (lots <= 0) return;

            if (current.compareTo(slLevel) <= 0) {
                log.warn("SL сработал: текущая={} ≤ SL={} (acc={}) — отправляем MARKET SELL {} лотов", current, slLevel, accountId, lots);
                orderService.placeSmartLimitOrder(figi, lots, OrderDirection.ORDER_DIRECTION_SELL, accountId, current);
                return;
            }
            if (current.compareTo(tpLevel) >= 0) {
                log.info("TP сработал: текущая={} ≥ TP={} (acc={}) — отправляем MARKET SELL {} лотов", current, tpLevel, accountId, lots);
                orderService.placeSmartLimitOrder(figi, lots, OrderDirection.ORDER_DIRECTION_SELL, accountId, current);
            }
        } catch (Exception e) {
                                    log.error("Ошибка исполнения SL/TP: {}", e.getMessage());
        }
    }

    private BigDecimal extractPrice(Object priceObj) {
        try {
            if (priceObj == null) return BigDecimal.ZERO;
            String s = priceObj.toString();
            if (s.contains("value=")) {
                String part = s.substring(s.indexOf("value=") + 6);
                part = part.substring(0, part.indexOf(','));
                return new BigDecimal(part);
            }
            String[] parts = s.split("[^0-9.]");
            for (String part : parts) {
                if (!part.isEmpty() && part.matches("\\d+\\.?\\d*")) {
                    return new BigDecimal(part);
                }
            }
        } catch (Exception ignore) {}
        return BigDecimal.ZERO;
    }

    private BigDecimal moneyToBigDecimalSafe(Object money) {
        try {
            if (money == null) return BigDecimal.ZERO;
            String s = money.toString();
            if (s.contains("units=") && s.contains("nano=")) {
                String us = s.substring(s.indexOf("units=") + 6);
                us = us.substring(0, us.indexOf(','));
                String ns = s.substring(s.indexOf("nano=") + 5);
                ns = ns.replaceAll("[^0-9]", "");
                long units = Long.parseLong(us.trim());
                int nano = ns.isEmpty() ? 0 : Integer.parseInt(ns);
                return BigDecimal.valueOf(units).add(BigDecimal.valueOf(nano, 9));
            }
        } catch (Exception ignore) {}
        return BigDecimal.ZERO;
    }

    private BigDecimal maxBigDecimal(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) >= 0 ? a : b;
    }
}


