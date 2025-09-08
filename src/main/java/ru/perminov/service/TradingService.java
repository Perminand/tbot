package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.tinkoff.piapi.contract.v1.OrderDirection;
import ru.tinkoff.piapi.contract.v1.PostOrderResponse;
import java.math.BigDecimal;
import ru.tinkoff.piapi.contract.v1.OrderState;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradingService {
    
    private final InvestApiManager investApiManager;
    private final OrderService orderService;
    private final MarketAnalysisService marketAnalysisService;
    
    /**
     * Размещение рыночного ордера на покупку (ИСПРАВЛЕНО)
     */
    public Mono<String> placeMarketBuyOrder(String figi, int lots, String accountId) {
        return Mono.fromCallable(() -> {
            log.info("🚀 ИСПРАВЛЕННАЯ ПОКУПКА: {} лотов, аккаунт {}", lots, accountId);
            // Используем новый оптимальный метод размещения ордеров
            PostOrderResponse response = orderService.placeOptimalOrder(figi, lots, OrderDirection.ORDER_DIRECTION_BUY, accountId);
            log.info("✅ Размещен оптимальный ордер на покупку: {} лотов", lots);
            return response.toString();
        }).doOnError(error -> {
            log.error("❌ Ошибка при размещении ордера на покупку: {}", error.getMessage());
            log.error("Полный стек ошибки:", error);
        });
    }
    
    /**
     * Размещение рыночного ордера на продажу (ИСПРАВЛЕНО)
     */
    public Mono<String> placeMarketSellOrder(String figi, int lots, String accountId) {
        return Mono.fromCallable(() -> {
            log.info("🚀 ИСПРАВЛЕННАЯ ПРОДАЖА: {} лотов, аккаунт {}", lots, accountId);
            // Используем новый оптимальный метод размещения ордеров
            PostOrderResponse response = orderService.placeOptimalOrder(figi, lots, OrderDirection.ORDER_DIRECTION_SELL, accountId);
            log.info("✅ Размещен оптимальный ордер на продажу: {} лотов", lots);
            return response.toString();
        }).doOnError(error -> log.error("❌ Ошибка при размещении ордера на продажу: {}", error.getMessage()));
    }
    
    /**
     * Размещение лимитного ордера
     */
    public Mono<String> placeLimitOrder(String figi, int lots, BigDecimal price, String direction, String accountId) {
        return Mono.fromCallable(() -> {
            OrderDirection orderDirection = "buy".equalsIgnoreCase(direction) ? 
                    OrderDirection.ORDER_DIRECTION_BUY : OrderDirection.ORDER_DIRECTION_SELL;
            String priceStr = price.stripTrailingZeros().toPlainString();
            PostOrderResponse response = orderService.placeLimitOrder(figi, lots, orderDirection, accountId, priceStr);
            log.info("Размещен лимитный ордер через OrderService: {} лотов по цене {}", lots, price);
            return response.toString();
        }).doOnError(error -> log.error("Ошибка при размещении лимитного ордера: {}", error.getMessage()));
    }
    
    /**
     * Отмена ордера
     */
    public Mono<String> cancelOrder(String orderId, String accountId) {
        return Mono.fromCallable(() -> {
            investApiManager.getCurrentInvestApi().getOrdersService().cancelOrderSync(accountId, orderId);
            log.info("Отменен ордер: {}", orderId);
            return "{\"status\": \"cancelled\"}";
        }).doOnError(error -> log.error("Ошибка при отмене ордера: {}", error.getMessage()));
    }
    
    /**
     * Получение статуса ордера
     */
    public Mono<String> getOrderState(String orderId, String accountId) {
        return Mono.fromCallable(() -> {
            OrderState orderState = investApiManager.getCurrentInvestApi().getOrdersService().getOrderStateSync(accountId, orderId);
            log.info("Получен статус ордера: {}", orderId);
            return orderState.toString();
        }).doOnError(error -> log.error("Ошибка при получении статуса ордера: {}", error.getMessage()));
    }
    
    /**
     * Получение списка активных ордеров
     */
    public Mono<String> getActiveOrders(String accountId) {
        return Mono.fromCallable(() -> {
            var orders = investApiManager.getCurrentInvestApi().getOrdersService().getOrdersSync(accountId);
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