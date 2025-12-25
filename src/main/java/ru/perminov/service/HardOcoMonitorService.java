package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.perminov.model.Order;
import ru.perminov.repository.OrderRepository;
import ru.tinkoff.piapi.contract.v1.OrderDirection;
import ru.tinkoff.piapi.contract.v1.OrderState;
import ru.tinkoff.piapi.core.models.Money;
import ru.tinkoff.piapi.core.models.Portfolio;
import ru.tinkoff.piapi.core.models.Position;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private final PortfolioService portfolioService;
    private final AccountService accountService;
    private final TradingSettingsService tradingSettingsService;
    private final InvestApiManager investApiManager;
    private final RiskRuleService riskRuleService;
    private final LotSizeService lotSizeService;
    private final BotLogService botLogService;
    private final InstrumentNameService instrumentNameService;

    /**
     * Восстановление жестких ордеров при старте системы
     * Выполняется один раз при запуске приложения
     */
    @Bean
    public ApplicationRunner restoreHardStopsOnStartup() {
        return args -> {
            try {
                // Небольшая задержка для инициализации всех сервисов
                Thread.sleep(5000);
                
                log.info("🔄 Восстановление жестких стоп-ордеров при старте системы...");
                
                // Проверяем, включена ли функция жестких ордеров
                if (!isHardStopsEnabled()) {
                    log.info("⏹️ Жесткие стоп-ордера отключены, пропускаем восстановление при старте");
                    return;
                }
                
                // Выполняем проверку и установку жестких стоп-ордеров для всех позиций
                checkAndSetupHardStopsForPositions();
                
                log.info("✅ Восстановление жестких стоп-ордеров при старте завершено");
            } catch (Exception e) {
                log.error("❌ Ошибка восстановления жестких стоп-ордеров при старте: {}", e.getMessage(), e);
            }
        };
    }

    /**
     * Мониторинг HARD OCO ордеров каждые 30 секунд
     */
    @Scheduled(fixedRate = 30000)
    public void monitorHardOcoOrders() {
        try {
            // Получаем все активные HARD OCO ордера из БД
            // Фильтруем по типу HARD_OCO_* (это гарантирует, что мы берем только реальные HARD OCO ордера)
            List<Order> hardOcoOrders = orderRepository.findAll().stream()
                    .filter(order -> {
                        // Проверяем тип ордера - должен начинаться с HARD_OCO_
                        if (order.getOrderType() != null && order.getOrderType().startsWith("HARD_OCO_")) {
                            return true;
                        }
                        return false;
                    })
                    .filter(order -> {
                        // Исключаем уже исполненные или отмененные ордера
                        String status = order.getStatus();
                        return status != null && 
                               !status.equals("FILLED") && 
                               !status.equals("EXECUTED") && 
                               !status.equals("CANCELLED") && 
                               !status.equals("CANCELLED_BY_OCO") &&
                               !status.equals("REJECTED");
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
                // Ордер не найден у брокера - возможно уже исполнен или отменен брокером (в конце торгового дня)
                String figi = order.getFigi();
                String status = order.getStatus();
                
                // Проверяем, не был ли ордер отменен брокером, но позиция еще активна
                if (status != null && (status.equals("CANCELLED") || status.equals("NEW") || status.equals("PENDING"))) {
                    // Проверяем, есть ли еще активная позиция
                    try {
                        Portfolio portfolio = portfolioService.getPortfolio(accountId);
                        Position position = portfolio.getPositions().stream()
                                .filter(p -> figi.equals(p.getFigi()))
                                .filter(p -> p.getQuantity() != null && p.getQuantity().compareTo(BigDecimal.ZERO) != 0)
                                .findFirst()
                                .orElse(null);
                        
                        if (position != null) {
                            // Позиция активна, но ордер отменен брокером - нужно восстановить
                            log.warn("🔄 HARD OCO ордер {} отменен брокером, но позиция {} еще активна. Восстанавливаем жесткие ордера...", 
                                    orderId, figi);
                            
                            // Обновляем статус в БД
                            order.setStatus("CANCELLED_BY_BROKER");
                            order.setMessage(order.getMessage() != null ? 
                                    order.getMessage() + " | Cancelled by broker, will restore" : 
                                    "Cancelled by broker, will restore");
                            orderRepository.save(order);
                            
                            // Восстанавливаем жесткие ордера для позиции
                            restoreHardStopsForPosition(position, accountId);
                            return;
                        } else {
                            // Позиция закрыта - ордер больше не нужен
                            log.debug("HARD OCO ордер {} не найден у брокера, позиция закрыта - это нормально", orderId);
                            return;
                        }
                    } catch (Exception e) {
                        log.warn("Ошибка проверки позиции для отмененного ордера {}: {}", orderId, e.getMessage());
                    }
                }
                
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
            } else if ("CANCELLED".equals(normalizedStatus) || "CANCELLED_BY_BROKER".equals(normalizedStatus)) {
                // Ордер отменен брокером - проверяем, нужно ли восстановить
                String figi = order.getFigi();
                try {
                    Portfolio portfolio = portfolioService.getPortfolio(accountId);
                    Position position = portfolio.getPositions().stream()
                            .filter(p -> figi.equals(p.getFigi()))
                            .filter(p -> p.getQuantity() != null && p.getQuantity().compareTo(BigDecimal.ZERO) != 0)
                            .findFirst()
                            .orElse(null);
                    
                    if (position != null) {
                        // Позиция активна, но ордер отменен - восстанавливаем
                        log.warn("🔄 HARD OCO ордер {} отменен брокером (статус: {}), но позиция {} еще активна. Восстанавливаем жесткие ордера...", 
                                orderId, brokerStatus, figi);
                        
                        // Обновляем статус в БД
                        order.setStatus("CANCELLED_BY_BROKER");
                        order.setMessage(order.getMessage() != null ? 
                                order.getMessage() + " | Cancelled by broker, will restore" : 
                                "Cancelled by broker, will restore");
                        orderRepository.save(order);
                        
                        // Восстанавливаем жесткие ордера для позиции
                        restoreHardStopsForPosition(position, accountId);
                        return;
                    }
                } catch (Exception e) {
                    log.warn("Ошибка проверки позиции для отмененного ордера {}: {}", orderId, e.getMessage());
                }
                
                // Обновляем статус в БД
                order.setStatus(normalizedStatus);
                orderRepository.save(order);
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

    /**
     * Проверка и установка жестких стоп-ордеров для существующих позиций без них
     * Выполняется каждые 5 минут, только если включена функция жестких ордеров
     */
    @Scheduled(fixedRate = 300000) // каждые 5 минут
    public void checkAndSetupHardStopsForPositions() {
        log.info("⏰ Запуск проверки жестких стоп-ордеров (каждые 5 минут)");
        botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.RISK_MANAGEMENT,
                "⏰ Запуск проверки жестких стоп-ордеров", 
                "Планируемая проверка каждые 5 минут");
        
        try {
            // Проверяем, включена ли функция жестких ордеров
            boolean enabled = isHardStopsEnabled();
            String mode = investApiManager.getCurrentMode();
            boolean settingEnabled = tradingSettingsService.getBoolean("hard_stops.enabled", false);
            
            log.info("🔧 Статус жестких стоп-ордеров: enabled={}, режим={}, настройка={}", 
                enabled, mode, settingEnabled);
            botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.RISK_MANAGEMENT,
                    "🔧 Статус жестких стоп-ордеров", 
                    String.format("enabled=%s, режим=%s, настройка=%s", enabled, mode, settingEnabled));
            
            if (!enabled) {
                String reason = !"production".equalsIgnoreCase(mode) 
                    ? String.format("режим не production (текущий: %s)", mode)
                    : "настройка hard_stops.enabled = false";
                log.warn("⚠️ Жесткие стоп-ордера отключены: {}. Пропускаем проверку позиций", reason);
                botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT,
                        "⚠️ Проверка жестких стоп-ордеров пропущена", 
                        String.format("Причина: %s", reason));
                return;
            }

            log.info("🔍 Проверка позиций на наличие жестких стоп-ордеров...");
            botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.RISK_MANAGEMENT,
                    "🔍 Проверка позиций на наличие жестких стоп-ордеров", "Начало проверки всех позиций");

            List<String> accountIds = accountService.getAccounts().stream()
                    .map(acc -> acc.getId())
                    .collect(Collectors.toList());

            int totalPositionsChecked = 0;
            int positionsWithStops = 0;
            int stopsInstalled = 0;

            for (String accountId : accountIds) {
                try {
                    var result = checkAndSetupHardStopsForAccount(accountId);
                    totalPositionsChecked += result.checked;
                    positionsWithStops += result.withStops;
                    stopsInstalled += result.installed;
                    Thread.sleep(200); // Небольшая задержка между аккаунтами
                } catch (Exception e) {
                    log.error("Ошибка проверки жестких стоп-ордеров для аккаунта {}: {}", accountId, e.getMessage());
                    botLogService.addLogEntry(BotLogService.LogLevel.ERROR, BotLogService.LogCategory.RISK_MANAGEMENT,
                            "❌ Ошибка проверки жестких стоп-ордеров",
                            String.format("Account: %s, Ошибка: %s", accountId, e.getMessage()));
                }
            }

            // Логируем итоги проверки
            log.info("✅ Проверка жестких стоп-ордеров завершена: проверено позиций={}, со стоп-ордерами={}, установлено новых={}", 
                totalPositionsChecked, positionsWithStops, stopsInstalled);
            botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.RISK_MANAGEMENT,
                    "✅ Проверка позиций завершена",
                    String.format("Проверено: %d, Со стоп-ордерами: %d, Установлено новых: %d",
                            totalPositionsChecked, positionsWithStops, stopsInstalled));

        } catch (Exception e) {
            log.error("❌ Критическая ошибка проверки и установки жестких стоп-ордеров для позиций: {}", e.getMessage(), e);
            botLogService.addLogEntry(BotLogService.LogLevel.ERROR, BotLogService.LogCategory.RISK_MANAGEMENT,
                    "❌ Критическая ошибка проверки жестких стоп-ордеров", e.getMessage());
        }
    }

    /**
     * Результат проверки позиций аккаунта
     */
    private static class CheckResult {
        int checked = 0;
        int withStops = 0;
        int installed = 0;
    }

    /**
     * Проверка и установка жестких стоп-ордеров для позиций конкретного аккаунта
     */
    private CheckResult checkAndSetupHardStopsForAccount(String accountId) {
        CheckResult result = new CheckResult();
        try {
            Portfolio portfolio = portfolioService.getPortfolio(accountId);
            
            for (Position position : portfolio.getPositions()) {
                // Пропускаем валюту и нулевые позиции
                if ("currency".equals(position.getInstrumentType())) continue;
                if (position.getQuantity() == null || position.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                result.checked++;
                String figi = position.getFigi();
                
                // Проверяем наличие активных жестких стоп-ордеров для этой позиции
                if (hasActiveHardOcoOrders(figi, accountId)) {
                    result.withStops++;
                    log.debug("Позиция {} уже имеет активные жесткие стоп-ордера, пропускаем", figi);
                    
                    // Дополнительная проверка: если ордера есть в БД, но отменены брокером - восстанавливаем
                    List<Order> cancelledHardOcoOrders = orderRepository.findByFigiAndAccountIdOrderByOrderDateDesc(figi, accountId)
                            .stream()
                            .filter(order -> {
                                String orderType = order.getOrderType();
                                if (orderType == null) return false;
                                return orderType.equals("HARD_OCO_STOP_LOSS") || orderType.equals("HARD_OCO_TAKE_PROFIT");
                            })
                            .filter(order -> {
                                String status = order.getStatus();
                                return status != null && 
                                       (status.equals("CANCELLED") || 
                                        status.equals("CANCELLED_BY_BROKER"));
                            })
                            .collect(Collectors.toList());
                    
                    if (!cancelledHardOcoOrders.isEmpty()) {
                        log.warn("🔄 Найдены отмененные брокером жесткие ордера для позиции {}. Восстанавливаем...", figi);
                        try {
                            restoreHardStopsForPosition(position, accountId);
                            result.installed++;
                        } catch (Exception e) {
                            log.error("Ошибка восстановления жестких стоп-ордеров для позиции {}: {}", figi, e.getMessage());
                        }
                    }
                    
                    continue;
                }

                // Устанавливаем жесткие стоп-ордера для позиции
                try {
                    setupHardStopsForPosition(position, accountId);
                    result.installed++;
                    Thread.sleep(500); // Задержка между установкой ордеров для разных позиций
                } catch (Exception e) {
                    log.error("Ошибка установки жестких стоп-ордеров для позиции {}: {}", figi, e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("Ошибка проверки жестких стоп-ордеров для аккаунта {}: {}", accountId, e.getMessage());
        }
        return result;
    }

    /**
     * Отмена всех активных ордеров для позиции (жесткие OCO + обычные лимитные)
     * Вызывается при закрытии позиции (SELL, CLOSE_SHORT, закрытие по SL/TP)
     */
    public void cancelAllOrdersForPosition(String figi, String accountId) {
        // Отменяем жесткие OCO ордера
        cancelHardOcoOrdersForPosition(figi, accountId);
        
        // Отменяем обычные лимитные ордера (отложенные ордера на покупку/продажу)
        cancelLimitOrdersForPosition(figi, accountId);
    }
    
    /**
     * Отмена всех активных лимитных ордеров (отложенных ордеров) для позиции
     */
    public void cancelLimitOrdersForPosition(String figi, String accountId) {
        try {
            log.info("🚫 Отмена лимитных ордеров для позиции {} (аккаунт {})", figi, accountId);
            
            // Получаем активные ордера через API для проверки реального статуса
            Set<String> activeOrderIdsFromApi = new HashSet<>();
            try {
                List<ru.tinkoff.piapi.contract.v1.OrderState> apiOrders = orderService.getOrders(accountId);
                for (ru.tinkoff.piapi.contract.v1.OrderState apiOrder : apiOrders) {
                    if (apiOrder.getFigi().equals(figi)) {
                        String status = apiOrder.getExecutionReportStatus().name();
                        // Проверяем, что ордер активен (NEW или PARTIALLY_FILLED)
                        if (status.contains("NEW") || status.contains("PARTIALLY_FILLED")) {
                            activeOrderIdsFromApi.add(apiOrder.getOrderId());
                        }
                    }
                }
                log.debug("Найдено {} активных ордеров через API для {}", activeOrderIdsFromApi.size(), figi);
            } catch (Exception e) {
                log.warn("Не удалось получить активные ордера через API: {}. Используем только БД.", e.getMessage());
            }
            
            // Находим все активные лимитные ордера для этой позиции из БД
            List<Order> activeLimitOrders = orderRepository.findByFigiAndAccountIdOrderByOrderDateDesc(figi, accountId)
                    .stream()
                    .filter(order -> {
                        // Только лимитные ордера, НО НЕ HARD_OCO и НЕ VIRTUAL (они управляются отдельно)
                        String orderType = order.getOrderType();
                        if (orderType == null) return false;
                        return (orderType.equals("LIMIT") || 
                               orderType.equals("ORDER_TYPE_LIMIT") ||
                               orderType.equals("STOP_LOSS")) &&
                               !orderType.startsWith("HARD_OCO_") &&
                               !orderType.startsWith("VIRTUAL_");
                    })
                    .filter(order -> {
                        // Проверяем статус в БД
                        String status = order.getStatus();
                        boolean isActiveInDb = status != null && 
                               !status.equals("FILLED") && 
                               !status.equals("EXECUTED") && 
                               !status.equals("CANCELLED") && 
                               !status.equals("CANCELLED_BY_OCO") &&
                               !status.equals("REJECTED") &&
                               !status.equals("ERROR");
                        
                        // Если есть данные из API, проверяем и там
                        if (!activeOrderIdsFromApi.isEmpty()) {
                            return isActiveInDb && activeOrderIdsFromApi.contains(order.getOrderId());
                        }
                        
                        return isActiveInDb;
                    })
                    .collect(Collectors.toList());
            
            if (activeLimitOrders.isEmpty()) {
                log.debug("Нет активных лимитных ордеров для отмены по позиции {}", figi);
                return;
            }
            
            log.info("Найдено {} активных лимитных ордеров для отмены по позиции {}", activeLimitOrders.size(), figi);
            
            // Отменяем все найденные ордера
            int successfullyCancelled = 0;
            for (Order order : activeLimitOrders) {
                try {
                    // Отменяем ордер у брокера
                    orderService.cancelOrder(accountId, order.getOrderId());
                    log.info("🚫 Отменен лимитный ордер {} у брокера (позиция закрыта)", order.getOrderId());
                    successfullyCancelled++;
                } catch (Exception e) {
                    log.warn("Не удалось отменить лимитный ордер {} у брокера: {}", order.getOrderId(), e.getMessage());
                    // Возможно, ордер уже был отменен или исполнен - обновляем статус в БД
                }
                
                // Обновляем статус в БД
                order.setStatus("CANCELLED");
                String existingMsg = order.getMessage() != null ? order.getMessage() : "";
                String newMessage = existingMsg + " | Cancelled: position closed";
                if (newMessage.length() > 200) {
                    newMessage = newMessage.substring(0, 197) + "...";
                }
                order.setMessage(newMessage);
                orderRepository.save(order);
                log.info("💾 Лимитный ордер {} отмечен как отмененный в БД (позиция закрыта)", order.getOrderId());
            }
            
            log.info("✅ Отмена лимитных ордеров для позиции {} завершена (отменено {}/{})", 
                figi, successfullyCancelled, activeLimitOrders.size());
            
        } catch (Exception e) {
            log.error("Ошибка отмены лимитных ордеров для позиции {}: {}", figi, e.getMessage(), e);
        }
    }
    
    /**
     * Отмена всех активных жестких OCO ордеров для позиции
     * Вызывается при закрытии позиции (SELL, CLOSE_SHORT, закрытие по SL/TP)
     */
    public void cancelHardOcoOrdersForPosition(String figi, String accountId) {
        try {
            log.info("🚫 Отмена жестких OCO ордеров для позиции {} (аккаунт {})", figi, accountId);
            
            // Находим все активные жесткие OCO ордера для этой позиции
            List<Order> activeHardOcoOrders = orderRepository.findByFigiAndAccountIdOrderByOrderDateDesc(figi, accountId)
                    .stream()
                    .filter(order -> {
                        String orderType = order.getOrderType();
                        if (orderType == null) return false;
                        return orderType.equals("HARD_OCO_STOP_LOSS") || orderType.equals("HARD_OCO_TAKE_PROFIT");
                    })
                    .filter(order -> {
                        String status = order.getStatus();
                        return status != null && 
                               !status.equals("FILLED") && 
                               !status.equals("EXECUTED") && 
                               !status.equals("CANCELLED") && 
                               !status.equals("CANCELLED_BY_OCO") &&
                               !status.equals("REJECTED");
                    })
                    .collect(Collectors.toList());
            
            if (activeHardOcoOrders.isEmpty()) {
                log.debug("Нет активных жестких OCO ордеров для отмены по позиции {}", figi);
                return;
            }
            
            log.info("Найдено {} активных жестких OCO ордеров для отмены по позиции {}", activeHardOcoOrders.size(), figi);
            
            // Отменяем все найденные ордера
            for (Order order : activeHardOcoOrders) {
                try {
                    // Отменяем ордер у брокера
                    orderService.cancelOrder(accountId, order.getOrderId());
                    log.info("🚫 Отменен жесткий OCO ордер {} у брокера (позиция закрыта)", order.getOrderId());
                } catch (Exception e) {
                    log.warn("Не удалось отменить жесткий OCO ордер {} у брокера: {}", order.getOrderId(), e.getMessage());
                }
                
                // Обновляем статус в БД
                order.setStatus("CANCELLED");
                order.setMessage(order.getMessage() != null ? order.getMessage() + " | Cancelled: position closed" : "Cancelled: position closed");
                orderRepository.save(order);
                log.info("💾 Жесткий OCO ордер {} отмечен как отмененный в БД (позиция закрыта)", order.getOrderId());
            }
            
            log.info("✅ Отмена жестких OCO ордеров для позиции {} завершена (отменено {})", figi, activeHardOcoOrders.size());
            
        } catch (Exception e) {
            log.error("Ошибка отмены жестких OCO ордеров для позиции {}: {}", figi, e.getMessage(), e);
        }
    }

    /**
     * Проверка наличия активных жестких OCO ордеров для позиции
     */
    private boolean hasActiveHardOcoOrders(String figi, String accountId) {
        List<Order> activeHardOcoOrders = orderRepository.findByFigiAndAccountIdOrderByOrderDateDesc(figi, accountId)
                .stream()
                .filter(order -> {
                    // Проверяем тип ордера - должен быть HARD_OCO_STOP_LOSS или HARD_OCO_TAKE_PROFIT
                    String orderType = order.getOrderType();
                    if (orderType == null) return false;
                    return orderType.equals("HARD_OCO_STOP_LOSS") || orderType.equals("HARD_OCO_TAKE_PROFIT");
                })
                .filter(order -> {
                    // Проверяем статус - должен быть активным
                    String status = order.getStatus();
                    return status != null && 
                           !status.equals("FILLED") && 
                           !status.equals("EXECUTED") && 
                           !status.equals("CANCELLED") && 
                           !status.equals("CANCELLED_BY_OCO") &&
                           !status.equals("REJECTED");
                })
                .collect(Collectors.toList());

        // Проверяем, что есть и SL и TP ордера
        boolean hasStopLoss = activeHardOcoOrders.stream()
                .anyMatch(order -> "HARD_OCO_STOP_LOSS".equals(order.getOrderType()));
        boolean hasTakeProfit = activeHardOcoOrders.stream()
                .anyMatch(order -> "HARD_OCO_TAKE_PROFIT".equals(order.getOrderType()));

        return hasStopLoss && hasTakeProfit;
    }

    /**
     * Восстановление жестких стоп-ордеров для позиции
     * Вызывается когда ордера были отменены брокером, но позиция еще активна
     */
    private void restoreHardStopsForPosition(Position position, String accountId) {
        try {
            String figi = position.getFigi();
            log.info("🔄 Восстановление жестких стоп-ордеров для позиции {} (аккаунт {})", figi, accountId);
            
            // Сначала отменяем старые отмененные ордера в БД (помечаем их как восстановленные)
            List<Order> cancelledOrders = orderRepository.findByFigiAndAccountIdOrderByOrderDateDesc(figi, accountId)
                    .stream()
                    .filter(order -> {
                        String orderType = order.getOrderType();
                        if (orderType == null) return false;
                        return orderType.equals("HARD_OCO_STOP_LOSS") || orderType.equals("HARD_OCO_TAKE_PROFIT");
                    })
                    .filter(order -> {
                        String status = order.getStatus();
                        return status != null && 
                               (status.equals("CANCELLED") || 
                                status.equals("CANCELLED_BY_BROKER") ||
                                status.equals("CANCELLED_BY_OCO"));
                    })
                    .collect(Collectors.toList());
            
            for (Order cancelledOrder : cancelledOrders) {
                cancelledOrder.setStatus("RESTORED");
                cancelledOrder.setMessage(cancelledOrder.getMessage() != null ? 
                        cancelledOrder.getMessage() + " | Replaced by restored order" : 
                        "Replaced by restored order");
                orderRepository.save(cancelledOrder);
            }
            
            // Устанавливаем новые жесткие ордера
            setupHardStopsForPosition(position, accountId);
            
        } catch (Exception e) {
            log.error("Ошибка восстановления жестких стоп-ордеров для позиции {}: {}", position.getFigi(), e.getMessage(), e);
        }
    }

    /**
     * Установка жестких стоп-ордеров для позиции
     */
    private void setupHardStopsForPosition(Position position, String accountId) {
        try {
            String figi = position.getFigi();
            String instrumentType = position.getInstrumentType();

            // Получаем количество лотов (используем абсолютное значение для SHORT позиций)
            int lotSize = lotSizeService.getLotSize(figi, instrumentType);
            BigDecimal quantity = position.getQuantity();
            BigDecimal absQuantity = quantity.abs();
            int lots = absQuantity.divide(new BigDecimal(Math.max(1, lotSize)), 0, RoundingMode.DOWN).intValue();
            
            if (lots <= 0) {
                log.warn("Позиция {} имеет некорректное количество лотов: {}", figi, lots);
                return;
            }

            // Определяем направление позиции и цену входа
            BigDecimal avgPrice = extractAveragePrice(position);
            if (avgPrice.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("Не удалось определить среднюю цену для позиции {}", figi);
                return;
            }

            // Определяем направление позиции (LONG или SHORT)
            // Для LONG позиции (quantity > 0) используем ORDER_DIRECTION_BUY (мы покупали)
            // Для SHORT позиции (quantity < 0) используем ORDER_DIRECTION_SELL (мы продавали)
            OrderDirection positionDirection = quantity.compareTo(BigDecimal.ZERO) > 0 
                    ? OrderDirection.ORDER_DIRECTION_BUY  // LONG позиция
                    : OrderDirection.ORDER_DIRECTION_SELL; // SHORT позиция

            // Получаем проценты SL и TP из правил риска
            double stopLossPct = riskRuleService.findByFigi(figi)
                    .map(rule -> rule.getStopLossPct())
                    .orElse(riskRuleService.getDefaultStopLossPct());
            double takeProfitPct = riskRuleService.findByFigi(figi)
                    .map(rule -> rule.getTakeProfitPct())
                    .orElse(riskRuleService.getDefaultTakeProfitPct());

            // Получаем название инструмента для логирования
            String instrumentName = getInstrumentDisplayName(figi, instrumentType);
            
            log.info("📊 Установка жестких стоп-ордеров для позиции {}: lots={}, avgPrice={}, SL={}%, TP={}%", 
                    figi, lots, avgPrice, stopLossPct * 100, takeProfitPct * 100);

            // Логируем начало установки стоп-ордеров
            // Система попытается установить жесткие ордера, но если цены слишком далеко от текущей рыночной,
            // автоматически использует виртуальные OCO
            botLogService.addLogEntry(BotLogService.LogLevel.TRADE, BotLogService.LogCategory.RISK_MANAGEMENT,
                    "🛡️ Установка стоп-ордеров для позиции",
                    String.format("%s (%s), Лотов: %d, Цена входа: %.2f, SL: %.2f%%, TP: %.2f%%, Тип: %s (жесткие или виртуальные)",
                            instrumentName, figi, lots, avgPrice, stopLossPct * 100, takeProfitPct * 100,
                            positionDirection == OrderDirection.ORDER_DIRECTION_BUY ? "LONG" : "SHORT"));

            // Устанавливаем жесткие OCO ордера
            // Если цены слишком далеко от текущей рыночной, placeHardOCO автоматически использует виртуальные OCO
            // Если жесткие ордера не установились (ошибка API, отклонение брокером), также используется виртуальный OCO
            try {
                orderService.placeHardOCO(figi, lots, positionDirection, accountId, 
                        avgPrice, takeProfitPct, stopLossPct);

                // Проверяем, что ордера действительно установились (жесткие или виртуальные)
                // Проверяем через небольшую задержку, чтобы БД успела обновиться
                Thread.sleep(500);
                
                boolean hasHardOco = hasActiveHardOcoOrders(figi, accountId);
                boolean hasVirtualOco = orderRepository.findByFigiAndAccountIdOrderByOrderDateDesc(figi, accountId)
                        .stream()
                        .anyMatch(order -> {
                            String orderType = order.getOrderType();
                            String status = order.getStatus();
                            return (orderType != null && 
                                   (orderType.equals("VIRTUAL_STOP_LOSS") || orderType.equals("VIRTUAL_TAKE_PROFIT"))) &&
                                   (status != null && status.equals("MONITORING"));
                        });
                
                if (hasHardOco) {
                    // Логируем успешную установку жестких ордеров
                botLogService.addLogEntry(BotLogService.LogLevel.SUCCESS, BotLogService.LogCategory.RISK_MANAGEMENT,
                            "✅ Жесткие стоп-ордера установлены",
                        String.format("%s (%s), Лотов: %d, SL: %.2f%%, TP: %.2f%%",
                                instrumentName, figi, lots, stopLossPct * 100, takeProfitPct * 100));
                    log.info("✅ Жесткие стоп-ордера успешно установлены для позиции {}", figi);
                } else if (hasVirtualOco) {
                    // Логируем установку виртуальных ордеров (подстраховка сработала)
                    botLogService.addLogEntry(BotLogService.LogLevel.SUCCESS, BotLogService.LogCategory.RISK_MANAGEMENT,
                            "✅ Виртуальные стоп-ордера установлены (подстраховка)",
                            String.format("%s (%s), Лотов: %d, SL: %.2f%%, TP: %.2f%% (жесткие не установились)",
                                    instrumentName, figi, lots, stopLossPct * 100, takeProfitPct * 100));
                    log.info("✅ Виртуальные стоп-ордера установлены для позиции {} (подстраховка: жесткие не установились)", figi);
                } else {
                    // Ни жесткие, ни виртуальные не установились - критическая ошибка
                    log.error("❌ КРИТИЧЕСКАЯ ОШИБКА: Ни жесткие, ни виртуальные ордера не установились для позиции {}", figi);
                    botLogService.addLogEntry(BotLogService.LogLevel.ERROR, BotLogService.LogCategory.RISK_MANAGEMENT,
                            "❌ Критическая ошибка установки стоп-ордеров",
                            String.format("%s (%s), Лотов: %d - ни жесткие, ни виртуальные ордера не установились",
                                    instrumentName, figi, lots));
                    
                    // Последняя попытка: устанавливаем виртуальные OCO напрямую
                    try {
                        orderService.placeVirtualOCO(figi, lots, positionDirection, accountId, 
                                avgPrice, takeProfitPct, stopLossPct);
                        log.info("✅ Виртуальные OCO установлены вручную для позиции {} (последняя попытка)", figi);
                    } catch (Exception virtualEx) {
                        log.error("❌ Не удалось установить даже виртуальные OCO для {}: {}", figi, virtualEx.getMessage());
                    }
                }
            } catch (Exception e) {
                // Логируем ошибку установки
                log.error("Ошибка установки стоп-ордеров для позиции {}: {}", figi, e.getMessage());
                
                // Подстраховка: если жесткие ордера не установились, пробуем виртуальные
                try {
                    log.warn("🔄 Подстраховка: устанавливаем виртуальные OCO для позиции {} после ошибки жестких", figi);
                    orderService.placeVirtualOCO(figi, lots, positionDirection, accountId, 
                            avgPrice, takeProfitPct, stopLossPct);
                    
                    botLogService.addLogEntry(BotLogService.LogLevel.SUCCESS, BotLogService.LogCategory.RISK_MANAGEMENT,
                            "✅ Виртуальные стоп-ордера установлены (подстраховка после ошибки)",
                            String.format("%s (%s), Лотов: %d, SL: %.2f%%, TP: %.2f%%",
                                    instrumentName, figi, lots, stopLossPct * 100, takeProfitPct * 100));
                    log.info("✅ Виртуальные OCO установлены для позиции {} (подстраховка после ошибки жестких)", figi);
                } catch (Exception virtualEx) {
                    // Логируем критическую ошибку
                    botLogService.addLogEntry(BotLogService.LogLevel.ERROR, BotLogService.LogCategory.RISK_MANAGEMENT,
                            "❌ Критическая ошибка установки стоп-ордеров",
                            String.format("%s (%s), Ошибка: %s (жесткие и виртуальные не установились)",
                                    instrumentName, figi, e.getMessage()));
                    log.error("❌ Критическая ошибка: не удалось установить ни жесткие, ни виртуальные OCO для {}: {}", 
                            figi, virtualEx.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("Ошибка установки жестких стоп-ордеров для позиции {}: {}", position.getFigi(), e.getMessage(), e);
        }
    }

    /**
     * Извлечение средней цены позиции
     */
    private BigDecimal extractAveragePrice(Position position) {
        BigDecimal avgPrice = BigDecimal.ZERO;
        
        // Пробуем взять среднюю цену из разных полей
        Money avgPriceMoney = position.getAveragePositionPrice();
        if (avgPriceMoney != null) {
            BigDecimal price = moneyToBigDecimal(avgPriceMoney);
            if (price.compareTo(BigDecimal.ZERO) > 0) {
                avgPrice = price;
            }
        }

        if (avgPrice.compareTo(BigDecimal.ZERO) <= 0) {
            Money avgPriceFifo = position.getAveragePositionPriceFifo();
            if (avgPriceFifo != null) {
                BigDecimal price = moneyToBigDecimal(avgPriceFifo);
                if (price.compareTo(BigDecimal.ZERO) > 0) {
                    avgPrice = price;
                }
            }
        }

        return avgPrice;
    }

    /**
     * Конвертация Money в BigDecimal
     */
    private BigDecimal moneyToBigDecimal(Money money) {
        if (money == null) return BigDecimal.ZERO;
        try {
            // Используем getValue() для получения BigDecimal напрямую
            Object value = money.getValue();
            if (value instanceof BigDecimal) {
                return (BigDecimal) value;
            } else if (value instanceof String) {
                return new BigDecimal((String) value);
            } else {
                return new BigDecimal(value.toString());
            }
        } catch (Exception e) {
            log.warn("Ошибка конвертации Money в BigDecimal: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /**
     * Проверка, включена ли функция жестких стоп-ордеров
     */
    private boolean isHardStopsEnabled() {
        try {
            boolean enabled = tradingSettingsService.getBoolean("hard_stops.enabled", false);
            String mode = investApiManager.getCurrentMode();
            if (!"production".equalsIgnoreCase(mode)) {
                log.debug("Жесткие стоп-ордера недоступны: режим не production (текущий: {})", mode);
                botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT,
                        "⏹️ Жесткие стоп-ордера недоступны", 
                        String.format("Режим не production (текущий: %s)", mode));
                return false;
            }
            if (!enabled) {
                log.debug("Жесткие стоп-ордера отключены в настройках (hard_stops.enabled = false)");
                botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT,
                        "⏹️ Жесткие стоп-ордера отключены", 
                        "Настройка hard_stops.enabled = false");
                return false;
            }
            botLogService.addLogEntry(BotLogService.LogLevel.SUCCESS, BotLogService.LogCategory.RISK_MANAGEMENT,
                    "✅ Жесткие стоп-ордера включены", 
                    "enabled=true, mode=production");
            return enabled;
        } catch (Exception e) {
            log.warn("Не удалось прочитать настройку hard_stops.enabled: {}", e.getMessage());
            botLogService.addLogEntry(BotLogService.LogLevel.ERROR, BotLogService.LogCategory.RISK_MANAGEMENT,
                    "❌ Ошибка проверки жестких стоп-ордеров", e.getMessage());
            return false;
        }
    }

    /**
     * Получение отображаемого названия инструмента
     */
    private String getInstrumentDisplayName(String figi, String instrumentType) {
        try {
            // Пробуем получить тикер
            String ticker = instrumentNameService.getTicker(figi, instrumentType);
            if (ticker != null && !ticker.isEmpty()) {
                // Пробуем получить полное название
                String name = instrumentNameService.getInstrumentName(figi, instrumentType);
                if (name != null && !name.isEmpty()) {
                    return name + " (" + ticker + ")";
                }
                return ticker;
            }
            
            // Если тикер не найден, пробуем только название
            String name = instrumentNameService.getInstrumentName(figi, instrumentType);
            if (name != null && !name.isEmpty()) {
                return name;
            }
            
            // Фоллбек на FIGI
            return figi;
        } catch (Exception e) {
            log.debug("Ошибка получения названия инструмента {}: {}", figi, e.getMessage());
            return figi;
        }
    }
}

