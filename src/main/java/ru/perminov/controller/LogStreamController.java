package ru.perminov.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import ru.perminov.event.LogEvent;
import ru.perminov.event.LogStatisticsEvent;
import ru.perminov.service.BotLogService;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
@Slf4j
public class LogStreamController {
    
    private final BotLogService botLogService;
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    
    /**
     * Подписка на поток логов в реальном времени
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLogs() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.add(emitter);
        
        // Отправляем начальные данные
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("Подключение к логам установлено"));
            
            // Отправляем последние записи
            var recentLogs = botLogService.getRecentLogEntries(10);
            emitter.send(SseEmitter.event()
                    .name("initial-logs")
                    .data(recentLogs));
                    
        } catch (IOException e) {
            log.error("Ошибка при отправке начальных данных", e);
        }
        
        // Обработка завершения соединения
        emitter.onCompletion(() -> {
            emitters.remove(emitter);
            log.info("SSE соединение завершено");
        });
        
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            log.info("SSE соединение прервано по таймауту");
        });
        
        emitter.onError((ex) -> {
            emitters.remove(emitter);
            log.error("Ошибка SSE соединения", ex);
        });
        
        log.info("Новое SSE соединение установлено. Всего соединений: {}", emitters.size());
        return emitter;
    }
    
    /**
     * Обработчик события нового лога
     */
    @EventListener
    public void handleLogEvent(LogEvent event) {
        if (emitters.isEmpty()) {
            return;
        }
        
        executorService.submit(() -> {
            emitters.removeIf(emitter -> {
                try {
                    emitter.send(SseEmitter.event()
                            .name("new-log")
                            .data(event.getLogEntry()));
                    return false;
                } catch (IOException e) {
                    log.debug("Ошибка отправки лога клиенту", e);
                    return true;
                }
            });
        });
    }
    
    /**
     * Обработчик события обновления статистики
     */
    @EventListener
    public void handleLogStatisticsEvent(LogStatisticsEvent event) {
        if (emitters.isEmpty()) {
            return;
        }
        
        executorService.submit(() -> {
            emitters.removeIf(emitter -> {
                try {
                    emitter.send(SseEmitter.event()
                            .name("statistics-update")
                            .data(event.getStatistics()));
                    return false;
                } catch (IOException e) {
                    log.debug("Ошибка отправки статистики клиенту", e);
                    return true;
                }
            });
        });
    }
    
    /**
     * Получение количества активных подключений
     */
    @GetMapping("/connections/count")
    public int getActiveConnectionsCount() {
        return emitters.size();
    }
} 