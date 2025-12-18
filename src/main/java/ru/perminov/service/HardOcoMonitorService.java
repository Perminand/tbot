package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.perminov.model.Order;
import ru.perminov.repository.OrderRepository;
import ru.tinkoff.piapi.contract.v1.OrderState;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Сервис для мониторинга HARD OCO ордеров и отмены парных ордеров при срабатывании одного
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HardOcoMonitorService {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    /**
     * Мониторинг HARD OCO ордеров каждые 30 секунд
     */
    @Scheduled(fixedRate = 30000)
    public void monitorHardOcoOrders() {
        try {
            // Получаем все активные HARD OCO ордера из БД
            List<Order> hardOcoOrders = orderRepository.findAll().stream()
                    .filter(order -> order.getMessage() != null && order.getMessage().contains("OCO_GROUP:"))
                    .filter(order -> order.getOrderType() != null && 
                            (order.getOrderType().startsWith("HARD_OCO_") || 
                             order.getOrderType().equals("STOP_LOSS") || 
                             order.getOrderType().equals("ORDER_TYPE_LIMIT")))
                    .filter(order -> {
                        String status = order.getStatus();
                        return status != null && 
                               !status.equals("FILLED") && 
                               !status.equals("EXECUTED") && 
                               !status.equals("CANCELLED") && 
                               !status.equals("CANCELLED_BY_OCO");
                    })
                    .collect(Collectors.toList());

            if (hardOcoOrders.isEmpty()) {
                return;
            }

            log.debug("🔍 Мониторинг {} HARD OCO ордеров", hardOcoOrders.size());

            for (Order order : hardOcoOrders) {
                try {
                    checkHardOcoOrder(order);
                    Thread.sleep(100); // Небольшая задержка между проверками
                } catch (Exception e) {
                    log.error("Ошибка проверки HARD OCO ордера {}: {}", order.getOrderId(), e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("Ошибка мониторинга HARD OCO ордеров: {}", e.getMessage());
        }
    }

    /**
     * Проверка конкретного HARD OCO ордера
     */
    private void checkHardOcoOrder(Order order) {
        try {
            String accountId = order.getAccountId();
            String orderId = order.getOrderId();

            // Получаем актуальный статус ордера у брокера
            List<OrderState> brokerOrders = orderService.getOrders(accountId);
            OrderState brokerOrder = brokerOrders.stream()
                    .filter(o -> o.getOrderId().equals(orderId))
                    .findFirst()
                    .orElse(null);

            if (brokerOrder == null) {
                // Ордер не найден у брокера - возможно уже исполнен или отменен
                log.debug("HARD OCO ордер {} не найден у брокера, возможно уже исполнен", orderId);
                return;
            }

            String brokerStatus = brokerOrder.getExecutionReportStatus().name();
            String normalizedStatus = normalizeExecutionStatus(brokerStatus);

            // Если ордер исполнен, отменяем парный ордер в OCO группе
            if ("FILLED".equals(normalizedStatus) || "EXECUTED".equals(normalizedStatus)) {
                log.info("🚨 HARD OCO ордер {} исполнен (статус: {}), отменяем парный ордер", orderId, brokerStatus);
                
                // Обновляем статус в БД
                order.setStatus(normalizedStatus);
                orderRepository.save(order);

                // Отменяем парный ордер в OCO группе
                String message = order.getMessage();
                if (message != null && message.contains("OCO_GROUP:")) {
                    String ocoGroupId = extractOcoGroupId(message);
                    cancelPairedOcoOrder(ocoGroupId, orderId, accountId);
                }
            } else if (!normalizedStatus.equals(order.getStatus())) {
                // Обновляем статус в БД если он изменился
                order.setStatus(normalizedStatus);
                orderRepository.save(order);
            }

        } catch (Exception e) {
            log.error("Ошибка проверки HARD OCO ордера {}: {}", order.getOrderId(), e.getMessage());
        }
    }

    /**
     * Отмена парного ордера в HARD OCO группе
     */
    private void cancelPairedOcoOrder(String ocoGroupId, String executedOrderId, String accountId) {
        try {
            if (ocoGroupId == null) {
                return;
            }

            // Находим все ордера в этой OCO группе
            List<Order> ocoOrders = orderRepository.findAll().stream()
                    .filter(order -> order.getMessage() != null && order.getMessage().contains("OCO_GROUP:" + ocoGroupId))
                    .filter(order -> {
                        String status = order.getStatus();
                        return status != null && 
                               !status.equals("FILLED") && 
                               !status.equals("EXECUTED") && 
                               !status.equals("CANCELLED") && 
                               !status.equals("CANCELLED_BY_OCO");
                    })
                    .collect(Collectors.toList());

            for (Order ocoOrder : ocoOrders) {
                // Отменяем все кроме исполненного
                if (!ocoOrder.getOrderId().equals(executedOrderId)) {
                    try {
                        // Отменяем ордер у брокера
                        orderService.cancelOrder(accountId, ocoOrder.getOrderId());
                        log.info("🚫 HARD OCO: Отменен парный ордер {} у брокера (исполнен {})", 
                            ocoOrder.getOrderId(), executedOrderId);
                    } catch (Exception e) {
                        log.warn("Не удалось отменить ордер {} у брокера: {}", ocoOrder.getOrderId(), e.getMessage());
                    }

                    // Обновляем статус в БД
                    ocoOrder.setStatus("CANCELLED_BY_OCO");
                    ocoOrder.setMessage(ocoOrder.getMessage() + " | Cancelled by paired order execution");
                    orderRepository.save(ocoOrder);

                    log.info("💾 HARD OCO: Парный ордер {} отмечен как отмененный в БД", ocoOrder.getOrderId());
                }
            }

        } catch (Exception e) {
            log.error("Ошибка отмены парных HARD OCO ордеров: {}", e.getMessage());
        }
    }

    /**
     * Извлечение ID OCO группы из сообщения
     */
    private String extractOcoGroupId(String message) {
        try {
            String[] parts = message.split("OCO_GROUP:");
            if (parts.length > 1) {
                return parts[1].split("\\|")[0].trim();
            }
        } catch (Exception e) {
            log.warn("Ошибка извлечения OCO группы из сообщения: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Приведение статусов API к унифицированным значениям
     */
    private String normalizeExecutionStatus(String statusName) {
        if (statusName == null) return "UNKNOWN";
        String s = statusName.toUpperCase();
        if (s.contains("FILL")) return "FILLED";
        if (s.contains("REJECT")) return "REJECTED";
        if (s.contains("CANCEL")) return "CANCELLED";
        if (s.contains("PENDING") || s.endsWith("_NEW") || s.equals("NEW")) return "NEW";
        return s;
    }
}

