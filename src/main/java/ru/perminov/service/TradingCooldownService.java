package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.perminov.model.Order;
import ru.perminov.repository.OrderRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 🚀 СЕРВИС ЗАЩИТЫ ОТ OVERTRADING: Предотвращает частые покупки-продажи одного инструмента
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TradingCooldownService {
    
    private final OrderRepository orderRepository;
    private final BotLogService botLogService;
    private final TradingSettingsService tradingSettingsService;
    
    // Кэш последних сделок по инструментам
    private final Map<String, LocalDateTime> lastTradeTime = new ConcurrentHashMap<>();
    
    // Настройки cooldown (в минутах) — читаем из конфига с дефолтами
    private int getMinCooldown() { return tradingSettingsService.getInt("cooldown.min.minutes", 15); }
    private int getSameDirectionCooldown() { return tradingSettingsService.getInt("cooldown.same.minutes", 30); }
    private int getReverseCooldown() { return tradingSettingsService.getInt("cooldown.reverse.minutes", 45); }
    
    /**
     * 🎯 ОСНОВНОЙ МЕТОД: Проверка можно ли торговать данным инструментом
     */
    public CooldownResult canTrade(String figi, String action, String accountId) {
        try {
            // Получаем последние ордера по этому инструменту
            List<Order> recentOrders = getRecentOrders(figi, accountId);
            
            if (recentOrders.isEmpty()) {
                log.debug("✅ Первая сделка с {}: разрешено", figi);
                return CooldownResult.allowed("Первая сделка с инструментом");
            }
            
            Order lastOrder = recentOrders.get(0);
            LocalDateTime lastTradeTime = lastOrder.getOrderDate();
            LocalDateTime now = LocalDateTime.now();
            
            long minutesSinceLastTrade = java.time.Duration.between(lastTradeTime, now).toMinutes();
            
            // Определяем необходимый cooldown
            int requiredCooldown = calculateRequiredCooldown(action, lastOrder);
            
            if (minutesSinceLastTrade < requiredCooldown) {
                String reason = String.format(
                    "Cooldown активен: последняя сделка %d мин назад, требуется %d мин (тип: %s → %s)",
                    minutesSinceLastTrade, requiredCooldown, 
                    getActionType(lastOrder.getOperation()), action
                );
                
                log.warn("🚫 БЛОКИРОВКА OVERTRADING: {} для {}", reason, figi);
                
                botLogService.addLogEntry(
                    BotLogService.LogLevel.WARNING,
                    BotLogService.LogCategory.RISK_MANAGEMENT,
                    "Блокировка частых сделок",
                    String.format("Инструмент: %s, Account: %s, %s", figi, accountId, reason)
                );
                
                return CooldownResult.blocked(reason);
            }
            
            log.info("✅ Cooldown прошел: {} для {}, последняя сделка {} мин назад", 
                action, figi, minutesSinceLastTrade);
            
            return CooldownResult.allowed(String.format("Прошло %d минут с последней сделки", minutesSinceLastTrade));
            
        } catch (Exception e) {
            log.error("Ошибка проверки cooldown для {}: {}", figi, e.getMessage());
            // В случае ошибки разрешаем торговлю
            return CooldownResult.allowed("Ошибка проверки, разрешено по умолчанию");
        }
    }
    
    /**
     * Получение последних ордеров по инструменту
     */
    private List<Order> getRecentOrders(String figi, String accountId) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(2); // Последние 2 часа
        
        return orderRepository.findByFigi(figi).stream()
            .filter(order -> order.getAccountId().equals(accountId))
            .filter(order -> {
                LocalDateTime orderDate = order.getOrderDate();
                return orderDate != null && orderDate.isAfter(cutoff);
            })
            // Учитываем несколько вариантов статусов из API
            .filter(order -> {
                String s = order.getStatus() != null ? order.getStatus().toUpperCase() : "";
                return s.contains("FILL") ||
                       s.equals("FILLED") ||
                       s.equals("EXECUTED") ||
                       s.equals("PARTIALLYFILL") ||
                       s.equals("PARTIAL_FILL");
            })
            .sorted((o1, o2) -> o2.getOrderDate().compareTo(o1.getOrderDate())) // Сортируем по убыванию
            .limit(5) // Берем последние 5 сделок
            .toList();
    }
    
    /**
     * Расчет необходимого cooldown в зависимости от типа сделки
     */
    private int calculateRequiredCooldown(String currentAction, Order lastOrder) {
                    String lastAction = getActionType(lastOrder.getOperation());
        
        // Если пытаемся сделать ту же операцию - увеличенный cooldown
        if (currentAction.equals(lastAction)) {
            return getSameDirectionCooldown();
        }
        
        // Если меняем направление (BUY→SELL или SELL→BUY) - максимальный cooldown
        if (isReverseAction(currentAction, lastAction)) {
            return getReverseCooldown();
        }
        
        // По умолчанию минимальный cooldown
        return getMinCooldown();
    }
    
    /**
     * Определение типа действия по направлению ордера
     */
    private String getActionType(String direction) {
        if (direction == null) return "UNKNOWN";
        
        switch (direction.toUpperCase()) {
            case "ORDER_DIRECTION_BUY":
            case "BUY":
                return "BUY";
            case "ORDER_DIRECTION_SELL":
            case "SELL":
                return "SELL";
            default:
                return "UNKNOWN";
        }
    }
    
    /**
     * Проверка являются ли действия противоположными
     */
    private boolean isReverseAction(String current, String last) {
        return ("BUY".equals(current) && "SELL".equals(last)) ||
               ("SELL".equals(current) && "BUY".equals(last));
    }
    
    /**
     * Результат проверки cooldown
     */
    public static class CooldownResult {
        private final boolean allowed;
        private final String reason;
        
        private CooldownResult(boolean allowed, String reason) {
            this.allowed = allowed;
            this.reason = reason;
        }
        
        public static CooldownResult allowed(String reason) {
            return new CooldownResult(true, reason);
        }
        
        public static CooldownResult blocked(String reason) {
            return new CooldownResult(false, reason);
        }
        
        public boolean isAllowed() { return allowed; }
        public boolean isBlocked() { return !allowed; }
        public String getReason() { return reason; }
    }
}
