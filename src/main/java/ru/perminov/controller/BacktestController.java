package ru.perminov.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.perminov.service.BacktestService;
import ru.tinkoff.piapi.contract.v1.CandleInterval;

import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/backtest")
@RequiredArgsConstructor
@Slf4j
public class BacktestController {

    private final BacktestService backtestService;

    @GetMapping
    public ResponseEntity<?> run(
            @RequestParam String figi,
            @RequestParam(defaultValue = "CANDLE_INTERVAL_DAY") CandleInterval interval,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "100000") BigDecimal initialCash,
            @RequestParam(defaultValue = "0.0003") BigDecimal commissionPct,
            @RequestParam(defaultValue = "0") int slippageTicks
    ) {
        try {
            var result = backtestService.run(figi, interval, from, to, initialCash, commissionPct, slippageTicks);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Backtest error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Backtest error: " + e.getMessage());
        }
    }
}


