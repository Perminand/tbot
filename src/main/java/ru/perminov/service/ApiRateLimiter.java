package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApiRateLimiter {

    // Простая модель: не более X запросов в минуту, распределённых по времени
    private final TradingSettingsService settingsService;
    // private final Semaphore permits = new Semaphore(1, true);
    private final AtomicInteger usedInWindow = new AtomicInteger(0);
    private volatile long windowStartMin = Instant.now().getEpochSecond() / 60;

    public void acquire() {
        throttleWindow();
        int limit = getPerMinuteLimit();
        int backoffMs = getBackoffMs();
        log.debug("ApiRateLimiter.acquire(): лимит={}, текущее использование={}", limit, usedInWindow.get());
        while (true) {
            throttleWindow();
            int current = usedInWindow.get();
            if (current < limit) {
                if (usedInWindow.compareAndSet(current, current + 1)) {
                    // Мягкая пауза для равномерности
                    int delay = getSpreadDelayMs(limit);
                    log.debug("ApiRateLimiter: разрешено, задержка {}ms", delay);
                    sleepQuiet(delay);
                    return;
                }
            } else {
                // Достигнут лимит окна - ждём начала следующей минуты
                log.debug("ApiRateLimiter: достигнут лимит, ожидание {}ms", backoffMs);
                sleepQuiet(backoffMs);
            }
        }
    }

    private void throttleWindow() {
        long nowMin = Instant.now().getEpochSecond() / 60;
        if (nowMin != windowStartMin) {
            synchronized (this) {
                if (nowMin != windowStartMin) {
                    log.debug("ApiRateLimiter: сброс окна {} -> {}", windowStartMin, nowMin);
                    windowStartMin = nowMin;
                    usedInWindow.set(0);
                }
            }
        }
    }

    private int getPerMinuteLimit() {
        // Значение по умолчанию консервативное; можно поднять при необходимости
        int limit = settingsService.getInt("api.limit.per_minute", 50);
        log.debug("ApiRateLimiter: лимит в минуту = {}", limit);
        return limit;
    }

    private int getBackoffMs() {
        int backoff = settingsService.getInt("api.limit.backoff_ms", 500);
        log.debug("ApiRateLimiter: задержка при превышении лимита = {}ms", backoff);
        return backoff;
    }

    private int getSpreadDelayMs(int limit) {
        // Распределяем запросы равномерно на окно (60 000 мс / limit)
        if (limit <= 0) return 1000;
        int delay = Math.max(10, 60000 / limit);
        log.debug("ApiRateLimiter: задержка распределения для лимита {} = {}ms", limit, delay);
        return delay;
    }

    private void sleepQuiet(int ms) {
        try {
            log.debug("ApiRateLimiter: ожидание {}ms", ms);
            TimeUnit.MILLISECONDS.sleep(ms);
        } catch (InterruptedException ignored) {
            log.debug("ApiRateLimiter: прервано ожидание {}ms", ms);
            Thread.currentThread().interrupt();
        }
    }
}


