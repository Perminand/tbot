package ru.perminov.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.perminov.service.RiskMetricsService;

@RestController
@RequestMapping("/api/risk")
@RequiredArgsConstructor
public class RiskMetricsController {

    private final RiskMetricsService riskMetricsService;

    @GetMapping("/metrics")
    public ResponseEntity<?> getMetrics(@RequestParam("accountId") String accountId) {
        return ResponseEntity.ok(riskMetricsService.getMetrics(accountId));
    }
}


