package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.contract.v1.OrderState;
import ru.tinkoff.piapi.contract.v1.PostOrderResponse;
// import ru.tinkoff.piapi.core.InvestApi; // unused
import ru.tinkoff.piapi.contract.v1.OrderDirection;
import ru.tinkoff.piapi.contract.v1.OrderType;
import ru.tinkoff.piapi.contract.v1.Quotation;
import ru.tinkoff.piapi.core.models.Position;
import ru.perminov.repository.OrderRepository;
import ru.perminov.model.Order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {
    private final InvestApiManager investApiManager;
    private final BotControlService botControlService;
    private final ApiRateLimiter apiRateLimiter;
    private final OrderRepository orderRepository;
    private final PortfolioService portfolioService;
    private final LotSizeService lotSizeService;
    private final MarketAnalysisService marketAnalysisService;

    public List<OrderState> getOrders(String accountId) {
        try {
            log.info("Получение ордеров для аккаунта: {}", accountId);
            apiRateLimiter.acquire();
            CompletableFuture<List<OrderState>> future = investApiManager.getCurrentInvestApi().getOrdersService().getOrders(accountId);
            List<OrderState> orders = future.get();
            log.info("Получено {} ордеров для аккаунта {}", orders.size(), accountId);
            return orders;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Ошибка при получении ордеров для аккаунта {}: {}", accountId, e.getMessage(), e);
            throw new RuntimeException("Ошибка при получении ордеров: " + e.getMessage(), e);
        }
    }

    public PostOrderResponse placeMarketOrder(String figi, int lots, OrderDirection direction, String accountId) {
        log.info("=== ВХОД В placeMarketOrder ===");
        log.info("Параметры: figi={}, lots={}, direction={}, accountId={}", figi, lots, direction, accountId);
        try {
            if (botControlService.isPanic()) {
                log.warn("Panic-Stop активен: размещение ордеров заблокировано");
                throw new IllegalStateException("Panic-Stop активен: размещение ордеров заблокировано");
            }
            if (!botControlService.tryReserveOrderSlot()) {
                log.warn("Превышен лимит ордеров в минуту");
                throw new IllegalStateException("Превышен лимит ордеров в минуту");
            }
            
            // Коррекция объема в зависимости от доступного количества лотов (не продавать больше, чем есть)
            lots = clampLotsByHoldings(figi, accountId, direction, lots);

            // 🚀 ИСПРАВЛЕНИЕ: Проверка количества лотов после коррекции
            if (lots <= 0) {
                String errorMsg = String.format("Невозможно разместить рыночный ордер: после коррекции количество лотов = %d (должно быть > 0)", lots);
                log.error("❌ {}", errorMsg);
                throw new IllegalStateException(errorMsg);
            }

            // Дополнительная проверка: если лотов слишком много, уменьшаем до разумного лимита
            if (lots > 100) {
                log.warn("Слишком много лотов для размещения: {} -> 100", lots);
                lots = 100;
            }
            
            String orderId = UUID.randomUUID().toString();
            log.info("Размещение рыночного ордера: {} лотов, направление {}, аккаунт {}, ID {}", 
                    lots, direction, accountId, orderId);
            
            // Создаем нулевую цену для рыночного ордера
            Quotation priceObj = Quotation.newBuilder()
                .setUnits(0)
                .setNano(0)
                .build();
            
            apiRateLimiter.acquire();
            CompletableFuture<PostOrderResponse> future = investApiManager.getCurrentInvestApi().getOrdersService().postOrder(
                figi,
                lots,
                priceObj, // используем нулевую цену для рыночного ордера
                direction,
                accountId,
                OrderType.ORDER_TYPE_MARKET,
                orderId
            );
            
            PostOrderResponse response = future.get();
            log.info("Рыночный ордер успешно размещен: orderId={}, status={}, lotsExecuted={}, executedPrice={}", 
                    response.getOrderId(), response.getExecutionReportStatus(), 
                    response.getLotsExecuted(), 
                    response.hasExecutedOrderPrice() ? response.getExecutedOrderPrice() : "N/A");
            try {
                Order entity = new Order();
                entity.setOrderId(response.getOrderId());
                entity.setFigi(figi);
                entity.setOperation(direction.name());
                entity.setStatus(normalizeExecutionStatus(response.getExecutionReportStatus() != null ? response.getExecutionReportStatus().name() : null));
                // Количества
                entity.setRequestedLots(java.math.BigDecimal.valueOf(lots));
                try {
                    // lotsExecuted есть в ответе
                    entity.setExecutedLots(java.math.BigDecimal.valueOf(response.getLotsExecuted()));
                } catch (Exception ignore) {
                    entity.setExecutedLots(java.math.BigDecimal.ZERO);
                }
                // Цена исполнения, если есть
                try {
                    if (response.hasExecutedOrderPrice()) {
                        entity.setPrice(moneyToBigDecimal(response.getExecutedOrderPrice()));
                    } else if (response.hasInitialOrderPrice()) {
                        entity.setPrice(moneyToBigDecimal(response.getInitialOrderPrice()));
                    } else {
                        entity.setPrice(java.math.BigDecimal.ZERO);
                    }
                } catch (Exception ex) {
                    entity.setPrice(java.math.BigDecimal.ZERO);
                }
                entity.setCurrency(null);
                entity.setOrderDate(java.time.LocalDateTime.now());
                entity.setOrderType(OrderType.ORDER_TYPE_MARKET.name());
                try {
                    entity.setCommission(moneyToBigDecimal(response.getExecutedCommission()));
                } catch (Exception ignore) {}
                entity.setMessage(null);
                entity.setAccountId(accountId);
                orderRepository.save(entity);
            } catch (Exception persistEx) {
                log.warn("Не удалось сохранить ордер {} в БД: {}", response.getOrderId(), persistEx.getMessage());
            }
            log.info("=== УСПЕШНОЕ ЗАВЕРШЕНИЕ placeMarketOrder ===");
            return response;
        } catch (InterruptedException | ExecutionException e) {
            log.error("=== ОШИБКА В placeMarketOrder ===");
            String errorMsg = e.getMessage();
            log.error("Ошибка при размещении рыночного ордера: {} лотов, направление {}, аккаунт {}, ошибка: {}", 
                    lots, direction, accountId, errorMsg, e);
            
            // Детальный анализ ошибки
            if (errorMsg != null) {
                if (errorMsg.contains("Недостаточно активов") || errorMsg.contains("30042")) {
                    log.error("ОШИБКА НЕДОСТАТОЧНО СРЕДСТВ: {}", errorMsg);
                } else if (errorMsg.contains("Инструмент недоступен") || errorMsg.contains("30043")) {
                    log.error("ОШИБКА ИНСТРУМЕНТ НЕДОСТУПЕН: {}", errorMsg);
                } else if (errorMsg.contains("Превышен лимит") || errorMsg.contains("30044")) {
                    log.error("ОШИБКА ПРЕВЫШЕН ЛИМИТ: {}", errorMsg);
                }
            }
            
            log.error("=== ВЫБРАСЫВАЕМ ИСКЛЮЧЕНИЕ ИЗ placeMarketOrder ===");
            throw new RuntimeException("Ошибка при размещении рыночного ордера: " + errorMsg, e);
        }
    }

    /**
     * 🚀 ИСПРАВЛЕННЫЙ МЕТОД: Умный лимитный ордер с правильным использованием bid/ask цен
     */
    public PostOrderResponse placeSmartLimitOrder(String figi, int lots, OrderDirection direction, String accountId, BigDecimal marketPrice) {
        int originalLots = lots; // Сохраняем оригинальное значение для fallback
        try {
            // Корректируем лоты до размещения лимитного ордера
            lots = clampLotsByHoldings(figi, accountId, direction, lots);
            if (lots <= 0) {
                throw new IllegalStateException("После коррекции объема лотов не осталось: было=" + originalLots);
            }
            
            // 🚀 ИСПРАВЛЕНИЕ: Получаем актуальные bid/ask цены вместо средней цены
            MarketAnalysisService.BidAskPrices bidAsk = marketAnalysisService.getBidAskPrices(figi);
            BigDecimal limitPrice;
            
            if (bidAsk != null) {
                // Используем реальные bid/ask цены
                BigDecimal offsetPct = getOptimalOffset(figi, direction);
                
                if (direction == OrderDirection.ORDER_DIRECTION_BUY) {
                    // 💰 ПОКУПКА: используем ASK цену + небольшой отступ ВВЕРХ для гарантированного исполнения
                    limitPrice = bidAsk.getAsk().multiply(BigDecimal.ONE.add(offsetPct));
                    log.info("📈 ПОКУПКА [ИСПРАВЛЕНО]: ask={} → лимит={} (отступ +{}%)", 
                        bidAsk.getAsk(), limitPrice, offsetPct.multiply(BigDecimal.valueOf(100)));
                } else {
                    // 💰 ПРОДАЖА: используем BID цену - небольшой отступ ВНИЗ для гарантированного исполнения  
                    limitPrice = bidAsk.getBid().multiply(BigDecimal.ONE.subtract(offsetPct));
                    log.info("📉 ПРОДАЖА [ИСПРАВЛЕНО]: bid={} → лимит={} (отступ -{}%)", 
                        bidAsk.getBid(), limitPrice, offsetPct.multiply(BigDecimal.valueOf(100)));
                }
                
                log.info("💡 Спрэд для {}: {}% (bid={}, ask={}, mid={})", 
                    figi, bidAsk.getSpreadPct().multiply(BigDecimal.valueOf(100)), 
                    bidAsk.getBid(), bidAsk.getAsk(), bidAsk.getMid());
                
            } else {
                // Fallback: если не удалось получить bid/ask, используем старую логику с marketPrice
                log.warn("⚠️ Не удалось получить bid/ask для {}, используем fallback с marketPrice={}", figi, marketPrice);
                BigDecimal offsetPct = getOptimalOffset(figi, direction);
                
                if (direction == OrderDirection.ORDER_DIRECTION_BUY) {
                    // Покупка: небольшой отступ вверх от средней цены
                    limitPrice = marketPrice.multiply(BigDecimal.ONE.add(offsetPct.multiply(BigDecimal.valueOf(0.5))));
                } else {
                    // Продажа: небольшой отступ вниз от средней цены
                    limitPrice = marketPrice.multiply(BigDecimal.ONE.subtract(offsetPct.multiply(BigDecimal.valueOf(0.5))));
                }
            }
            
            return placeLimitOrder(figi, lots, direction, accountId, limitPrice.setScale(4, RoundingMode.HALF_UP).toPlainString());
            
        } catch (Exception e) {
            log.warn("⚠️ Ошибка умного лимита для {}, переходим на рыночный: {}", figi, e.getMessage());
            // 🚀 ИСПРАВЛЕНИЕ: Используем оригинальное значение lots, если скорректированное равно 0
            int lotsToUse = (lots > 0) ? lots : originalLots;
            if (lotsToUse <= 0) {
                log.error("❌ Невозможно разместить ордер: lots={}, originalLots={}, direction={}", lots, originalLots, direction);
                throw new IllegalStateException("Невозможно разместить ордер: количество лотов равно 0 (было=" + originalLots + ")");
            }
            log.info("🔄 Fallback на рыночный ордер: lots={} (было скорректировано до {})", lotsToUse, lots);
            return placeMarketOrder(figi, lotsToUse, direction, accountId);
        }
    }
    
    /**
     * 🚀 ИСПРАВЛЕННЫЙ РАСЧЕТ отступов для гарантированного исполнения
     */
    private BigDecimal getOptimalOffset(String figi, OrderDirection direction) {
        // 💡 НОВАЯ ЛОГИКА: Минимальные отступы для гарантированного исполнения
        BigDecimal baseOffset;
        
        if (isBlueChip(figi)) {
            // Голубые фишки: минимальный отступ (высокая ликвидность)
            baseOffset = new BigDecimal("0.0002"); // 0.02% - очень маленький отступ
        } else if (isETF(figi)) {
            // ETF: небольшой отступ
            baseOffset = new BigDecimal("0.0005"); // 0.05%
        } else {
            // Остальные акции: умеренный отступ
            baseOffset = new BigDecimal("0.001"); // 0.1%
        }
        
        // 🎯 ОДИНАКОВЫЕ ОТСТУПЫ для покупки и продажи (для гарантированного исполнения)
        // При покупке: отступ ВВЕРХ от ask цены
        // При продаже: отступ ВНИЗ от bid цены
        return baseOffset;
    }
    
    /**
     * Проверка является ли инструмент голубой фишкой
     */
    private boolean isBlueChip(String figi) {
        // Список основных голубых фишек
        return figi.equals("BBG004730N88") || // SBER
               figi.equals("BBG004731354") || // GAZP  
               figi.equals("BBG004730RP0") || // LKOH
               figi.equals("BBG00475KKY8") || // NVTK
               figi.equals("BBG004731032") || // GMKN
               figi.equals("BBG004730ZJ9");   // YNDX
    }
    
    /**
     * Проверка является ли инструмент ETF
     */
    private boolean isETF(String figi) {
        // Простая проверка по префиксу или известным ETF
        return figi.startsWith("BBG00") && figi.contains("ETF"); // Упрощенная логика
    }
    
    /**
     * 🚀 НОВЫЙ МЕТОД: Размещение ордера с автоматическим выбором стратегии исполнения
     * Автоматически выбирает между рыночным и лимитным ордером в зависимости от спрэда
     */
    public PostOrderResponse placeOptimalOrder(String figi, int lots, OrderDirection direction, String accountId) {
        try {
            // Получаем информацию о спрэде
            MarketAnalysisService.BidAskPrices bidAsk = marketAnalysisService.getBidAskPrices(figi);
            
            if (bidAsk != null) {
                BigDecimal spreadPct = bidAsk.getSpreadPct();
                
                // Если спрэд очень маленький (< 0.1%), используем рыночный ордер
                if (spreadPct.compareTo(new BigDecimal("0.001")) < 0) {
                    log.info("🚀 ОПТИМАЛЬНЫЙ ВЫБОР для {}: РЫНОЧНЫЙ ордер (спрэд {}% < 0.1%)", 
                        figi, spreadPct.multiply(BigDecimal.valueOf(100)));
                    return placeMarketOrder(figi, lots, direction, accountId);
                } else {
                    // Иначе используем умный лимитный ордер
                    log.info("🚀 ОПТИМАЛЬНЫЙ ВЫБОР для {}: ЛИМИТНЫЙ ордер (спрэд {}% >= 0.1%)", 
                        figi, spreadPct.multiply(BigDecimal.valueOf(100)));
                    return placeSmartLimitOrder(figi, lots, direction, accountId, bidAsk.getMid());
                }
            } else {
                // Fallback: рыночный ордер, если не удалось получить данные о спрэде
                log.warn("⚠️ Не удалось получить данные о спрэде для {}, используем рыночный ордер", figi);
                return placeMarketOrder(figi, lots, direction, accountId);
            }
            
        } catch (Exception e) {
            log.error("Ошибка в placeOptimalOrder для {}: {}", figi, e.getMessage());
            // Fallback: рыночный ордер при ошибке
            return placeMarketOrder(figi, lots, direction, accountId);
        }
    }
    
    /**
     * 🚀 НОВЫЙ МЕТОД: Размещение стоп-ордера
     */
    public PostOrderResponse placeStopOrder(String figi, int lots, OrderDirection direction, String accountId, BigDecimal stopPrice) {
        return placeStopOrder(figi, lots, direction, accountId, stopPrice, "Stop-Loss order");
    }

    /**
     * Размещение стоп-ордера с произвольным сообщением (используется для HARD OCO)
     */
    public PostOrderResponse placeStopOrder(String figi, int lots, OrderDirection direction, String accountId, BigDecimal stopPrice, String message) {
        try {
            String orderId = UUID.randomUUID().toString();
            log.info("🛑 Размещение стоп-ордера: {} лотов, направление {}, стоп-цена {}, аккаунт {}, ID {}", 
                    lots, direction, stopPrice, accountId, orderId);
            
            // Создаем стоп-цену
            Quotation stopPriceObj = Quotation.newBuilder()
                .setUnits(stopPrice.longValue())
                .setNano((int)((stopPrice.remainder(BigDecimal.ONE)).multiply(BigDecimal.valueOf(1_000_000_000)).longValue()))
                .build();
            
            // Пока используем лимитный ордер вместо стоп-ордера (API ограничения)
            // В будущем можно заменить на настоящие стоп-ордера
            log.warn("⚠️ Используем лимитный ордер вместо стоп-ордера (API ограничения)");
            
            apiRateLimiter.acquire();
            CompletableFuture<PostOrderResponse> future = investApiManager.getCurrentInvestApi().getOrdersService().postOrder(
                figi,
                lots,
                stopPriceObj,
                direction,
                accountId,
                OrderType.ORDER_TYPE_LIMIT,
                UUID.randomUUID().toString()
            );
            
            PostOrderResponse response = future.get();
            log.info("🛑 Стоп-ордер успешно размещен: orderId={}, status={}", 
                    response.getOrderId(), response.getExecutionReportStatus());
            
            // Сохраняем в БД
            try {
                Order entity = new Order();
                entity.setOrderId(response.getOrderId());
                entity.setFigi(figi);
                entity.setOperation(direction.name());
                entity.setStatus(normalizeExecutionStatus(response.getExecutionReportStatus() != null ? response.getExecutionReportStatus().name() : null));
                entity.setRequestedLots(BigDecimal.valueOf(lots));
                entity.setPrice(stopPrice);
                entity.setCurrency("RUB");
                entity.setOrderDate(java.time.LocalDateTime.now());
                entity.setOrderType("STOP_LOSS");
                entity.setAccountId(accountId);
                entity.setMessage(message);
                orderRepository.save(entity);
            } catch (Exception persistEx) {
                log.warn("Не удалось сохранить стоп-ордер {} в БД: {}", response.getOrderId(), persistEx.getMessage());
            }
            
            return response;
            
        } catch (InterruptedException | ExecutionException e) {
            String errorMsg = e.getMessage();
            log.error("Ошибка при размещении стоп-ордера: {} лотов, стоп-цена {}, аккаунт {}, ошибка: {}", 
                    lots, stopPrice, accountId, errorMsg, e);
            throw new RuntimeException("Ошибка при размещении стоп-ордера: " + errorMsg, e);
        }
    }
    
    /**
     * 🚀 НОВЫЙ МЕТОД: Автоматическое размещение стоп-лосса после входа в позицию
     * Использует виртуальную систему мониторинга вместо реальных стоп-ордеров
     */
    public void placeAutoStopLoss(String figi, int lots, OrderDirection direction, String accountId, BigDecimal entryPrice, double stopLossPct) {
        try {
            BigDecimal stopPrice;
            String positionType;
            
            if (direction == OrderDirection.ORDER_DIRECTION_BUY) {
                // Для лонга: стоп ниже цены входа
                stopPrice = entryPrice.multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(stopLossPct)));
                positionType = "LONG";
                log.info("📈 ЛОНГ: виртуальный стоп-лосс {} на уровне {} (-{}%)", figi, stopPrice, stopLossPct * 100);
            } else {
                // Для шорта: стоп выше цены входа  
                stopPrice = entryPrice.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(stopLossPct)));
                positionType = "SHORT";
                log.info("📉 ШОРТ: виртуальный стоп-лосс {} на уровне {} (+{}%)", figi, stopPrice, stopLossPct * 100);
            }
            
            // Сохраняем информацию о виртуальном стопе в БД для мониторинга
            try {
                Order virtualStop = new Order();
                virtualStop.setOrderId("VIRTUAL_STOP_" + System.currentTimeMillis());
                virtualStop.setFigi(figi);
                virtualStop.setOperation("VIRTUAL_STOP_" + positionType);
                virtualStop.setStatus("MONITORING");
                virtualStop.setRequestedLots(BigDecimal.valueOf(lots));
                virtualStop.setPrice(stopPrice);
                virtualStop.setCurrency("RUB");
                virtualStop.setOrderDate(java.time.LocalDateTime.now());
                virtualStop.setOrderType("VIRTUAL_STOP_LOSS");
                virtualStop.setAccountId(accountId);
                virtualStop.setMessage("Entry: " + entryPrice + ", StopLoss: " + stopLossPct * 100 + "%");
                orderRepository.save(virtualStop);
                
                log.info("💾 Виртуальный стоп-лосс сохранен в БД: {} → {}", figi, stopPrice);
                
            } catch (Exception e) {
                log.warn("Не удалось сохранить виртуальный стоп в БД: {}", e.getMessage());
            }
            
        } catch (Exception e) {
            log.error("Ошибка создания виртуального стоп-лосса для {}: {}", figi, e.getMessage());
        }
    }
    
    /**
     * 🚀 НОВЫЙ МЕТОД: OCO ордера (One-Cancels-Other) - виртуальная реализация
     * Размещает одновременно Take-Profit и Stop-Loss, при срабатывании одного отменяет другой
     */
    public void placeVirtualOCO(String figi, int lots, OrderDirection originalDirection, String accountId, 
                                BigDecimal entryPrice, double takeProfitPct, double stopLossPct) {
        try {
            BigDecimal takeProfitPrice;
            BigDecimal stopLossPrice;
            String positionType;
            
            if (originalDirection == OrderDirection.ORDER_DIRECTION_BUY) {
                // Для лонга
                takeProfitPrice = entryPrice.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(takeProfitPct)));
                stopLossPrice = entryPrice.multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(stopLossPct)));
                positionType = "LONG";
                log.info("📈 ЛОНГ OCO: TP={} (+{}%), SL={} (-{}%)", 
                    takeProfitPrice, takeProfitPct * 100, stopLossPrice, stopLossPct * 100);
            } else {
                // Для шорта
                takeProfitPrice = entryPrice.multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(takeProfitPct)));
                stopLossPrice = entryPrice.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(stopLossPct)));
                positionType = "SHORT";
                log.info("📉 ШОРТ OCO: TP={} (-{}%), SL={} (+{}%)", 
                    takeProfitPrice, takeProfitPct * 100, stopLossPrice, stopLossPct * 100);
            }
            
            String ocoGroupId = "OCO_" + System.currentTimeMillis();
            
            // Создаем виртуальный Take-Profit
            Order virtualTP = new Order();
            virtualTP.setOrderId("VIRTUAL_TP_" + System.currentTimeMillis());
            virtualTP.setFigi(figi);
            virtualTP.setOperation("VIRTUAL_TP_" + positionType);
            virtualTP.setStatus("MONITORING");
            virtualTP.setRequestedLots(BigDecimal.valueOf(lots));
            virtualTP.setPrice(takeProfitPrice);
            virtualTP.setCurrency("RUB");
            virtualTP.setOrderDate(java.time.LocalDateTime.now());
            virtualTP.setOrderType("VIRTUAL_TAKE_PROFIT");
            virtualTP.setAccountId(accountId);
            virtualTP.setMessage("OCO_GROUP:" + ocoGroupId + " | Entry: " + entryPrice + ", TP: " + takeProfitPct * 100 + "%");
            orderRepository.save(virtualTP);
            
            // Создаем виртуальный Stop-Loss
            Order virtualSL = new Order();
            virtualSL.setOrderId("VIRTUAL_SL_" + (System.currentTimeMillis() + 1));
            virtualSL.setFigi(figi);
            virtualSL.setOperation("VIRTUAL_STOP_" + positionType);
            virtualSL.setStatus("MONITORING");
            virtualSL.setRequestedLots(BigDecimal.valueOf(lots));
            virtualSL.setPrice(stopLossPrice);
            virtualSL.setCurrency("RUB");
            virtualSL.setOrderDate(java.time.LocalDateTime.now());
            virtualSL.setOrderType("VIRTUAL_STOP_LOSS");
            virtualSL.setAccountId(accountId);
            virtualSL.setMessage("OCO_GROUP:" + ocoGroupId + " | Entry: " + entryPrice + ", SL: " + stopLossPct * 100 + "%");
            orderRepository.save(virtualSL);
            
            log.info("🎯 Виртуальный OCO создан: {} | TP: {} | SL: {} | Группа: {}", 
                figi, takeProfitPrice, stopLossPrice, ocoGroupId);
            
        } catch (Exception e) {
            log.error("Ошибка создания виртуального OCO для {}: {}", figi, e.getMessage());
        }
    }

    /**
     * HARD OCO для продакшена: реальные заявки у брокера (TP лимит + SL стоп).
     * Используется, когда hard_stops.enabled=true и режим = production.
     */
    public void placeHardOCO(String figi, int lots, OrderDirection originalDirection, String accountId,
                             BigDecimal entryPrice, double takeProfitPct, double stopLossPct) {
        try {
            // 🚀 ПРОВЕРКА РЕЖИМА: только production
            String currentMode = investApiManager.getCurrentMode();
            if (!"production".equalsIgnoreCase(currentMode)) {
                log.warn("⚠️ HARD OCO доступен только в production режиме. Текущий режим: {}", currentMode);
                throw new IllegalStateException("HARD OCO доступен только в production режиме. Текущий режим: " + currentMode);
            }

            BigDecimal takeProfitPrice;
            BigDecimal stopLossPrice;
            OrderDirection exitDirection;
            String positionType;

            if (originalDirection == OrderDirection.ORDER_DIRECTION_BUY) {
                // Лонг: выходим SELL
                takeProfitPrice = entryPrice.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(takeProfitPct)));
                stopLossPrice   = entryPrice.multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(stopLossPct)));
                exitDirection   = OrderDirection.ORDER_DIRECTION_SELL;
                positionType    = "LONG";
                log.info("📈 HARD OCO ЛОНГ: TP={} (+{}%), SL={} (-{}%)", takeProfitPrice, takeProfitPct * 100, stopLossPrice, stopLossPct * 100);
            } else {
                // Шорт: выходим BUY
                takeProfitPrice = entryPrice.multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(takeProfitPct)));
                stopLossPrice   = entryPrice.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(stopLossPct)));
                exitDirection   = OrderDirection.ORDER_DIRECTION_BUY;
                positionType    = "SHORT";
                log.info("📉 HARD OCO ШОРТ: TP={} (-{}%), SL={} (+{}%)", takeProfitPrice, takeProfitPct * 100, stopLossPrice, stopLossPct * 100);
            }

            String ocoGroupId = "HARD_OCO_" + System.currentTimeMillis();
            String ocoMessage = "OCO_GROUP:" + ocoGroupId + " | Entry: " + entryPrice + " | " + positionType;

            // Размещаем тейк-профит как лимитный ордер с информацией об OCO группе
            PostOrderResponse tpResp = placeLimitOrder(figi, lots, exitDirection, accountId, takeProfitPrice.toPlainString(), ocoMessage);
            log.info("🎯 HARD OCO: TP ордер создан, orderId={}, group={}", tpResp.getOrderId(), ocoGroupId);

            // Обновляем сохраненный ордер в БД с информацией об OCO группе
            try {
                Order tpOrder = orderRepository.findById(tpResp.getOrderId()).orElse(null);
                if (tpOrder != null) {
                    tpOrder.setMessage(ocoMessage + " | TP: " + takeProfitPct * 100 + "%");
                    tpOrder.setOrderType("HARD_OCO_TAKE_PROFIT");
                    orderRepository.save(tpOrder);
                    log.info("💾 HARD OCO TP ордер обновлен в БД: orderId={}, group={}", tpResp.getOrderId(), ocoGroupId);
                }
            } catch (Exception e) {
                log.warn("Не удалось обновить TP ордер в БД: {}", e.getMessage());
            }

            // Размещаем стоп как стоп-ордер с информацией об OCO группе
            PostOrderResponse slResp = placeStopOrder(figi, lots, exitDirection, accountId, stopLossPrice, ocoMessage + " | SL: " + stopLossPct * 100 + "%");
            log.info("🛑 HARD OCO: SL ордер создан, orderId={}, group={}", slResp.getOrderId(), ocoGroupId);

            // Обновляем сохраненный ордер в БД с информацией об OCO группе
            try {
                Order slOrder = orderRepository.findById(slResp.getOrderId()).orElse(null);
                if (slOrder != null) {
                    slOrder.setMessage(ocoMessage + " | SL: " + stopLossPct * 100 + "%");
                    slOrder.setOrderType("HARD_OCO_STOP_LOSS");
                    orderRepository.save(slOrder);
                    log.info("💾 HARD OCO SL ордер обновлен в БД: orderId={}, group={}", slResp.getOrderId(), ocoGroupId);
                }
            } catch (Exception e) {
                log.warn("Не удалось обновить SL ордер в БД: {}", e.getMessage());
            }

            log.info("✅ HARD OCO группа создана: {} | TP orderId={}, SL orderId={}, group={}", 
                figi, tpResp.getOrderId(), slResp.getOrderId(), ocoGroupId);

        } catch (Exception e) {
            log.error("Ошибка создания HARD OCO для {}: {}", figi, e.getMessage(), e);
            throw new RuntimeException("Не удалось создать HARD OCO: " + e.getMessage(), e);
        }
    }

    /**
     * Размещение лимитного ордера с опциональным сообщением для OCO групп
     */
    public PostOrderResponse placeLimitOrder(String figi, int lots, OrderDirection direction, String accountId, String price) {
        return placeLimitOrder(figi, lots, direction, accountId, price, null);
    }

    public PostOrderResponse placeLimitOrder(String figi, int lots, OrderDirection direction, String accountId, String price, String message) {
        try {
            // Корректируем лоты до размещения лимитного ордера
            lots = clampLotsByHoldings(figi, accountId, direction, lots);
            String orderId = UUID.randomUUID().toString();
            log.info("Размещение лимитного ордера: {} лотов, направление {}, аккаунт {}, цена {}, ID {}", 
                    lots, direction, accountId, price, orderId);
            
            // Исправленная логика преобразования цены в Quotation
            Quotation priceObj;
            String[] priceParts = price.split("\\.");
            long units = Long.parseLong(priceParts[0]);
            int nano = 0;
            
            if (priceParts.length > 1 && !priceParts[1].isEmpty()) {
                String fractionalPart = priceParts[1];
                // Ограничиваем дробную часть до 9 символов (максимум для nano)
                if (fractionalPart.length() > 9) {
                    fractionalPart = fractionalPart.substring(0, 9);
                }
                // Дополняем нулями справа до 9 символов
                String nanoStr = fractionalPart + "000000000";
                nano = Integer.parseInt(nanoStr.substring(0, 9));
            }
            
            priceObj = Quotation.newBuilder()
                .setUnits(units)
                .setNano(nano)
                .build();
            
            apiRateLimiter.acquire();
            CompletableFuture<PostOrderResponse> future = investApiManager.getCurrentInvestApi().getOrdersService().postOrder(
                figi,
                lots,
                priceObj,
                direction,
                accountId,
                OrderType.ORDER_TYPE_LIMIT,
                orderId
            );
            
            PostOrderResponse response = future.get();
            log.info("Лимитный ордер успешно размещен: orderId={}, status={}", 
                    response.getOrderId(), response.getExecutionReportStatus());
            try {
                Order entity = new Order();
                entity.setOrderId(response.getOrderId());
                entity.setFigi(figi);
                entity.setOperation(direction.name());
                entity.setStatus(normalizeExecutionStatus(response.getExecutionReportStatus() != null ? response.getExecutionReportStatus().name() : null));
                entity.setRequestedLots(java.math.BigDecimal.valueOf(lots));
                try {
                    entity.setExecutedLots(java.math.BigDecimal.valueOf(response.getLotsExecuted()));
                } catch (Exception ignore) {
                    entity.setExecutedLots(java.math.BigDecimal.ZERO);
                }
                try {
                    if (response.hasExecutedOrderPrice()) {
                        entity.setPrice(moneyToBigDecimal(response.getExecutedOrderPrice()));
                    } else if (response.hasInitialOrderPrice()) {
                        entity.setPrice(moneyToBigDecimal(response.getInitialOrderPrice()));
                    } else {
                        entity.setPrice(quotationToBigDecimal(priceObj));
                    }
                } catch (Exception ex) {
                    entity.setPrice(quotationToBigDecimal(priceObj));
                }
                entity.setCurrency(null);
                entity.setOrderDate(java.time.LocalDateTime.now());
                entity.setOrderType(OrderType.ORDER_TYPE_LIMIT.name());
                try { entity.setCommission(moneyToBigDecimal(response.getExecutedCommission())); } catch (Exception ignore) {}
                entity.setMessage(message); // Сохраняем сообщение (может содержать информацию об OCO группе)
                entity.setAccountId(accountId);
                orderRepository.save(entity);
            } catch (Exception persistEx) {
                log.warn("Не удалось сохранить лимитный ордер {} в БД: {}", response.getOrderId(), persistEx.getMessage());
            }
            return response;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Ошибка при размещении лимитного ордера: {} лотов, направление {}, аккаунт {}, цена {}, ошибка {}", 
                    lots, direction, accountId, price, e.getMessage(), e);
            throw new RuntimeException("Ошибка при размещении лимитного ордера: " + e.getMessage(), e);
        }
    }

    /**
     * Корректирует количество лотов с учётом доступного количества в портфеле.
     * - Для SELL не позволяет продать больше, чем есть в лонге.
     * - Для BUY не позволяет купить больше, чем нужно для полного закрытия шорта (если он есть).
     * Возвращает скорректированное положительное число лотов (или 0, если торговать нельзя).
     */
    private int clampLotsByHoldings(String figi, String accountId, OrderDirection direction, int requestedLots) {
        try {
            int lots = Math.max(0, requestedLots);
            if (lots == 0) return 0;

            var portfolio = portfolioService.getPortfolio(accountId);
            var positionOpt = portfolio.getPositions().stream()
                .filter(p -> figi.equals(p.getFigi()))
                .findFirst();

            if (positionOpt.isEmpty()) {
                log.info("Коррекция объема: позиции по {} нет, requestedLots={} → {} (без изменений)", figi, requestedLots, lots);
                return lots; // нет позиции — не ограничиваем покупку
            }

            var position = positionOpt.get();
            String instrumentType = position.getInstrumentType() != null ? position.getInstrumentType() : "share";
            int lotSize = lotSizeService.getLotSize(figi, instrumentType);
            java.math.BigDecimal positionLots = resolvePositionLots(position, lotSize);
            int availableLots = positionLots.abs().setScale(0, java.math.RoundingMode.DOWN).intValue();

            int finalLots = lots;
            if (direction == OrderDirection.ORDER_DIRECTION_SELL) {
                // Нельзя продать больше, чем есть лонговых лотов
                if (positionLots.signum() > 0) {
                    finalLots = Math.min(lots, availableLots);
                } else {
                    // нет лонга — запрещаем SELL
                    finalLots = 0;
                }
            } else if (direction == OrderDirection.ORDER_DIRECTION_BUY) {
                // Если есть шорт, не покупаем больше, чем для его закрытия
                if (positionLots.signum() < 0) {
                    finalLots = Math.min(lots, availableLots);
                }
            }

            log.info("Коррекция лотов [{}]: positionLots={}, lotSize={}, requested={}, available={}, final={}",
                figi, positionLots, lotSize, requestedLots, availableLots, finalLots);

            return finalLots;
        } catch (Exception e) {
            log.warn("Не удалось скорректировать количество лотов по {}: {}. Используем requestedLots={}.", figi, e.getMessage(), requestedLots);
            return requestedLots;
        }
    }

    /**
     * Приведение статусов API к унифицированным значениям, чтобы остальные сервисы (например, cooldown)
     * могли корректно определять факт совершенной сделки.
     */
    private String normalizeExecutionStatus(String statusName) {
        if (statusName == null) return "UNKNOWN";
        String s = statusName.toUpperCase();
        if (s.contains("FILL")) return "FILLED"; // EXECUTION_REPORT_STATUS_FILL / PARTIALLYFILL
        if (s.contains("REJECT")) return "REJECTED";
        if (s.contains("PENDING") || s.endsWith("_NEW") || s.equals("NEW")) return "NEW";
        return s;
    }

    public void cancelOrder(String accountId, String orderId) {
        try {
            log.info("Отмена ордера: accountId={}, orderId={}", accountId, orderId);
            apiRateLimiter.acquire();
            CompletableFuture<java.time.Instant> future = investApiManager.getCurrentInvestApi().getOrdersService().cancelOrder(accountId, orderId);
            java.time.Instant cancelTime = future.get();
            log.info("Ордер успешно отменен: accountId={}, orderId={}, cancelTime={}", accountId, orderId, cancelTime);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Ошибка при отмене ордера: accountId={}, orderId={}, error={}", 
                    accountId, orderId, e.getMessage(), e);
            throw new RuntimeException("Ошибка при отмене ордера: " + e.getMessage(), e);
        }
    }

    /**
     * Отмена всех активных ордеров (NEW/ PARTIALLY_FILLED)
     */
    public Map<String, Object> cancelAllActiveOrders(String accountId) {
        Map<String, Object> result = new java.util.HashMap<>();
        int total = 0;
        int cancelled = 0;
        int failed = 0;
        java.util.List<String> errors = new java.util.ArrayList<>();
        try {
            List<OrderState> orders = getOrders(accountId);
            for (OrderState o : orders) {
                String status = o.getExecutionReportStatus().name();
                if ("EXECUTION_REPORT_STATUS_NEW".equals(status) || "EXECUTION_REPORT_STATUS_PARTIALLY_FILLED".equals(status)) {
                    total++;
                    try {
                        cancelOrder(accountId, o.getOrderId());
                        cancelled++;
                    } catch (RuntimeException ex) {
                        failed++;
                        errors.add(o.getOrderId() + ": " + ex.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Ошибка отмены ордеров: " + e.getMessage(), e);
        }
        result.put("totalCandidates", total);
        result.put("cancelled", cancelled);
        result.put("failed", failed);
        result.put("errors", errors);
        return result;
    }

    private java.math.BigDecimal quotationToBigDecimal(ru.tinkoff.piapi.contract.v1.Quotation q) {
        if (q == null) return java.math.BigDecimal.ZERO;
        long units = 0L; int nano = 0;
        try { units = q.getUnits(); } catch (Exception ignore) {}
        try { nano = q.getNano(); } catch (Exception ignore) {}
        String str = units + "." + String.format("%09d", nano);
        try { return new java.math.BigDecimal(str); } catch (Exception e) { return java.math.BigDecimal.ZERO; }
    }

    private java.math.BigDecimal moneyToBigDecimal(Object money) {
        try {
            if (money == null) return null;
            // money может быть MoneyValue, пытаемся получить units/nano
            java.lang.reflect.Method getUnits = money.getClass().getMethod("getUnits");
            java.lang.reflect.Method getNano = money.getClass().getMethod("getNano");
            long units = (long) getUnits.invoke(money);
            int nano = (int) getNano.invoke(money);
            return new java.math.BigDecimal(units + "." + String.format("%09d", nano));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Определяет фактическое количество лотов в портфеле.
     * Приоритетно используем поле quantityLots, при его отсутствии делим quantity на размер лота.
     */
    private java.math.BigDecimal resolvePositionLots(Position position, int lotSize) {
        java.math.BigDecimal lots = null;
        try {
            lots = position.getQuantityLots();
        } catch (Exception ignore) { }

        if (lots != null) {
            return lots;
        }

        java.math.BigDecimal quantity = position.getQuantity();
        if (quantity == null) {
            return java.math.BigDecimal.ZERO;
        }

        if (lotSize <= 1) {
            return quantity;
        }

        try {
            return quantity.divide(new java.math.BigDecimal(lotSize), 6, java.math.RoundingMode.DOWN);
        } catch (Exception e) {
            return quantity;
        }
    }
} 