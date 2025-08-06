package ru.perminov.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.perminov.service.NotificationService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {
    
    private final NotificationService notificationService;
    
    /**
     * Получение всех уведомлений
     */
    @GetMapping
    public ResponseEntity<?> getAllNotifications() {
        try {
            List<NotificationService.Notification> notifications = notificationService.getAllNotifications();
            Map<String, Object> response = new HashMap<>();
            response.put("notifications", notifications);
            response.put("count", notifications.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting notifications: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Error getting notifications: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
    
    /**
     * Получение уведомлений по типу
     */
    @GetMapping("/type/{type}")
    public ResponseEntity<?> getNotificationsByType(@PathVariable("type") String type) {
        try {
            NotificationService.NotificationType notificationType = 
                NotificationService.NotificationType.valueOf(type.toUpperCase());
            
            List<NotificationService.Notification> notifications = 
                notificationService.getNotificationsByType(notificationType);
            
            Map<String, Object> response = new HashMap<>();
            response.put("notifications", notifications);
            response.put("count", notifications.size());
            response.put("type", type);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Invalid notification type: {}", type);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Invalid notification type: " + type);
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            log.error("Error getting notifications by type: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Error getting notifications: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
    
    /**
     * Получение уведомлений по приоритету
     */
    @GetMapping("/priority/{priority}")
    public ResponseEntity<?> getNotificationsByPriority(@PathVariable("priority") String priority) {
        try {
            NotificationService.NotificationPriority notificationPriority = 
                NotificationService.NotificationPriority.valueOf(priority.toUpperCase());
            
            List<NotificationService.Notification> notifications = 
                notificationService.getNotificationsByPriority(notificationPriority);
            
            Map<String, Object> response = new HashMap<>();
            response.put("notifications", notifications);
            response.put("count", notifications.size());
            response.put("priority", priority);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Invalid notification priority: {}", priority);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Invalid notification priority: " + priority);
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            log.error("Error getting notifications by priority: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Error getting notifications: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
    
    /**
     * Очистка старых уведомлений
     */
    @DeleteMapping("/clear")
    public ResponseEntity<?> clearOldNotifications(@RequestParam(defaultValue = "24") int hoursOld) {
        try {
            notificationService.clearOldNotifications(hoursOld);
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Old notifications cleared");
            response.put("hoursOld", hoursOld);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error clearing old notifications: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Error clearing notifications: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
    
    /**
     * Получение статистики уведомлений
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getNotificationStats() {
        try {
            List<NotificationService.Notification> allNotifications = notificationService.getAllNotifications();
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("total", allNotifications.size());
            
            // Статистика по типам
            Map<String, Long> typeStats = allNotifications.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    n -> n.getType().getDisplayName(),
                    java.util.stream.Collectors.counting()
                ));
            stats.put("byType", typeStats);
            
            // Статистика по приоритетам
            Map<String, Long> priorityStats = allNotifications.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    n -> n.getPriority().getDisplayName(),
                    java.util.stream.Collectors.counting()
                ));
            stats.put("byPriority", priorityStats);
            
            // Непрочитанные уведомления
            long unreadCount = allNotifications.stream()
                .filter(n -> !n.isRead())
                .count();
            stats.put("unread", unreadCount);
            
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error getting notification stats: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Error getting notification stats: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
}
