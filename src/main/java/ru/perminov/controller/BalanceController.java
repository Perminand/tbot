package ru.perminov.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.perminov.model.AccountBalanceSnapshot;
import ru.perminov.repository.AccountBalanceSnapshotRepository;
import ru.perminov.service.BalanceSummaryService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/balance")
@RequiredArgsConstructor
public class BalanceController {
    private final AccountBalanceSnapshotRepository repository;
    private final BalanceSummaryService summaryService;

    @GetMapping("/snapshots")
    public ResponseEntity<List<AccountBalanceSnapshot>> getSnapshots(
            @RequestParam String accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        List<AccountBalanceSnapshot> snaps = repository.findByAccountIdAndCapturedAtBetweenOrderByCapturedAtAsc(accountId, from, to);
        return ResponseEntity.ok(snaps);
    }

    @GetMapping("/daily-summary")
    public ResponseEntity<BalanceSummaryService.DailyBalanceSummary> getDailySummary(
            @RequestParam String accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok(summaryService.getDailySummary(accountId, date));
    }

    @GetMapping("/latest")
    public ResponseEntity<List<AccountBalanceSnapshot>> getLatest(
            @RequestParam String accountId
    ) {
        LocalDateTime from = LocalDate.now().minusDays(2).atStartOfDay();
        LocalDateTime to = LocalDateTime.now();
        List<AccountBalanceSnapshot> snaps = repository.findByAccountIdAndCapturedAtBetweenOrderByCapturedAtAsc(accountId, from, to);
        return ResponseEntity.ok(snaps);
    }
}
