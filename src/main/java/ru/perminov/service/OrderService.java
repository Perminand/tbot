package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.contract.v1.OrderState;
import ru.tinkoff.piapi.contract.v1.PostOrderResponse;
import ru.tinkoff.piapi.core.InvestApi;
import ru.tinkoff.piapi.contract.v1.OrderDirection;
import ru.tinkoff.piapi.contract.v1.OrderType;
import ru.tinkoff.piapi.contract.v1.Quotation;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {
    private final InvestApi investApi;

    public List<OrderState> getOrders(String accountId) {
        try {
            log.info("Получение ордеров для аккаунта: {}", accountId);
            CompletableFuture<List<OrderState>> future = investApi.getOrdersService().getOrders(accountId);
            List<OrderState> orders = future.get();
            log.info("Получено {} ордеров для аккаунта {}", orders.size(), accountId);
            return orders;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Ошибка при получении ордеров для аккаунта {}: {}", accountId, e.getMessage(), e);
            throw new RuntimeException("Ошибка при получении ордеров: " + e.getMessage(), e);
        }
    }

    public PostOrderResponse placeMarketOrder(String figi, int lots, OrderDirection direction, String accountId) {
        try {
            String orderId = UUID.randomUUID().toString();
            log.info("Размещение рыночного ордера: figi={}, lots={}, direction={}, accountId={}, orderId={}", 
                    figi, lots, direction, accountId, orderId);
            
            // Создаем нулевую цену для рыночного ордера
            Quotation priceObj = Quotation.newBuilder()
                .setUnits(0)
                .setNano(0)
                .build();
            
            CompletableFuture<PostOrderResponse> future = investApi.getOrdersService().postOrder(
                figi,
                lots,
                priceObj, // используем нулевую цену для рыночного ордера
                direction,
                accountId,
                OrderType.ORDER_TYPE_MARKET,
                orderId
            );
            
            PostOrderResponse response = future.get();
            log.info("Рыночный ордер успешно размещен: orderId={}, status={}", 
                    response.getOrderId(), response.getExecutionReportStatus());
            return response;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Ошибка при размещении рыночного ордера: figi={}, lots={}, direction={}, accountId={}, error={}", 
                    figi, lots, direction, accountId, e.getMessage(), e);
            throw new RuntimeException("Ошибка при размещении рыночного ордера: " + e.getMessage(), e);
        }
    }

    public PostOrderResponse placeLimitOrder(String figi, int lots, OrderDirection direction, String accountId, String price) {
        try {
            String orderId = UUID.randomUUID().toString();
            log.info("Размещение лимитного ордера: figi={}, lots={}, direction={}, accountId={}, price={}, orderId={}", 
                    figi, lots, direction, accountId, price, orderId);
            
            Quotation priceObj = Quotation.newBuilder()
                .setUnits(Long.parseLong(price.split("\\.")[0]))
                .setNano(Integer.parseInt(price.split("\\.")[1] + "000000000".substring(price.split("\\.")[1].length())))
                .build();
            
            CompletableFuture<PostOrderResponse> future = investApi.getOrdersService().postOrder(
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
            return response;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Ошибка при размещении лимитного ордера: figi={}, lots={}, direction={}, accountId={}, price={}, error={}", 
                    figi, lots, direction, accountId, price, e.getMessage(), e);
            throw new RuntimeException("Ошибка при размещении лимитного ордера: " + e.getMessage(), e);
        }
    }

    public void cancelOrder(String accountId, String orderId) {
        try {
            log.info("Отмена ордера: accountId={}, orderId={}", accountId, orderId);
            CompletableFuture<java.time.Instant> future = investApi.getOrdersService().cancelOrder(accountId, orderId);
            java.time.Instant cancelTime = future.get();
            log.info("Ордер успешно отменен: accountId={}, orderId={}, cancelTime={}", accountId, orderId, cancelTime);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Ошибка при отмене ордера: accountId={}, orderId={}, error={}", 
                    accountId, orderId, e.getMessage(), e);
            throw new RuntimeException("Ошибка при отмене ордера: " + e.getMessage(), e);
        }
    }
} 