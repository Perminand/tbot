package ru.perminov.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ru.perminov.service.TradingModeService;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/trading-mode")
@RequiredArgsConstructor
public class TradingModeController {
    
    private final TradingModeService tradingModeService;
    
    @GetMapping("/status")
    public Map<String, Object> getTradingModeStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("mode", tradingModeService.getCurrentMode());
        status.put("isSandbox", tradingModeService.isSandboxMode());
        status.put("isProduction", tradingModeService.isProductionMode());
        status.put("displayName", tradingModeService.getModeDisplayName());
        status.put("badgeClass", tradingModeService.getModeBadgeClass());
        return status;
    }
} 