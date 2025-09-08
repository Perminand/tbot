package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.perminov.model.Order;
import ru.perminov.repository.OrderRepository;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Сервис для проверки минимального времени удержания позиции
 * Предотвращает закрытие позиций раньше минимального времени (например, 5-10 минут)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PositionHoldTimeService {
    
    private final OrderRepository orderRepository;
    private final TradingSettingsService tradingSettingsService;
    
    /**
     * Проверяет, можно ли закрыть позицию по инструменту
     * @param figi FIGI инструмента
     * @param accountId ID аккаунта
     * @return результат проверки
     */
    public HoldTimeResult canClosePosition(String figi, String accountId) {
        try {
            // Настройки минимального времени удержания
            int minHoldTimeMinutes = tradingSettingsService.getInt("position.min_hold_time_minutes", 10);
            
            // Ищем последний ордер на открытие позиции (BUY)
            List<Order> orders = orderRepository.findByFigiAndAccountIdOrderByOrderDateDesc(figi, accountId);
            Optional<Order> lastOpenOrder = orders.stream()
                .filter(order -> "ORDER_DIRECTION_BUY".equals(order.getOperation()))
                .findFirst();
            
            if (lastOpenOrder.isEmpty()) {
                log.debug("Нет ордеров на открытие позиции для {}, разрешаем закрытие", figi);
                return HoldTimeResult.allowed("Нет открывающих ордеров");
            }
            
            Order openOrder = lastOpenOrder.get();
            LocalDateTime openTime = openOrder.getOrderDate();
            LocalDateTime now = LocalDateTime.now();
            
            long minutesSinceOpen = ChronoUnit.MINUTES.between(openTime, now);
            
            if (minutesSinceOpen < minHoldTimeMinutes) {
                String reason = String.format("Минимальное время удержания: позиция открыта %d мин назад, требуется %d мин", 
                    minutesSinceOpen, minHoldTimeMinutes);
                log.warn("🚫 {} для {}: {}", reason, figi, openOrder.getOrderId());
                return HoldTimeResult.blocked(reason);
            }
            
            log.info("✅ Минимальное время удержания соблюдено для {}: {} минут", figi, minutesSinceOpen);
            return HoldTimeResult.allowed(String.format("Позиция удерживается %d минут", minutesSinceOpen));
            
        } catch (Exception e) {
            log.error("Ошибка проверки времени удержания для {}: {}", figi, e.getMessage());
            // В случае ошибки разрешаем закрытие, чтобы не заблокировать торговлю
            return HoldTimeResult.allowed("Ошибка проверки, разрешено по умолчанию");
        }
    }
    
    /**
     * Результат проверки минимального времени удержания
     */
    public static class HoldTimeResult {
        private final boolean allowed;
        private final String reason;
        
        private HoldTimeResult(boolean allowed, String reason) {
            this.allowed = allowed;
            this.reason = reason;
        }
        
        public static HoldTimeResult allowed(String reason) {
            return new HoldTimeResult(true, reason);
        }
        
        public static HoldTimeResult blocked(String reason) {
            return new HoldTimeResult(false, reason);
        }
        
        public boolean isAllowed() { return allowed; }
        public boolean isBlocked() { return !allowed; }
        public String getReason() { return reason; }
    }
}
