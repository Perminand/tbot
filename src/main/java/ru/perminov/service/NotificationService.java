package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    
    private final List<Notification> notifications = new ArrayList<>();
    private static final int MAX_NOTIFICATIONS = 100;
    
    /**
     * Отправка уведомления о торговой операции
     */
    public void sendTradeNotification(String figi, String action, int lots, BigDecimal price, BigDecimal totalValue) {
        String message = String.format("🔔 ТОРГОВАЯ ОПЕРАЦИЯ: %s %d лотов %s по цене %.2f руб. (%.2f руб.)", 
            action, lots, figi, price, totalValue);
        
        addNotification(NotificationType.TRADE, message, NotificationPriority.HIGH);
        log.info("Trade notification: {}", message);
    }
    
    /**
     * Отправка уведомления о риске
     */
    public void sendRiskNotification(String riskType, String description) {
        String message = String.format("⚠️ РИСК: %s - %s", riskType, description);
        
        addNotification(NotificationType.RISK, message, NotificationPriority.HIGH);
        log.warn("Risk notification: {}", message);
    }
    
    /**
     * Отправка уведомления о прибыли/убытке
     */
    public void sendPnLNotification(BigDecimal pnl, BigDecimal portfolioValue) {
        BigDecimal pnlPercentage = pnl.divide(portfolioValue, 4, java.math.RoundingMode.HALF_UP)
            .multiply(java.math.BigDecimal.valueOf(100));
        
        String emoji = pnl.compareTo(java.math.BigDecimal.ZERO) >= 0 ? "📈" : "📉";
        String message = String.format("%s P&L: %.2f руб. (%.2f%%)", emoji, pnl, pnlPercentage);
        
        addNotification(NotificationType.PNL, message, NotificationPriority.MEDIUM);
        log.info("P&L notification: {}", message);
    }
    
    /**
     * Отправка уведомления о состоянии портфеля
     */
    public void sendPortfolioNotification(BigDecimal totalValue, int positionsCount) {
        String message = String.format("💼 ПОРТФЕЛЬ: %.2f руб., %d позиций", totalValue, positionsCount);
        
        addNotification(NotificationType.PORTFOLIO, message, NotificationPriority.LOW);
        log.info("Portfolio notification: {}", message);
    }
    
    /**
     * Отправка уведомления об ошибке
     */
    public void sendErrorNotification(String error, String details) {
        String message = String.format("❌ ОШИБКА: %s - %s", error, details);
        
        addNotification(NotificationType.ERROR, message, NotificationPriority.HIGH);
        log.error("Error notification: {}", message);
    }
    
    /**
     * Добавление уведомления в список
     */
    private void addNotification(NotificationType type, String message, NotificationPriority priority) {
        Notification notification = new Notification();
        notification.setType(type);
        notification.setMessage(message);
        notification.setPriority(priority);
        notification.setTimestamp(LocalDateTime.now());
        
        synchronized (notifications) {
            notifications.add(0, notification); // Добавляем в начало списка
            
            // Ограничиваем количество уведомлений
            if (notifications.size() > MAX_NOTIFICATIONS) {
                notifications.remove(notifications.size() - 1);
            }
        }
    }
    
    /**
     * Получение всех уведомлений
     */
    public List<Notification> getAllNotifications() {
        synchronized (notifications) {
            return new ArrayList<>(notifications);
        }
    }
    
    /**
     * Получение уведомлений по типу
     */
    public List<Notification> getNotificationsByType(NotificationType type) {
        synchronized (notifications) {
            return notifications.stream()
                .filter(n -> n.getType() == type)
                .collect(java.util.stream.Collectors.toList());
        }
    }
    
    /**
     * Получение уведомлений по приоритету
     */
    public List<Notification> getNotificationsByPriority(NotificationPriority priority) {
        synchronized (notifications) {
            return notifications.stream()
                .filter(n -> n.getPriority() == priority)
                .collect(java.util.stream.Collectors.toList());
        }
    }
    
    /**
     * Очистка старых уведомлений
     */
    public void clearOldNotifications(int hoursOld) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(hoursOld);
        
        synchronized (notifications) {
            notifications.removeIf(n -> n.getTimestamp().isBefore(cutoff));
        }
    }
    
    /**
     * Типы уведомлений
     */
    public enum NotificationType {
        TRADE("Торговля"),
        RISK("Риск"),
        PNL("Прибыль/Убыток"),
        PORTFOLIO("Портфель"),
        ERROR("Ошибка"),
        SYSTEM("Система");
        
        private final String displayName;
        
        NotificationType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    /**
     * Приоритеты уведомлений
     */
    public enum NotificationPriority {
        LOW("Низкий"),
        MEDIUM("Средний"),
        HIGH("Высокий"),
        CRITICAL("Критический");
        
        private final String displayName;
        
        NotificationPriority(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    /**
     * Модель уведомления
     */
    public static class Notification {
        private NotificationType type;
        private String message;
        private NotificationPriority priority;
        private LocalDateTime timestamp;
        private boolean read = false;
        
        // Getters and setters
        public NotificationType getType() { return type; }
        public void setType(NotificationType type) { this.type = type; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public NotificationPriority getPriority() { return priority; }
        public void setPriority(NotificationPriority priority) { this.priority = priority; }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        
        public boolean isRead() { return read; }
        public void setRead(boolean read) { this.read = read; }
        
        public String getFormattedTimestamp() {
            return timestamp.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
        }
    }
}
