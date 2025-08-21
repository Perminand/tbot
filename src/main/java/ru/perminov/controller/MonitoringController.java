package ru.perminov.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.perminov.service.PortfolioManagementService;
import ru.perminov.service.TradingSettingsService;

import java.util.Map;

@RestController
@RequestMapping("/api/monitoring")
@RequiredArgsConstructor
public class MonitoringController {

    private final PortfolioManagementService portfolioManagementService;
    private final TradingSettingsService tradingSettingsService;

    @PostMapping("/start")
    public ResponseEntity<?> start(@RequestParam String accountId) {
        portfolioManagementService.startAutoMonitoring(accountId);
        tradingSettingsService.upsert("auto_monitor.enable", "true", "Auto monitoring enabled");
        tradingSettingsService.upsert("auto_monitor.account_id", accountId, "Auto monitoring account");
        return ResponseEntity.ok(Map.of("started", true, "accountId", accountId));
    }

    @PostMapping("/stop")
    public ResponseEntity<?> stop() {
        portfolioManagementService.stopAutoMonitoring();
        tradingSettingsService.upsert("auto_monitor.enable", "false", "Auto monitoring disabled");
        return ResponseEntity.ok(Map.of("stopped", true));
    }

    @GetMapping("/status")
    public ResponseEntity<?> status() {
        boolean enabled = portfolioManagementService.isAutoMonitoringEnabled();
        String accountId = tradingSettingsService.getString("auto_monitor.account_id", "");
        return ResponseEntity.ok(Map.of("enabled", enabled, "accountId", accountId));
    }
}


