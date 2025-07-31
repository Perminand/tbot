package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.tinkoff.piapi.core.InvestApi;
import ru.tinkoff.piapi.contract.v1.OrderDirection;
import ru.tinkoff.piapi.contract.v1.OrderType;
import ru.tinkoff.piapi.contract.v1.PostOrderResponse;
import ru.tinkoff.piapi.contract.v1.OrderState;
import ru.tinkoff.piapi.contract.v1.Quotation;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradingService {
    
    private final InvestApi investApi;
    
    /**
     * Размещение рыночного ордера на покупку
     */
    public Mono<String> placeMarketBuyOrder(String figi, int lots, String accountId) {
        return Mono.fromCallable(() -> {
            log.info("Попытка размещения рыночного ордера на покупку: figi={}, lots={}, accountId={}", figi, lots, accountId);
            String orderId = generateOrderId();
            log.info("Сгенерирован orderId: {}", orderId);
            
            PostOrderResponse response = investApi.getOrdersService().postOrderSync(
                    figi, lots, null, OrderDirection.ORDER_DIRECTION_BUY, 
                    accountId, OrderType.ORDER_TYPE_MARKET, orderId);
            
            log.info("Размещен рыночный ордер на покупку: {} лотов {}", lots, figi);
            log.info("Ответ от API: {}", response);
            return response.toString();
        }).doOnError(error -> {
            log.error("Ошибка при размещении ордера на покупку: {}", error.getMessage());
            log.error("Полный стек ошибки:", error);
        });
    }
    
    /**
     * Размещение рыночного ордера на продажу
     */
    public Mono<String> placeMarketSellOrder(String figi, int lots, String accountId) {
        return Mono.fromCallable(() -> {
            String orderId = generateOrderId();
            
            PostOrderResponse response = investApi.getOrdersService().postOrderSync(
                    figi, lots, null, OrderDirection.ORDER_DIRECTION_SELL, 
                    accountId, OrderType.ORDER_TYPE_MARKET, orderId);
            log.info("Размещен рыночный ордер на продажу: {} лотов {}", lots, figi);
            return response.toString();
        }).doOnError(error -> log.error("Ошибка при размещении ордера на продажу: {}", error.getMessage()));
    }
    
    /**
     * Размещение лимитного ордера
     */
    public Mono<String> placeLimitOrder(String figi, int lots, BigDecimal price, String direction, String accountId) {
        return Mono.fromCallable(() -> {
            OrderDirection orderDirection = "buy".equalsIgnoreCase(direction) ? 
                    OrderDirection.ORDER_DIRECTION_BUY : OrderDirection.ORDER_DIRECTION_SELL;
            
            Quotation priceQuotation = Quotation.newBuilder()
                    .setUnits((long) price.longValue())
                    .setNano((int) ((price.doubleValue() - price.longValue()) * 1_000_000_000))
                    .build();
            
            String orderId = generateOrderId();
            
            PostOrderResponse response = investApi.getOrdersService().postOrderSync(
                    figi, lots, priceQuotation, orderDirection, 
                    accountId, OrderType.ORDER_TYPE_LIMIT, orderId);
            log.info("Размещен лимитный ордер: {} лотов {} по цене {}", lots, figi, price);
            return response.toString();
        }).doOnError(error -> log.error("Ошибка при размещении лимитного ордера: {}", error.getMessage()));
    }
    
    /**
     * Отмена ордера
     */
    public Mono<String> cancelOrder(String orderId, String accountId) {
        return Mono.fromCallable(() -> {
            investApi.getOrdersService().cancelOrderSync(accountId, orderId);
            log.info("Отменен ордер: {}", orderId);
            return "{\"status\": \"cancelled\"}";
        }).doOnError(error -> log.error("Ошибка при отмене ордера: {}", error.getMessage()));
    }
    
    /**
     * Получение статуса ордера
     */
    public Mono<String> getOrderState(String orderId, String accountId) {
        return Mono.fromCallable(() -> {
            OrderState orderState = investApi.getOrdersService().getOrderStateSync(accountId, orderId);
            log.info("Получен статус ордера: {}", orderId);
            return orderState.toString();
        }).doOnError(error -> log.error("Ошибка при получении статуса ордера: {}", error.getMessage()));
    }
    
    /**
     * Получение списка активных ордеров
     */
    public Mono<String> getActiveOrders(String accountId) {
        return Mono.fromCallable(() -> {
            var orders = investApi.getOrdersService().getOrdersSync(accountId);
            log.info("Получен список активных ордеров для аккаунта: {}", accountId);
            return orders.toString();
        }).doOnError(error -> log.error("Ошибка при получении активных ордеров: {}", error.getMessage()));
    }
    
    /**
     * Генерация уникального ID ордера
     */
    private String generateOrderId() {
        return "order_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
    }
} 