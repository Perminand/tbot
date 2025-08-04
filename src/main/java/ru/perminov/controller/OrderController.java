package ru.perminov.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.perminov.service.OrderService;
import ru.tinkoff.piapi.contract.v1.OrderDirection;
import ru.tinkoff.piapi.contract.v1.OrderState;
import ru.tinkoff.piapi.contract.v1.PostOrderResponse;
import ru.perminov.dto.OrderResponseDto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {
    private final OrderService orderService;

    @GetMapping
    public ResponseEntity<?> getOrders(@RequestParam("accountId") String accountId) {
        try {
            List<OrderState> orders = orderService.getOrders(accountId);
            return ResponseEntity.ok(orders);
        } catch (Exception e) {
            log.error("Error getting orders for accountId: " + accountId, e);
            return ResponseEntity.internalServerError()
                    .body("Error getting orders: " + e.getMessage());
        }
    }

    @GetMapping("/test-instrument")
    public ResponseEntity<?> testInstrument(@RequestParam("figi") String figi) {
        try {
            Map<String, Object> result = new HashMap<>();
            result.put("figi", figi);
            result.put("message", "Instrument test endpoint - check if instrument is available for trading");
            result.put("note", "Most instruments in sandbox may not be available for trading");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error testing instrument: " + figi, e);
            return ResponseEntity.internalServerError()
                    .body("Error testing instrument: " + e.getMessage());
        }
    }

    @PostMapping("/market")
    public ResponseEntity<?> placeMarketOrder(
            @RequestParam("figi") String figi,
            @RequestParam("lots") int lots,
            @RequestParam("direction") String direction,
            @RequestParam("accountId") String accountId,
            @RequestParam(value = "price", required = false) String price) {
        try {
            log.info("Получен запрос на размещение рыночного ордера: figi={}, lots={}, direction={}, accountId={}", 
                    figi, lots, direction, accountId);
            
            OrderDirection orderDirection = "buy".equalsIgnoreCase(direction) ? 
                OrderDirection.ORDER_DIRECTION_BUY : OrderDirection.ORDER_DIRECTION_SELL;
            
            PostOrderResponse response = orderService.placeMarketOrder(figi, lots, orderDirection, accountId);
            OrderResponseDto responseDto = OrderResponseDto.from(response);
            log.info("Рыночный ордер успешно размещен: orderId={}", response.getOrderId());
            return ResponseEntity.ok(responseDto);
        } catch (Exception e) {
            log.error("Ошибка при размещении рыночного ордера: figi={}, lots={}, direction={}, accountId={}, error={}", 
                    figi, lots, direction, accountId, e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Ошибка при размещении рыночного ордера: " + e.getMessage());
            error.put("figi", figi);
            error.put("lots", lots);
            error.put("direction", direction);
            error.put("accountId", accountId);
            error.put("details", e.getClass().getSimpleName());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @PostMapping("/limit")
    public ResponseEntity<?> placeLimitOrder(
            @RequestParam("figi") String figi,
            @RequestParam("lots") int lots,
            @RequestParam("direction") String direction,
            @RequestParam("accountId") String accountId,
            @RequestParam("price") String price) {
        try {
            OrderDirection orderDirection = "buy".equalsIgnoreCase(direction) ? 
                OrderDirection.ORDER_DIRECTION_BUY : OrderDirection.ORDER_DIRECTION_SELL;
            
            PostOrderResponse response = orderService.placeLimitOrder(figi, lots, orderDirection, accountId, price);
            OrderResponseDto responseDto = OrderResponseDto.from(response);
            return ResponseEntity.ok(responseDto);
        } catch (Exception e) {
            log.error("Error placing limit order", e);
            return ResponseEntity.internalServerError()
                    .body("Error placing limit order: " + e.getMessage());
        }
    }

    @PostMapping("/cancel")
    public ResponseEntity<?> cancelOrder(
            @RequestParam("accountId") String accountId,
            @RequestParam("orderId") String orderId) {
        try {
            orderService.cancelOrder(accountId, orderId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error canceling order", e);
            return ResponseEntity.internalServerError()
                    .body("Error canceling order: " + e.getMessage());
        }
    }

    @GetMapping("/info/{orderId}")
    public ResponseEntity<?> getOrderInfo(@PathVariable String orderId, @RequestParam("accountId") String accountId) {
        try {
            log.info("Получение информации об ордере: orderId={}, accountId={}", orderId, accountId);
            
            // Получаем все ордера и ищем нужный
            List<OrderState> orders = orderService.getOrders(accountId);
            OrderState order = orders.stream()
                    .filter(o -> o.getOrderId().equals(orderId))
                    .findFirst()
                    .orElse(null);
            
            if (order != null) {
                Map<String, Object> orderInfo = new HashMap<>();
                orderInfo.put("orderId", order.getOrderId());
                orderInfo.put("figi", order.getFigi());
                orderInfo.put("direction", order.getDirection().name());
                orderInfo.put("orderType", order.getOrderType().name());
                orderInfo.put("lotsRequested", order.getLotsRequested());
                orderInfo.put("lotsExecuted", order.getLotsExecuted());
                orderInfo.put("executionReportStatus", order.getExecutionReportStatus().name());
                // Убираем несуществующие методы
                // orderInfo.put("message", order.getMessage());
                
                if (order.hasInitialOrderPrice()) {
                    orderInfo.put("initialOrderPrice", order.getInitialOrderPrice().getUnits() + "." + 
                        String.format("%09d", order.getInitialOrderPrice().getNano()));
                }
                
                if (order.hasExecutedOrderPrice()) {
                    orderInfo.put("executedOrderPrice", order.getExecutedOrderPrice().getUnits() + "." + 
                        String.format("%09d", order.getExecutedOrderPrice().getNano()));
                }
                
                // Убираем несуществующие методы
                // orderInfo.put("createdAt", order.getCreatedAt());
                // orderInfo.put("updatedAt", order.getUpdatedAt());
                
                return ResponseEntity.ok(orderInfo);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Ошибка при получении информации об ордере: orderId={}, accountId={}, error={}", 
                    orderId, accountId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body("Ошибка при получении информации об ордере: " + e.getMessage());
        }
    }
} 