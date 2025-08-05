package ru.perminov.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import ru.perminov.service.BotLogService;

@Getter
public class LogEvent extends ApplicationEvent {
    private final BotLogService.BotLogEntry logEntry;
    
    public LogEvent(BotLogService.BotLogEntry logEntry) {
        super(logEntry);
        this.logEntry = logEntry;
    }
} 