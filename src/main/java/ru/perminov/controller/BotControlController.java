package ru.perminov.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.perminov.service.BotControlService;

import java.util.Map;

@RestController
@RequestMapping("/api/bot-control")
@RequiredArgsConstructor
public class BotControlController {

    private final BotControlService botControlService;
    private final ru.perminov.service.OrderService orderService;

    @PostMapping("/panic-on")
    public Map<String, Object> panicOn() {
        botControlService.activatePanic();
        return Map.of("panic", true);
    }

    @PostMapping("/panic-off")
    public Map<String, Object> panicOff() {
        botControlService.resetPanic();
        return Map.of("panic", false);
    }

    @PostMapping("/limit")
    public Map<String, Object> setLimit(@RequestParam("maxPerMinute") int maxPerMinute) {
        botControlService.setMaxOrdersPerMinute(maxPerMinute);
        return Map.of("maxPerMinute", botControlService.getMaxOrdersPerMinute());
    }

    @PostMapping("/cancel-all")
    public ResponseEntity<java.util.Map<String, Object>> cancelAll(@RequestParam("accountId") String accountId) {
        java.util.Map<String, Object> result = orderService.cancelAllActiveOrders(accountId);
        result.put("status", "done");
        return ResponseEntity.ok(result);
    }
}


