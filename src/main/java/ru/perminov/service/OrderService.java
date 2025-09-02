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
import ru.perminov.repository.OrderRepository;
import ru.perminov.model.Order;

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
                entity.setStatus(response.getExecutionReportStatus().name());
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

    public PostOrderResponse placeLimitOrder(String figi, int lots, OrderDirection direction, String accountId, String price) {
        try {
            String orderId = UUID.randomUUID().toString();
            log.info("Размещение лимитного ордера: {} лотов, направление {}, аккаунт {}, цена {}, ID {}", 
                    lots, direction, accountId, price, orderId);
            
            Quotation priceObj = Quotation.newBuilder()
                .setUnits(Long.parseLong(price.split("\\.")[0]))
                .setNano(Integer.parseInt(price.split("\\.")[1] + "000000000".substring(price.split("\\.")[1].length())))
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
                entity.setStatus(response.getExecutionReportStatus().name());
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
                entity.setMessage(null);
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
} 