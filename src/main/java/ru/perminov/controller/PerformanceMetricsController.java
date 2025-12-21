package ru.perminov.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.perminov.model.PerformanceMetrics;
import ru.perminov.service.PerformanceMetricsService;

import java.util.List;
import java.util.Optional;

/**
 * REST API для метрик производительности
 */
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class PerformanceMetricsController {
    
    private final PerformanceMetricsService performanceMetricsService;
    
    /**
     * Получить последние метрики для аккаунта
     * GET /api/analytics/metrics?accountId=xxx
     */
    @GetMapping("/metrics")
    public ResponseEntity<?> getLatestMetrics(@RequestParam String accountId) {
        Optional<PerformanceMetrics> metrics = performanceMetricsService.getLatestMetrics(accountId);
        
        if (metrics.isPresent()) {
            return ResponseEntity.ok(metrics.get());
        } else {
            return ResponseEntity.ok(java.util.Map.of(
                "message", "Метрики еще не рассчитаны",
                "accountId", accountId
            ));
        }
    }
    
    /**
     * Получить все метрики для аккаунта
     * GET /api/analytics/metrics/all?accountId=xxx
     */
    @GetMapping("/metrics/all")
    public ResponseEntity<List<PerformanceMetrics>> getAllMetrics(@RequestParam String accountId) {
        List<PerformanceMetrics> metrics = performanceMetricsService.getAllMetrics(accountId);
        return ResponseEntity.ok(metrics);
    }
    
    /**
     * Рассчитать и сохранить метрики для аккаунта
     * POST /api/analytics/metrics/calculate?accountId=xxx&periodDays=30
     */
    @PostMapping("/metrics/calculate")
    public ResponseEntity<PerformanceMetrics> calculateMetrics(
            @RequestParam String accountId,
            @RequestParam(required = false) Integer periodDays) {
        
        PerformanceMetrics metrics = performanceMetricsService.calculateAndSaveMetrics(accountId, periodDays);
        return ResponseEntity.ok(metrics);
    }
}

