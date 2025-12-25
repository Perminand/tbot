package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.perminov.model.Order;
import ru.perminov.repository.OrderRepository;
import ru.tinkoff.piapi.contract.v1.OrderDirection;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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
    private final TradingCooldownService tradingCooldownService;
    private final MarginService marginService;

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
            
            // 🚨 КРИТИЧЕСКАЯ ПРОВЕРКА: Блокировка по ликвидности
            if (portfolioManagementService.isLiquidityBlocked(figi)) {
                long minutesLeft = portfolioManagementService.getLiquidityBlockRemainingMinutes(figi);
                log.warn("⛔ БЛОКИРОВКА ПО ЛИКВИДНОСТИ: {} заблокирован для {} (осталось ~{} мин). Ордер не размещен.", 
                    triggerType, displayOf(figi), minutesLeft);
                botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT,
                    "Блокировка " + triggerType + " по ликвидности", 
                    String.format("%s заблокирован до %d мин", displayOf(figi), minutesLeft));
                return;
            }
            
            // 🚨 КРИТИЧЕСКАЯ ПРОВЕРКА: Динамические фильтры ликвидности
            if (!portfolioManagementService.passesDynamicLiquidityFilters(figi, accountId)) {
                log.warn("⛔ БЛОКИРОВКА ПО ЛИКВИДНОСТИ: {} не проходит динамические фильтры ликвидности для {}. Ордер не размещен.", 
                    triggerType, displayOf(figi));
                botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT,
                    "Блокировка " + triggerType + " по ликвидности", 
                    String.format("%s не проходит фильтры ликвидности", displayOf(figi)));
                return;
            }
            
            // 🚨 КРИТИЧЕСКАЯ ПРОВЕРКА: Cooldown (защита от переторговли)
            String actionForCooldown = (direction == OrderDirection.ORDER_DIRECTION_BUY) ? "BUY" : "SELL";
            TradingCooldownService.CooldownResult cooldownCheck = tradingCooldownService.canTrade(figi, actionForCooldown, accountId);
            if (cooldownCheck.isBlocked()) {
                log.warn("⛔ БЛОКИРОВКА COOLDOWN: {} для {} заблокирован. Причина: {}", 
                    triggerType, displayOf(figi), cooldownCheck.getReason());
                botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT,
                    "Блокировка " + triggerType + " по cooldown", 
                    String.format("%s: %s", displayOf(figi), cooldownCheck.getReason()));
                return;
            }
            
            // 🚨 КРИТИЧЕСКАЯ ПРОВЕРКА: Маржа для BUY операций (закрытие шорта требует маржи)
            if (direction == OrderDirection.ORDER_DIRECTION_BUY) {
                BigDecimal requiredAmount = currentPrice.multiply(BigDecimal.valueOf(lots));
                if (!checkMarginAvailability(accountId, requiredAmount, figi, triggerType)) {
                    log.warn("⛔ БЛОКИРОВКА ПО МАРЖЕ: {} для {} заблокирован. Недостаточно маржинальных средств для покупки {} лотов по цене {}", 
                        triggerType, displayOf(figi), lots, currentPrice);
                    
                    // 🚨 КРИТИЧЕСКАЯ ЛОГИКА: Попытка освободить маржу через отмену других лимитных ордеров
                    int executedLots = tryExecuteWithMarginRecovery(figi, lots, direction, accountId, currentPrice, triggerType, requiredAmount);
                    
                    if (executedLots <= 0) {
                        log.error("❌ КРИТИЧЕСКАЯ ОШИБКА: Не удалось исполнить {} для {} даже после освобождения маржи", 
                            triggerType, displayOf(figi));
                        botLogService.addLogEntry(BotLogService.LogLevel.ERROR, BotLogService.LogCategory.RISK_MANAGEMENT,
                            "КРИТИЧЕСКАЯ ОШИБКА: " + triggerType + " не исполнен", 
                            String.format("%s: недостаточно маржи даже после освобождения", displayOf(figi)));
                        return;
                    }
                    
                    // Если исполнили частично, обновляем виртуальный ордер
                    if (executedLots < lots) {
                        log.warn("⚠️ Частичное исполнение {}: запрошено {} лотов, исполнено {} лотов", 
                            triggerType, lots, executedLots);
                        virtualOrder.setRequestedLots(BigDecimal.valueOf(executedLots));
                        virtualOrder.setStatus("PARTIALLY_EXECUTED");
                        virtualOrder.setMessage((virtualOrder.getMessage() != null ? virtualOrder.getMessage() : "") + 
                            " | Partially executed: " + executedLots + "/" + lots);
                        orderRepository.save(virtualOrder);
                    } else {
                        // Полностью исполнено
                        virtualOrder.setStatus("EXECUTED");
                        virtualOrder.setMessage((virtualOrder.getMessage() != null ? virtualOrder.getMessage() : "") + 
                            " | Executed at: " + currentPrice + " | Type: " + triggerType);
                        orderRepository.save(virtualOrder);
                    }
                    
                    // OCO логика
                    String message = virtualOrder.getMessage();
                    if (message != null && message.contains("OCO_GROUP:")) {
                        String ocoGroupId = extractOCOGroupId(message);
                        cancelPairedOCOOrder(ocoGroupId, virtualOrder.getOrderId());
                    }
                    
                    botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT,
                        "🎯 " + triggerType + " исполнен (после освобождения маржи)", 
                        String.format("%s, Лотов: %d/%d, Цена: %.2f", displayOf(figi), executedLots, lots, currentPrice));
                    
                    return; // Уже обработано
                }
            }
            
            log.info("🚨 ИСПОЛНЯЕМ {}: {} {} лотов по цене {}", 
                triggerType, displayOf(figi), lots, currentPrice);
            
            // Размещаем умный лимитный ордер для лучшей цены
            orderService.placeSmartLimitOrder(figi, lots, direction, accountId, currentPrice);
            
            // Обновляем статус исполненного ордера
            virtualOrder.setStatus("EXECUTED");
            
            // Безопасное добавление сообщения с ограничением длины
            String existingMsg = virtualOrder.getMessage() != null ? virtualOrder.getMessage() : "";
            String newMessage = existingMsg + " | Executed at: " + currentPrice + " | Type: " + triggerType;
            if (newMessage.length() > 200) {
                newMessage = newMessage.substring(0, 197) + "...";
            }
            virtualOrder.setMessage(newMessage);
            
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
            
            // Извлекаем короткое сообщение об ошибке (без stack trace)
            String shortErrorMsg = extractShortErrorMessage(e);
            
            // Помечаем как ошибочный
            virtualOrder.setStatus("ERROR");
            
            // Обрезаем сообщение до 200 символов для БД
            String existingMsg = virtualOrder.getMessage() != null ? virtualOrder.getMessage() : "";
            String newMessage = existingMsg + " | Error: " + shortErrorMsg;
            if (newMessage.length() > 200) {
                newMessage = newMessage.substring(0, 197) + "...";
            }
            virtualOrder.setMessage(newMessage);
            
            try {
            orderRepository.save(virtualOrder);
            } catch (Exception saveEx) {
                log.error("Не удалось сохранить статус ошибки для ордера {}: {}", virtualOrder.getOrderId(), saveEx.getMessage());
            }
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
                    
                    // Безопасное добавление сообщения с ограничением длины
                    String existingMsg = ocoOrder.getMessage() != null ? ocoOrder.getMessage() : "";
                    String newMessage = existingMsg + " | Cancelled by paired order execution";
                    if (newMessage.length() > 200) {
                        newMessage = newMessage.substring(0, 197) + "...";
                    }
                    ocoOrder.setMessage(newMessage);
                    
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
            if (instrumentNameService == null) return figi;
            
            // Специальная обработка для валют
            if ("RUB000UTSTOM".equals(figi)) {
                return "Рубли РФ (RUB)";
            }
            
            // 🚀 УЛУЧШЕННАЯ ЛОГИКА: Пробуем разные типы инструментов
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
                        return ticker + " [" + getInstrumentTypeDisplayName(type) + "]";
                    }
                } catch (Exception ignore) {
                    // Пробуем следующий тип
                }
            }
            
            // 🎯 СПЕЦИАЛЬНАЯ ОБРАБОТКА неизвестных кодов
            return getHumanReadableName(figi);
            
        } catch (Exception ignore) {}
        return figi;
    }
    
    /**
     * Получение читаемого имени из FIGI для неизвестных инструментов
     */
    private String getHumanReadableName(String figi) {
        // Специальные случаи
        if ("ISSUANCEPRLS".equals(figi)) {
            return "Размещение облигаций (ISSUANCEPRLS)";
        }
        
        // Обработка по шаблонам
        if (figi.startsWith("BBG")) {
            return "Инструмент " + figi.substring(0, Math.min(12, figi.length()));
        }
        
        if (figi.startsWith("TCS")) {
            return "Тинькофф инструмент " + figi.substring(0, Math.min(12, figi.length()));
        }
        
        if (figi.contains("ISSUANCE")) {
            return "Размещение (" + figi + ")";
        }
        
        if (figi.contains("PRLS") || figi.contains("PRL")) {
            return "Облигация " + figi;
        }
        
        // По умолчанию
        return "Инструмент " + figi.substring(0, Math.min(12, figi.length()));
    }
    
    /**
     * Получение отображаемого названия типа инструмента
     */
    private String getInstrumentTypeDisplayName(String instrumentType) {
        switch (instrumentType) {
            case "share":
                return "Акция";
            case "bond":
                return "Облигация";
            case "etf":
                return "ETF";
            case "currency":
                return "Валюта";
            default:
                return "Инструмент";
        }
    }
    
    /**
     * Попытка исполнить стоп-ордер с освобождением маржи через отмену других лимитных ордеров
     * Возвращает количество исполненных лотов (0 если не удалось)
     */
    private int tryExecuteWithMarginRecovery(String figi, int requestedLots, OrderDirection direction, 
                                            String accountId, BigDecimal currentPrice, String triggerType, 
                                            BigDecimal requiredAmount) {
        log.info("🔄 Попытка освободить маржу для {}: требуется {}, запрошено {} лотов", 
            displayOf(figi), requiredAmount, requestedLots);
        
        // Шаг 1: Получаем все активные лимитные ордера для аккаунта
        List<Order> activeLimitOrders = getActiveLimitOrders(accountId, figi);
        
        if (activeLimitOrders.isEmpty()) {
            log.warn("⚠️ Нет активных лимитных ордеров для отмены. Пробуем частичное закрытие.");
            return tryPartialExecution(figi, requestedLots, direction, accountId, currentPrice, triggerType);
        }
        
        log.info("📋 Найдено {} активных лимитных ордеров для отмены", activeLimitOrders.size());
        
        // Шаг 2: Сохраняем информацию об ордерах для восстановления
        List<OrderSnapshot> orderSnapshots = new ArrayList<>();
        for (Order order : activeLimitOrders) {
            orderSnapshots.add(new OrderSnapshot(order));
        }
        
        // Шаг 3: Отменяем все активные лимитные ордера
        List<Order> successfullyCancelled = new ArrayList<>();
        for (Order order : activeLimitOrders) {
            try {
                orderService.cancelOrder(accountId, order.getOrderId());
                order.setStatus("CANCELLED_FOR_MARGIN");
                order.setMessage((order.getMessage() != null ? order.getMessage() : "") + 
                    " | Cancelled to free margin for " + triggerType);
                orderRepository.save(order);
                successfullyCancelled.add(order);
                log.info("✅ Отменен лимитный ордер {} для освобождения маржи", order.getOrderId());
            } catch (Exception e) {
                log.error("❌ Ошибка отмены ордера {}: {}", order.getOrderId(), e.getMessage());
            }
        }
        
        if (successfullyCancelled.isEmpty()) {
            log.warn("⚠️ Не удалось отменить ни одного ордера. Пробуем частичное закрытие.");
            return tryPartialExecution(figi, requestedLots, direction, accountId, currentPrice, triggerType);
        }
        
        // Шаг 4: Небольшая задержка для освобождения маржи
        try {
            Thread.sleep(1500); // 1.5 секунды для обработки отмены на стороне брокера
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Шаг 5: Проверяем маржу снова
        if (checkMarginAvailability(accountId, requiredAmount, figi, triggerType)) {
            log.info("✅ Маржи достаточно после отмены ордеров. Размещаем полный ордер.");
            try {
                orderService.placeSmartLimitOrder(figi, requestedLots, direction, accountId, currentPrice);
                
                // Шаг 6: Восстанавливаем отмененные ордера
                restoreCancelledOrders(orderSnapshots, accountId);
                
                return requestedLots;
            } catch (Exception e) {
                log.error("❌ Ошибка размещения ордера после освобождения маржи: {}", e.getMessage());
                // Восстанавливаем ордера даже при ошибке
                restoreCancelledOrders(orderSnapshots, accountId);
                return tryPartialExecution(figi, requestedLots, direction, accountId, currentPrice, triggerType);
            }
        } else {
            log.warn("⚠️ Маржи все еще недостаточно после отмены ордеров. Пробуем частичное закрытие.");
            int partialLots = tryPartialExecution(figi, requestedLots, direction, accountId, currentPrice, triggerType);
            
            // Восстанавливаем ордера после частичного закрытия
            restoreCancelledOrders(orderSnapshots, accountId);
            
            return partialLots;
        }
    }
    
    /**
     * Попытка частичного закрытия позиции (сколько лотов позволяет маржа)
     */
    private int tryPartialExecution(String figi, int requestedLots, OrderDirection direction, 
                                   String accountId, BigDecimal currentPrice, String triggerType) {
        log.info("🔄 Попытка частичного закрытия {}: запрошено {} лотов", displayOf(figi), requestedLots);
        
        // Получаем доступную маржу
        BigDecimal availableMargin = getAvailableMargin(accountId);
        if (availableMargin.compareTo(BigDecimal.ZERO) <= 0) {
            log.error("❌ Нет доступной маржи для частичного закрытия");
            return 0;
        }
        
        // Вычисляем максимальное количество лотов, которое можно закрыть
        BigDecimal maxLotsDecimal = availableMargin.divide(currentPrice, 0, RoundingMode.DOWN);
        int maxLots = maxLotsDecimal.intValue();
        
        if (maxLots <= 0) {
            log.error("❌ Недостаточно маржи даже для 1 лота");
            return 0;
        }
        
        // Берем минимум из запрошенного и доступного
        int lotsToExecute = Math.min(requestedLots, maxLots);
        
        log.warn("⚠️ Частичное закрытие: запрошено {}, доступно {}, исполняем {}", 
            requestedLots, maxLots, lotsToExecute);
        
        try {
            orderService.placeSmartLimitOrder(figi, lotsToExecute, direction, accountId, currentPrice);
            return lotsToExecute;
        } catch (Exception e) {
            log.error("❌ Ошибка частичного закрытия: {}", e.getMessage());
            return 0;
        }
    }
    
    /**
     * Получение всех активных лимитных ордеров для аккаунта (исключая текущий инструмент)
     * НЕ включает HARD_OCO ордера, так как они управляются HardOcoMonitorService
     */
    private List<Order> getActiveLimitOrders(String accountId, String excludeFigi) {
        return orderRepository.findByAccountId(accountId).stream()
            .filter(order -> {
                // Только лимитные ордера, НО НЕ HARD_OCO (они управляются отдельно)
                String orderType = order.getOrderType();
                if (orderType == null) return false;
                return (orderType.equals("LIMIT") || 
                       orderType.equals("ORDER_TYPE_LIMIT")) &&
                       !orderType.startsWith("HARD_OCO_");
            })
            .filter(order -> {
                // Только активные ордера
                String status = order.getStatus();
                return status != null && 
                       (status.equals("NEW") || 
                        status.equals("PARTIALLY_FILLED") ||
                        status.contains("EXECUTION_REPORT_STATUS_NEW") ||
                        status.contains("EXECUTION_REPORT_STATUS_PARTIALLYFILL"));
            })
            .filter(order -> {
                // Исключаем текущий инструмент
                return order.getFigi() != null && !order.getFigi().equals(excludeFigi);
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Восстановление отмененных ордеров
     */
    private void restoreCancelledOrders(List<OrderSnapshot> snapshots, String accountId) {
        if (snapshots.isEmpty()) {
            return;
        }
        
        log.info("🔄 Восстановление {} отмененных ордеров", snapshots.size());
        
        for (OrderSnapshot snapshot : snapshots) {
            try {
                // Преобразуем operation в OrderDirection
                OrderDirection direction;
                if (snapshot.operation == null) {
                    log.warn("⚠️ Пропуск восстановления ордера {}: operation is null", snapshot.orderId);
                    continue;
                }
                
                try {
                    // Пробуем напрямую
                    direction = OrderDirection.valueOf(snapshot.operation);
                } catch (IllegalArgumentException e) {
                    // Если не получилось, пробуем извлечь из строки
                    if (snapshot.operation.contains("BUY")) {
                        direction = OrderDirection.ORDER_DIRECTION_BUY;
                    } else if (snapshot.operation.contains("SELL")) {
                        direction = OrderDirection.ORDER_DIRECTION_SELL;
                    } else {
                        log.warn("⚠️ Не удалось определить направление для ордера {}: {}", snapshot.orderId, snapshot.operation);
                        continue;
                    }
                }
                
                if (snapshot.price == null || snapshot.price.compareTo(BigDecimal.ZERO) <= 0) {
                    log.warn("⚠️ Пропуск восстановления ордера {}: некорректная цена {}", snapshot.orderId, snapshot.price);
                    continue;
                }
                
                String price = snapshot.price.toPlainString();
                int lots = snapshot.requestedLots != null ? snapshot.requestedLots.intValue() : 0;
                
                if (lots <= 0) {
                    log.warn("⚠️ Пропуск восстановления ордера {}: некорректное количество лотов {}", snapshot.orderId, lots);
                    continue;
                }
                
                orderService.placeLimitOrder(
                    snapshot.figi,
                    lots,
                    direction,
                    accountId,
                    price
                );
                
                log.info("✅ Восстановлен ордер: {} лотов, {}, цена {}", 
                    lots, snapshot.operation, snapshot.price);
                
                // Небольшая задержка между восстановлением ордеров
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } catch (Exception e) {
                log.error("❌ Ошибка восстановления ордера {}: {}", snapshot.orderId, e.getMessage(), e);
            }
        }
    }
    
    /**
     * Получение доступной маржи
     */
    private BigDecimal getAvailableMargin(String accountId) {
        try {
            var marginAttrs = marginService.getAccountMarginAttributes(accountId);
            if (marginAttrs == null) {
                return BigDecimal.ZERO;
            }
            
            BigDecimal liquid = marginService.toBigDecimal(marginAttrs.getLiquidPortfolio());
            BigDecimal minimal = marginService.toBigDecimal(marginAttrs.getMinimalMargin());
            BigDecimal missing = marginService.toBigDecimal(marginAttrs.getAmountOfMissingFunds());
            
            BigDecimal freeMargin = liquid.subtract(minimal).subtract(missing.max(BigDecimal.ZERO));
            if (freeMargin.signum() < 0) {
                freeMargin = BigDecimal.ZERO;
            }
            
            BigDecimal safetyPct = marginService.getSafetyPct();
            return freeMargin.multiply(safetyPct).setScale(2, RoundingMode.DOWN);
        } catch (Exception e) {
            log.error("Ошибка получения доступной маржи: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }
    
    /**
     * Снимок ордера для восстановления
     */
    private static class OrderSnapshot {
        final String orderId;
        final String figi;
        final String operation;
        final BigDecimal requestedLots;
        final BigDecimal price;
        
        OrderSnapshot(Order order) {
            this.orderId = order.getOrderId();
            this.figi = order.getFigi();
            this.operation = order.getOperation();
            this.requestedLots = order.getRequestedLots();
            this.price = order.getPrice();
        }
    }
    
    /**
     * Проверка доступности маржи для BUY операции
     * Возвращает true, если маржи достаточно, false - если недостаточно
     */
    private boolean checkMarginAvailability(String accountId, BigDecimal requiredAmount, String figi, String triggerType) {
        try {
            // Если маржа отключена, проверяем только наличные средства через портфель
            if (!marginService.isMarginEnabled()) {
                // Для простоты, если маржа отключена, считаем что средств достаточно
                // (основная проверка должна быть в PortfolioManagementService)
                return true;
            }
            
            // Получаем маржинальные атрибуты счета
            var marginAttrs = marginService.getAccountMarginAttributes(accountId);
            if (marginAttrs == null) {
                // Если не можем получить атрибуты, пропускаем проверку (но логируем)
                log.warn("⚠️ Не удалось получить маржинальные атрибуты для аккаунта {}. Пропускаем проверку маржи.", accountId);
                return true; // Пропускаем проверку, чтобы не блокировать ордер
            }
            
            // Вычисляем свободную маржу
            BigDecimal liquid = marginService.toBigDecimal(marginAttrs.getLiquidPortfolio());
            BigDecimal minimal = marginService.toBigDecimal(marginAttrs.getMinimalMargin());
            BigDecimal missing = marginService.toBigDecimal(marginAttrs.getAmountOfMissingFunds());
            
            // Свободная маржа = liquid - minimal - missing
            BigDecimal freeMargin = liquid.subtract(minimal).subtract(missing.max(BigDecimal.ZERO));
            if (freeMargin.signum() < 0) {
                freeMargin = BigDecimal.ZERO;
            }
            
            // Применяем коэффициент безопасности
            BigDecimal safetyPct = marginService.getSafetyPct();
            BigDecimal availableMargin = freeMargin.multiply(safetyPct).setScale(2, RoundingMode.DOWN);
            
            log.debug("💰 Проверка маржи для {}: требуется={}, доступно={}, liquid={}, minimal={}, missing={}", 
                displayOf(figi), requiredAmount, availableMargin, liquid, minimal, missing);
            
            // Проверяем, достаточно ли маржи
            if (availableMargin.compareTo(requiredAmount) < 0) {
                log.warn("❌ Недостаточно маржи для {}: требуется {}, доступно {}", 
                    displayOf(figi), requiredAmount, availableMargin);
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("Ошибка проверки маржи для аккаунта {}: {}", accountId, e.getMessage());
            // В случае ошибки пропускаем проверку, чтобы не блокировать ордер
            return true;
        }
    }
    
    /**
     * Извлечение короткого сообщения об ошибке (без stack trace)
     * Специальная обработка для ошибки 30042 (недостаточно маржи)
     */
    private String extractShortErrorMessage(Exception e) {
        String errorMsg = e.getMessage();
        if (errorMsg == null) {
            return "Неизвестная ошибка";
        }
        
        // Специальная обработка для ошибки недостатка маржи
        if (errorMsg.contains("30042") || errorMsg.contains("Недостаточно активов")) {
            return "Недостаточно маржинальных средств (30042)";
        }
        
        // Извлекаем только первую строку сообщения (до первого переноса строки или двоеточия)
        String shortMsg = errorMsg;
        
        // Убираем stack trace - берем только до первого "at "
        int stackTraceStart = shortMsg.indexOf("\n\tat ");
        if (stackTraceStart > 0) {
            shortMsg = shortMsg.substring(0, stackTraceStart);
        }
        
        // Убираем полный путь к классу - оставляем только сообщение до первого ":"
        int colonIndex = shortMsg.indexOf(": ");
        if (colonIndex > 0 && colonIndex < 100) {
            // Если двоеточие есть и оно не слишком далеко, берем часть после него
            shortMsg = shortMsg.substring(colonIndex + 2);
        }
        
        // Обрезаем до 150 символов
        if (shortMsg.length() > 150) {
            shortMsg = shortMsg.substring(0, 147) + "...";
        }
        
        return shortMsg.trim();
    }
}
