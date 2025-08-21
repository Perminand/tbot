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
        while (true) {
            throttleWindow();
            int current = usedInWindow.get();
            if (current < limit) {
                if (usedInWindow.compareAndSet(current, current + 1)) {
                    // Мягкая пауза для равномерности
                    sleepQuiet(getSpreadDelayMs(limit));
                    return;
                }
            } else {
                // Достигнут лимит окна - ждём начала следующей минуты
                sleepQuiet(backoffMs);
            }
        }
    }

    private void throttleWindow() {
        long nowMin = Instant.now().getEpochSecond() / 60;
        if (nowMin != windowStartMin) {
            synchronized (this) {
                if (nowMin != windowStartMin) {
                    windowStartMin = nowMin;
                    usedInWindow.set(0);
                }
            }
        }
    }

    private int getPerMinuteLimit() {
        // Значение по умолчанию консервативное; можно поднять при необходимости
        return settingsService.getInt("api.limit.per_minute", 50);
    }

    private int getBackoffMs() {
        return settingsService.getInt("api.limit.backoff_ms", 500);
    }

    private int getSpreadDelayMs(int limit) {
        // Распределяем запросы равномерно на окно (60 000 мс / limit)
        if (limit <= 0) return 1000;
        return Math.max(10, 60000 / limit);
    }

    private void sleepQuiet(int ms) {
        try {
            TimeUnit.MILLISECONDS.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}


