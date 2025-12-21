package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.core.models.Portfolio;
import ru.tinkoff.piapi.core.models.Position;
import ru.tinkoff.piapi.core.models.Money;
import ru.perminov.model.RiskRule;
import ru.perminov.model.PositionRiskState;

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
    private final PositionRiskStateService positionRiskStateService;
    private final LotSizeService lotSizeService;
    private final PortfolioManagementService portfolioManagementService;
    private final InstrumentNameService instrumentNameService;

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
                        // Получаем или создаем состояние рисков для позиции
                        PositionRiskState.PositionSide side = p.getQuantity().compareTo(BigDecimal.ZERO) > 0 ? 
                            PositionRiskState.PositionSide.LONG : PositionRiskState.PositionSide.SHORT;
                        
                        BigDecimal avgPrice = extractPrice(p.getAveragePositionPrice());
                        if (avgPrice == null || avgPrice.compareTo(BigDecimal.ZERO) <= 0) {
                            avgPrice = currentPrice;
                        }
                        if (avgPrice == null || avgPrice.compareTo(BigDecimal.ZERO) <= 0) continue;
                        
                        BigDecimal quantityShares = p.getQuantity().abs();
                        int lotSize = lotSizeService.getLotSize(figi, p.getInstrumentType());
                        BigDecimal quantity = quantityShares.divide(new BigDecimal(Math.max(1, lotSize)), 0, RoundingMode.DOWN);
                        
                        // Получаем правила рисков
                        RiskRule rule = riskRuleService.findByFigi(figi).orElseGet(() -> {
                            double sl = riskRuleService.getDefaultStopLossPct();
                            double tp = riskRuleService.getDefaultTakeProfitPct();
                            return riskRuleService.upsert(figi, sl, tp, true);
                        });
                        
                        if (Boolean.FALSE.equals(rule.getActive())) continue;
                        
                        Double slPct = rule.getStopLossPct() != null ? rule.getStopLossPct() : riskRuleService.getDefaultStopLossPct();
                        Double tpPct = rule.getTakeProfitPct() != null ? rule.getTakeProfitPct() : riskRuleService.getDefaultTakeProfitPct();
                        Double trailingPct = riskRuleService.getDefaultTrailingStopPct();
                        
                        // Создаем или обновляем состояние рисков
                        PositionRiskState riskState = positionRiskStateService.createOrUpdateRiskState(
                            accountId, figi, side, 
                            BigDecimal.valueOf(slPct), BigDecimal.valueOf(tpPct), BigDecimal.valueOf(trailingPct),
                            currentPrice, avgPrice, quantity
                        );
                        
                        // Обновляем watermark для trailing stop
                        positionRiskStateService.updateWatermark(accountId, figi, side, currentPrice);
                        
                        // Проверяем срабатывание SL/TP по рассчитанным уровням
                        if (riskState.getStopLossLevel() != null) {
                            boolean slTriggered = false;
                            if (side == PositionRiskState.PositionSide.LONG) {
                                slTriggered = currentPrice.compareTo(riskState.getStopLossLevel()) <= 0;
                            } else {
                                slTriggered = currentPrice.compareTo(riskState.getStopLossLevel()) >= 0;
                            }
                            
                            if (slTriggered) {
                                // 🚫 ПРОВЕРКА БЛОКИРОВКИ ПО ЛИКВИДНОСТИ
                                if (isLiquidityBlocked(figi)) {
                                    log.warn("⏳ БЛОКИРОВКА ПО ЛИКВИДНОСТИ: SL для {} заблокирован", getInstrumentDisplayName(figi));
                                    continue;
                                }
                                
                                int lots = quantity.intValue();
                                if (side == PositionRiskState.PositionSide.LONG) {
                                    log.warn("Срабатывание SL (лонг): price={} <= SL={} — продаем {} лотов", 
                                            currentPrice, riskState.getStopLossLevel(), lots);
                                    orderService.placeSmartLimitOrder(figi, lots, ru.tinkoff.piapi.contract.v1.OrderDirection.ORDER_DIRECTION_SELL, accountId, currentPrice);
                                } else {
                                    log.warn("Срабатывание SL (шорт): price={} >= SL={} — закрываем {} лотов покупкой", 
                                            currentPrice, riskState.getStopLossLevel(), lots);
                                    orderService.placeSmartLimitOrder(figi, lots, ru.tinkoff.piapi.contract.v1.OrderDirection.ORDER_DIRECTION_BUY, accountId, currentPrice);
                                }
                                
                                // Закрываем состояние рисков
                                positionRiskStateService.closePosition(accountId, figi, side);
                                continue;
                            }
                        }
                        
                        if (riskState.getTakeProfitLevel() != null) {
                            boolean tpTriggered = false;
                            if (side == PositionRiskState.PositionSide.LONG) {
                                tpTriggered = currentPrice.compareTo(riskState.getTakeProfitLevel()) >= 0;
                        } else {
                                tpTriggered = currentPrice.compareTo(riskState.getTakeProfitLevel()) <= 0;
                            }
                            
                            if (tpTriggered) {
                                // 🚫 ПРОВЕРКА БЛОКИРОВКИ ПО ЛИКВИДНОСТИ
                                if (isLiquidityBlocked(figi)) {
                                    log.warn("⏳ БЛОКИРОВКА ПО ЛИКВИДНОСТИ: TP для {} заблокирован", getInstrumentDisplayName(figi));
                                    continue;
                                }
                                
                                int lots = quantity.intValue();
                                String key = "tp.stage." + accountId + "." + figi;
                                int stage = tradingSettingsService.getInt(key, 0);
                                
                                if (stage == 0) {
                                    int qty = Math.max(1, lots / 2);
                                    if (side == PositionRiskState.PositionSide.LONG) {
                                        log.info("TP1 (лонг): продаем {} из {} лотов", qty, lots);
                                        orderService.placeSmartLimitOrder(figi, qty, ru.tinkoff.piapi.contract.v1.OrderDirection.ORDER_DIRECTION_SELL, accountId, currentPrice);
                                    } else {
                                        log.info("TP1 (шорт): покупаем {} из {} лотов для частичного закрытия", qty, lots);
                                    orderService.placeSmartLimitOrder(figi, qty, ru.tinkoff.piapi.contract.v1.OrderDirection.ORDER_DIRECTION_BUY, accountId, currentPrice);
                                    }
                                    tradingSettingsService.upsert(key, "1", "TP1 hit (" + side.toString().toLowerCase() + ")");
                                    continue;
                                } else {
                                    if (side == PositionRiskState.PositionSide.LONG) {
                                        log.info("TP2 (лонг): продаем остаток {} лотов", lots);
                                        orderService.placeSmartLimitOrder(figi, lots, ru.tinkoff.piapi.contract.v1.OrderDirection.ORDER_DIRECTION_SELL, accountId, currentPrice);
                                    } else {
                                        log.info("TP2 (шорт): покупаем остаток {} лотов для полного закрытия", lots);
                                        orderService.placeSmartLimitOrder(figi, lots, ru.tinkoff.piapi.contract.v1.OrderDirection.ORDER_DIRECTION_BUY, accountId, currentPrice);
                                    }
                                    tradingSettingsService.upsert(key, "0", "TP cycle done (" + side.toString().toLowerCase() + ")");
                                    
                                    // Закрываем состояние рисков после полного TP
                                    positionRiskStateService.closePosition(accountId, figi, side);
                                    continue;
                                }
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
    
    /**
     * Проверка блокировки по ликвидности
     */
    private boolean isLiquidityBlocked(String figi) {
        if (portfolioManagementService == null) return false;
        return portfolioManagementService.isLiquidityBlocked(figi);
    }
    
    /**
     * Получение читаемого имени инструмента
     */
    private String getInstrumentDisplayName(String figi) {
        try {
            if (instrumentNameService != null) {
                String[] instrumentTypes = {"share", "bond", "etf", "currency"};
                for (String type : instrumentTypes) {
                    try {
                        String name = instrumentNameService.getInstrumentName(figi, type);
                        String ticker = instrumentNameService.getTicker(figi, type);
                        if (name != null && ticker != null) {
                            return name + " (" + ticker + ")";
                        }
                        if (name != null) {
                            return name;
                        }
                        if (ticker != null) {
                            return ticker;
                        }
                    } catch (Exception ignore) {
                        // Пробуем следующий тип
                    }
                }
            }
        } catch (Exception ignore) {
        }
        return figi;
    }
}


