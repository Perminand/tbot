package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.perminov.model.Order;
import ru.perminov.repository.OrderRepository;
import ru.tinkoff.piapi.contract.v1.OrderDirection;

import java.math.BigDecimal;
import java.util.List;

/**
 * 🚀 НОВЫЙ СЕРВИС: Мониторинг виртуальных стоп-лоссов
 * Проверяет цены и автоматически исполняет стопы при достижении уровней
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VirtualStopMonitorService {
    
    private final OrderRepository orderRepository;
    private final MarketAnalysisService marketAnalysisService;
    private final OrderService orderService;
    private final BotLogService botLogService;
    private final InstrumentNameService instrumentNameService;
    
    /**
     * Мониторинг виртуальных стопов и OCO ордеров каждые 30 секунд
     */
    @Scheduled(fixedRate = 30000)
    public void monitorVirtualStops() {
        try {
            // Получаем все активные виртуальные ордера
            List<Order> virtualStops = orderRepository.findByStatusAndOrderTypeIn("MONITORING", 
                List.of("VIRTUAL_STOP_LOSS", "VIRTUAL_TAKE_PROFIT"));
            
            if (virtualStops.isEmpty()) {
                return;
            }
            
            log.debug("🔍 Мониторинг {} виртуальных ордеров (стопы + OCO)", virtualStops.size());
            
            for (Order virtualOrder : virtualStops) {
                try {
                    checkVirtualOrder(virtualOrder);
                    Thread.sleep(100); // Небольшая задержка между проверками
                } catch (Exception e) {
                    log.error("Ошибка проверки виртуального ордера {}: {}", virtualOrder.getOrderId(), e.getMessage());
                }
            }
            
        } catch (Exception e) {
            log.error("Ошибка мониторинга виртуальных ордеров: {}", e.getMessage());
        }
    }
    
    /**
     * Проверка конкретного виртуального ордера (стоп или тейк-профит)
     */
    private void checkVirtualOrder(Order virtualOrder) {
        try {
            String figi = virtualOrder.getFigi();
            BigDecimal triggerPrice = virtualOrder.getPrice();
            String operation = virtualOrder.getOperation();
            String orderType = virtualOrder.getOrderType();
            int lots = virtualOrder.getRequestedLots().intValue();
            String accountId = virtualOrder.getAccountId();
            
            // Получаем текущую цену
            MarketAnalysisService.TrendAnalysis trend = marketAnalysisService.analyzeTrend(
                figi, ru.tinkoff.piapi.contract.v1.CandleInterval.CANDLE_INTERVAL_1_MIN);
            BigDecimal currentPrice = trend.getCurrentPrice();
            
            boolean shouldTrigger = false;
            OrderDirection triggerDirection = null;
            String triggerType = "";
            
            // Логика для Stop-Loss
            if ("VIRTUAL_STOP_LONG".equals(operation)) {
                // Лонг: стоп срабатывает если цена упала ниже уровня
                if (currentPrice.compareTo(triggerPrice) <= 0) {
                    shouldTrigger = true;
                    triggerDirection = OrderDirection.ORDER_DIRECTION_SELL;
                    triggerType = "STOP-LOSS (ЛОНГ)";
                    log.warn("🛑 СРАБАТЫВАНИЕ СТОП-ЛОССА (ЛОНГ): {} упал до {} (стоп: {})", 
                        displayOf(figi), currentPrice, triggerPrice);
                }
            } else if ("VIRTUAL_STOP_SHORT".equals(operation)) {
                // Шорт: стоп срабатывает если цена выросла выше уровня
                if (currentPrice.compareTo(triggerPrice) >= 0) {
                    shouldTrigger = true;
                    triggerDirection = OrderDirection.ORDER_DIRECTION_BUY;
                    triggerType = "STOP-LOSS (ШОРТ)";
                    log.warn("🛑 СРАБАТЫВАНИЕ СТОП-ЛОССА (ШОРТ): {} вырос до {} (стоп: {})", 
                        displayOf(figi), currentPrice, triggerPrice);
                }
            }
            // Логика для Take-Profit
            else if ("VIRTUAL_TP_LONG".equals(operation)) {
                // Лонг: тейк-профит срабатывает если цена выросла выше уровня
                if (currentPrice.compareTo(triggerPrice) >= 0) {
                    shouldTrigger = true;
                    triggerDirection = OrderDirection.ORDER_DIRECTION_SELL;
                    triggerType = "TAKE-PROFIT (ЛОНГ)";
                    log.info("🎯 СРАБАТЫВАНИЕ ТЕЙК-ПРОФИТА (ЛОНГ): {} вырос до {} (TP: {})", 
                        displayOf(figi), currentPrice, triggerPrice);
                }
            } else if ("VIRTUAL_TP_SHORT".equals(operation)) {
                // Шорт: тейк-профит срабатывает если цена упала ниже уровня
                if (currentPrice.compareTo(triggerPrice) <= 0) {
                    shouldTrigger = true;
                    triggerDirection = OrderDirection.ORDER_DIRECTION_BUY;
                    triggerType = "TAKE-PROFIT (ШОРТ)";
                    log.info("🎯 СРАБАТЫВАНИЕ ТЕЙК-ПРОФИТА (ШОРТ): {} упал до {} (TP: {})", 
                        displayOf(figi), currentPrice, triggerPrice);
                }
            }
            
            if (shouldTrigger) {
                executeVirtualOrder(virtualOrder, triggerDirection, currentPrice, triggerType);
            }
            
        } catch (Exception e) {
            log.error("Ошибка проверки виртуального ордера {}: {}", virtualOrder.getOrderId(), e.getMessage());
        }
    }
    
    /**
     * Исполнение виртуального ордера с поддержкой OCO
     */
    private void executeVirtualOrder(Order virtualOrder, OrderDirection direction, BigDecimal currentPrice, String triggerType) {
        try {
            String figi = virtualOrder.getFigi();
            int lots = virtualOrder.getRequestedLots().intValue();
            String accountId = virtualOrder.getAccountId();
            
            log.info("🚨 ИСПОЛНЯЕМ {}: {} {} лотов по цене {}", 
                triggerType, displayOf(figi), lots, currentPrice);
            
            // Размещаем рыночный ордер для быстрого исполнения
            orderService.placeMarketOrder(figi, lots, direction, accountId);
            
            // Обновляем статус исполненного ордера
            virtualOrder.setStatus("EXECUTED");
            virtualOrder.setMessage(virtualOrder.getMessage() + " | Executed at: " + currentPrice + " | Type: " + triggerType);
            orderRepository.save(virtualOrder);
            
            // 🚀 OCO ЛОГИКА: Отменяем парный ордер если это OCO группа
            String message = virtualOrder.getMessage();
            if (message != null && message.contains("OCO_GROUP:")) {
                String ocoGroupId = extractOCOGroupId(message);
                cancelPairedOCOOrder(ocoGroupId, virtualOrder.getOrderId());
            }
            
            // Логируем в систему
            BotLogService.LogLevel logLevel = triggerType.contains("TAKE-PROFIT") ? 
                BotLogService.LogLevel.SUCCESS : BotLogService.LogLevel.WARNING;
            
            botLogService.addLogEntry(logLevel, BotLogService.LogCategory.RISK_MANAGEMENT,
                "🎯 " + triggerType + " исполнен", 
                String.format("%s, Лотов: %d, Цена: %.2f", displayOf(figi), lots, currentPrice));
            
            log.info("✅ {} исполнен успешно: {} → статус EXECUTED", triggerType, virtualOrder.getOrderId());
            
        } catch (Exception e) {
            log.error("❌ Ошибка исполнения {}: {}", triggerType, e.getMessage());
            
            // Помечаем как ошибочный
            virtualOrder.setStatus("ERROR");
            virtualOrder.setMessage(virtualOrder.getMessage() + " | Error: " + e.getMessage());
            orderRepository.save(virtualOrder);
        }
    }
    
    /**
     * Извлечение ID OCO группы из сообщения
     */
    private String extractOCOGroupId(String message) {
        try {
            String[] parts = message.split("OCO_GROUP:");
            if (parts.length > 1) {
                return parts[1].split("\\|")[0].trim();
            }
        } catch (Exception e) {
            log.warn("Не удалось извлечь OCO Group ID из: {}", message);
        }
        return null;
    }
    
    /**
     * Отмена парного ордера в OCO группе
     */
    private void cancelPairedOCOOrder(String ocoGroupId, String executedOrderId) {
        try {
            if (ocoGroupId == null) return;
            
            // Находим все ордера в этой OCO группе
            List<Order> ocoOrders = orderRepository.findByStatusAndMessageContaining("MONITORING", "OCO_GROUP:" + ocoGroupId);
            
            for (Order ocoOrder : ocoOrders) {
                // Отменяем все кроме исполненного
                if (!ocoOrder.getOrderId().equals(executedOrderId)) {
                    ocoOrder.setStatus("CANCELLED_BY_OCO");
                    ocoOrder.setMessage(ocoOrder.getMessage() + " | Cancelled by paired order execution");
                    orderRepository.save(ocoOrder);
                    
                    log.info("🚫 OCO: Отменен парный ордер {} (исполнен {})", 
                        ocoOrder.getOrderId(), executedOrderId);
                }
            }
            
        } catch (Exception e) {
            log.error("Ошибка отмены парных OCO ордеров: {}", e.getMessage());
        }
    }
    
    /**
     * Получение читаемого имени инструмента
     */
    private String displayOf(String figi) {
        try {
            return instrumentNameService.getDisplayName(figi);
        } catch (Exception e) {
            return figi;
        }
    }
}
