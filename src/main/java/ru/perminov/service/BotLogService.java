package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import ru.perminov.event.LogEvent;
import ru.perminov.event.LogStatisticsEvent;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class BotLogService {
    
    private final ApplicationEventPublisher eventPublisher;
    
    private static final int MAX_LOG_ENTRIES = 1000;
    private final List<BotLogEntry> logEntries = new CopyOnWriteArrayList<>();
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    // Инициализация при создании сервиса
    {
        addLogEntry(LogLevel.INFO, LogCategory.SYSTEM_STATUS, "Сервис логирования инициализирован", "BotLogService запущен");
    }
    
    public enum LogLevel {
        INFO, WARNING, ERROR, SUCCESS, TRADE
    }
    
    public enum LogCategory {
        MARKET_ANALYSIS, PORTFOLIO_MANAGEMENT, PORTFOLIO_ANALYSIS, TRADING_STRATEGY, 
        REBALANCING, TECHNICAL_INDICATORS, AUTOMATIC_TRADING, 
        RISK_MANAGEMENT, SYSTEM_STATUS
    }
    
    /**
     * Добавление записи в лог
     */
    public void addLogEntry(LogLevel level, LogCategory category, String message, String details) {
        BotLogEntry entry = new BotLogEntry(
            LocalDateTime.now(),
            level,
            category,
            message,
            details
        );
        
        logEntries.add(0, entry); // Добавляем в начало списка
        
        // Ограничиваем размер лога
        if (logEntries.size() > MAX_LOG_ENTRIES) {
            logEntries.remove(logEntries.size() - 1);
        }
        
        // Логируем в стандартный лог
        switch (level) {
            case ERROR:
                log.error("[{}] {}: {}", category, message, details);
                break;
            case WARNING:
                log.warn("[{}] {}: {}", category, message, details);
                break;
            case SUCCESS:
                log.info("[{}] ✅ {}: {}", category, message, details);
                break;
            case TRADE:
                log.info("[{}] 💰 {}: {}", category, message, details);
                break;
            default:
                log.info("[{}] {}: {}", category, message, details);
        }
        
        // Отправляем в реальном времени через SSE
        try {
            eventPublisher.publishEvent(new LogEvent(entry));
            
            // Обновляем статистику каждые 10 записей
            if (logEntries.size() % 10 == 0) {
                BotLogService.LogStatistics statistics = getLogStatistics();
                eventPublisher.publishEvent(new LogStatisticsEvent(statistics));
            }
        } catch (Exception e) {
            log.debug("Ошибка отправки лога через SSE", e);
        }
    }
    
    /**
     * Добавление простой записи
     */
    public void addLogEntry(LogLevel level, LogCategory category, String message) {
        addLogEntry(level, category, message, "");
    }
    
    /**
     * Получение всех записей лога
     */
    public List<BotLogEntry> getAllLogEntries() {
        return new ArrayList<>(logEntries);
    }
    
    /**
     * Получение записей лога с фильтрацией
     */
    public List<BotLogEntry> getLogEntries(LogLevel level, LogCategory category, int limit) {
        return logEntries.stream()
            .filter(entry -> level == null || entry.getLevel() == level)
            .filter(entry -> category == null || entry.getCategory() == category)
            .limit(limit)
            .toList();
    }
    
    /**
     * Получение последних записей
     */
    public List<BotLogEntry> getRecentLogEntries(int count) {
        return logEntries.stream()
            .limit(count)
            .toList();
    }
    
    /**
     * Очистка лога
     */
    public void clearLog() {
        logEntries.clear();
        addLogEntry(LogLevel.INFO, LogCategory.SYSTEM_STATUS, "Лог очищен", "Все записи удалены");
    }
    
    /**
     * Получение статистики лога
     */
    public LogStatistics getLogStatistics() {
        long totalEntries = logEntries.size();
        long infoCount = logEntries.stream().filter(e -> e.getLevel() == LogLevel.INFO).count();
        long warningCount = logEntries.stream().filter(e -> e.getLevel() == LogLevel.WARNING).count();
        long errorCount = logEntries.stream().filter(e -> e.getLevel() == LogLevel.ERROR).count();
        long successCount = logEntries.stream().filter(e -> e.getLevel() == LogLevel.SUCCESS).count();
        long tradeCount = logEntries.stream().filter(e -> e.getLevel() == LogLevel.TRADE).count();
        
        return new LogStatistics(totalEntries, infoCount, warningCount, errorCount, successCount, tradeCount);
    }
    
    /**
     * Класс для представления записи лога
     */
    public static class BotLogEntry {
        private final LocalDateTime timestamp;
        private final LogLevel level;
        private final LogCategory category;
        private final String message;
        private final String details;
        
        public BotLogEntry(LocalDateTime timestamp, LogLevel level, LogCategory category, String message, String details) {
            this.timestamp = timestamp;
            this.level = level;
            this.category = category;
            this.message = message;
            this.details = details;
        }
        
        // Getters
        public LocalDateTime getTimestamp() { return timestamp; }
        public LogLevel getLevel() { return level; }
        public LogCategory getCategory() { return category; }
        public String getMessage() { return message; }
        public String getDetails() { return details; }
        
        public String getFormattedTimestamp() {
            return timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        
        public String getLevelIcon() {
            return switch (level) {
                case INFO -> "ℹ️";
                case WARNING -> "⚠️";
                case ERROR -> "❌";
                case SUCCESS -> "✅";
                case TRADE -> "💰";
            };
        }
        
        public String getCategoryIcon() {
            return switch (category) {
                case MARKET_ANALYSIS -> "📊";
                case PORTFOLIO_MANAGEMENT -> "💼";
                case PORTFOLIO_ANALYSIS -> "📋";
                case TRADING_STRATEGY -> "🎯";
                case REBALANCING -> "⚖️";
                case TECHNICAL_INDICATORS -> "📈";
                case AUTOMATIC_TRADING -> "🤖";
                case RISK_MANAGEMENT -> "🛡️";
                case SYSTEM_STATUS -> "⚙️";
            };
        }
    }
    
    /**
     * Класс для статистики лога
     */
    public static class LogStatistics {
        private final long totalEntries;
        private final long infoCount;
        private final long warningCount;
        private final long errorCount;
        private final long successCount;
        private final long tradeCount;
        
        public LogStatistics(long totalEntries, long infoCount, long warningCount, 
                           long errorCount, long successCount, long tradeCount) {
            this.totalEntries = totalEntries;
            this.infoCount = infoCount;
            this.warningCount = warningCount;
            this.errorCount = errorCount;
            this.successCount = successCount;
            this.tradeCount = tradeCount;
        }
        
        // Getters
        public long getTotalEntries() { return totalEntries; }
        public long getInfoCount() { return infoCount; }
        public long getWarningCount() { return warningCount; }
        public long getErrorCount() { return errorCount; }
        public long getSuccessCount() { return successCount; }
        public long getTradeCount() { return tradeCount; }
    }
} 