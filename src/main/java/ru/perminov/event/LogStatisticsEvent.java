package ru.perminov.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import ru.perminov.service.BotLogService;

@Getter
public class LogStatisticsEvent extends ApplicationEvent {
    private final BotLogService.LogStatistics statistics;
    
    public LogStatisticsEvent(BotLogService.LogStatistics statistics) {
        super(statistics);
        this.statistics = statistics;
    }
} 