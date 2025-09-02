package ru.perminov.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class BotControlService {

    private final AtomicBoolean panicStop = new AtomicBoolean(false);

    // Простой лимитер ордеров: X ордеров в текущем минутном окне
    private final AtomicInteger ordersInWindow = new AtomicInteger(0);
    private volatile long windowStartEpochMinute = Instant.now().getEpochSecond() / 60;
    private volatile int maxOrdersPerMinute = 30; // можно вынести в настройки

    public boolean isPanic() {
        boolean panic = panicStop.get();
        if (panic) {
            log.warn("PANIC-STOP активен: торговля заблокирована");
        }
        return panic;
    }

    public void activatePanic() {
        panicStop.set(true);
        log.warn("PANIC-STOP активирован: торговля заблокирована");
    }

    public void resetPanic() {
        panicStop.set(false);
        log.info("PANIC-STOP снят: торговля разрешена");
    }

    public void setMaxOrdersPerMinute(int limit) {
        this.maxOrdersPerMinute = Math.max(1, limit);
    }

    public int getMaxOrdersPerMinute() {
        return maxOrdersPerMinute;
    }

    public boolean tryReserveOrderSlot() {
        rotateWindowIfNeeded();
        int current = ordersInWindow.incrementAndGet();
        boolean allowed = current <= maxOrdersPerMinute;
        if (!allowed) {
            ordersInWindow.decrementAndGet();
            log.warn("Превышен лимит ордеров в минуту: {} > {}", current, maxOrdersPerMinute);
        } else {
            log.debug("Ордер разрешен: {} <= {}", current, maxOrdersPerMinute);
        }
        return allowed;
    }

    private void rotateWindowIfNeeded() {
        long nowMin = Instant.now().getEpochSecond() / 60;
        if (nowMin != windowStartEpochMinute) {
            windowStartEpochMinute = nowMin;
            ordersInWindow.set(0);
        }
    }
}


