package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.perminov.model.AccountBalanceSnapshot;
import ru.perminov.repository.AccountBalanceSnapshotRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BalanceSummaryService {

    private final AccountBalanceSnapshotRepository repository;

    public DailyBalanceSummary getDailySummary(String accountId, LocalDate day) {
        LocalDateTime from = day.atStartOfDay();
        LocalDateTime to = day.atTime(LocalTime.MAX);
        List<AccountBalanceSnapshot> snaps = repository.findByAccountIdAndCapturedAtBetweenOrderByCapturedAtAsc(accountId, from, to);
        if (snaps.isEmpty()) {
            return new DailyBalanceSummary(day, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        AccountBalanceSnapshot open = snaps.get(0);
        AccountBalanceSnapshot close = snaps.get(snaps.size() - 1);

        BigDecimal deltaCash = close.getCashTotal().subtract(open.getCashTotal());
        BigDecimal deltaPortfolio = close.getPortfolioValue().subtract(open.getPortfolioValue());
        BigDecimal deltaTotal = close.getTotalValue().subtract(open.getTotalValue());

        return new DailyBalanceSummary(day, deltaCash, deltaPortfolio, deltaTotal, close.getTotalValue());
    }

    public record DailyBalanceSummary(
            LocalDate date,
            BigDecimal cashChange,
            BigDecimal portfolioChange,
            BigDecimal totalChange,
            BigDecimal closingTotal
    ) {}
}


