package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.perminov.model.Order;
import ru.perminov.repository.OrderRepository;
import ru.tinkoff.piapi.contract.v1.OrderDirection;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    private final TradingSettingsService tradingSettingsService;
    private final PortfolioManagementService portfolioManagementService;

    // Анти-ложные срабатывания: счетчики подтверждений
    private final Map<String, Integer> touchCounters = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastTouch = new ConcurrentHashMap<>();
    
    /**
     * Мониторинг виртуальных стопов и OCO ордеров каждые 30 секунд
     */
    @Scheduled(fixedRate = 30000)
    public void monitorVirtualStops() {
        try {
            // Получаем все активные виртуальные ордера
                            List<Order> virtualStops = orderRepository.findByStatus("MONITORING").stream()
                    .filter(order -> "VIRTUAL_STOP_LOSS".equals(order.getOrderType()) || 
                                   "VIRTUAL_TAKE_PROFIT".equals(order.getOrderType()))
                    .collect(java.util.stream.Collectors.toList());
            
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
            if (lots <= 0) return;

            // Arm-delay: не активируем SL/TP первые N секунд
            int armDelaySec =  tradingSettingsService.getInt("virtual.stop.arm.delay.sec", 60);
            try {
                LocalDateTime od = virtualOrder.getOrderDate();
                if (od != null) {
                    if (Duration.between(od, LocalDateTime.now()).getSeconds() < armDelaySec) {
                        log.debug("⏳ Arm-delay для {}: стоп ещё не активен", virtualOrder.getOrderId());
                        return;
                    }
                }
            } catch (Exception ignore) { }
            
            // Получаем текущую цену
            MarketAnalysisService.TrendAnalysis trend = marketAnalysisService.analyzeTrend(
                figi, ru.tinkoff.piapi.contract.v1.CandleInterval.CANDLE_INTERVAL_1_MIN);
            BigDecimal currentPrice = trend.getCurrentPrice();
            if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
                log.debug("⚠️ Цена недоступна для {} — пропуск", displayOf(figi));
                return;
            }

            // Учет спрэда: используем bid/ask оценку от mid
            BigDecimal spreadPct = marketAnalysisService.getSpreadPct(figi);
            if (spreadPct == null) spreadPct = BigDecimal.ZERO;
            BigDecimal half = new BigDecimal("0.5");
            BigDecimal halfSpread = spreadPct.multiply(half);
            BigDecimal bidApprox = currentPrice.multiply(BigDecimal.ONE.subtract(halfSpread));
            BigDecimal askApprox = currentPrice.multiply(BigDecimal.ONE.add(halfSpread));
            
            boolean shouldTrigger = false;
            OrderDirection triggerDirection = null;
            String triggerType = "";
            
            // Логика для Stop-Loss
            if ("VIRTUAL_STOP_LONG".equals(operation)) {
                // Лонг: проверяем bid
                if (bidApprox.compareTo(triggerPrice) <= 0) {
                    shouldTrigger = true;
                    triggerDirection = OrderDirection.ORDER_DIRECTION_SELL;
                    triggerType = "STOP-LOSS (ЛОНГ)";
                    log.warn("🛑 КАНДИДАТ SL (ЛОНГ): {} bid≈{} (mid={}) стоп {}", 
                        displayOf(figi), bidApprox, currentPrice, triggerPrice);
                }
            } else if ("VIRTUAL_STOP_SHORT".equals(operation)) {
                // Шорт: проверяем ask
                if (askApprox.compareTo(triggerPrice) >= 0) {
                    shouldTrigger = true;
                    triggerDirection = OrderDirection.ORDER_DIRECTION_BUY;
                    triggerType = "STOP-LOSS (ШОРТ)";
                    log.warn("🛑 КАНДИДАТ SL (ШОРТ): {} ask≈{} (mid={}) стоп {}", 
                        displayOf(figi), askApprox, currentPrice, triggerPrice);
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
                int need = tradingSettingsService.getInt("virtual.stop.confirmations", 2);
                int touches = touchCounters.merge(virtualOrder.getOrderId(), 1, Integer::sum);
                lastTouch.put(virtualOrder.getOrderId(), LocalDateTime.now());
                if (touches >= need) {
                    touchCounters.remove(virtualOrder.getOrderId());
                    lastTouch.remove(virtualOrder.getOrderId());
                    executeVirtualOrder(virtualOrder, triggerDirection, currentPrice, triggerType);
                } else {
                    log.debug("⏳ Подтверждение SL {}/{} для {}", touches, need, virtualOrder.getOrderId());
                }
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
            
            // 🚫 ПРОВЕРКА БЛОКИРОВКИ ПО ЛИКВИДНОСТИ: блокируем исполнение виртуальных стопов для инструментов с провалом ликвидности
            if (portfolioManagementService != null && portfolioManagementService.isLiquidityBlocked(figi)) {
                long minutesLeft = portfolioManagementService.getLiquidityBlockRemainingMinutes(figi);
                log.warn("⏳ БЛОКИРОВКА ПО ЛИКВИДНОСТИ: {} для {} заблокирован. Осталось ~{} мин", 
                    triggerType, displayOf(figi), minutesLeft);
                botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT,
                    "🚫 " + triggerType + " заблокирован по ликвидности", 
                    String.format("%s, осталось ~%d мин", displayOf(figi), minutesLeft));
                return;
            }
            
            log.info("🚨 ИСПОЛНЯЕМ {}: {} {} лотов по цене {}", 
                triggerType, displayOf(figi), lots, currentPrice);
            
            // Размещаем умный лимитный ордер для лучшей цены
            orderService.placeSmartLimitOrder(figi, lots, direction, accountId, currentPrice);
            
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
            List<Order> ocoOrders = orderRepository.findByStatus("MONITORING").stream()
                .filter(order -> order.getMessage() != null && order.getMessage().contains("OCO_GROUP:" + ocoGroupId))
                .collect(java.util.stream.Collectors.toList());
            
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
            return instrumentNameService.getInstrumentName(figi, "SHARE");
        } catch (Exception e) {
            return figi;
        }
    }
}
