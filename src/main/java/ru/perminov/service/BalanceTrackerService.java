package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.perminov.model.AccountBalanceSnapshot;
import ru.perminov.repository.AccountBalanceSnapshotRepository;
import ru.tinkoff.piapi.core.models.Portfolio;
import ru.tinkoff.piapi.core.models.Money;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BalanceTrackerService {

    private final AccountService accountService;
    private final PortfolioService portfolioService;
    private final AccountBalanceSnapshotRepository snapshotRepository;

    // Снимок каждые 15 минут
    @Scheduled(fixedRate = 900_000)
    public void captureBalances() {
        try {
            List<ru.tinkoff.piapi.contract.v1.Account> accounts = accountService.getAccounts();
            for (ru.tinkoff.piapi.contract.v1.Account acc : accounts) {
                String accountId = acc.getId();
                Portfolio p = portfolioService.getPortfolio(accountId);

                BigDecimal cash = moneyToDecimal(p.getTotalAmountCurrencies());
                BigDecimal portfolioValue = moneyToDecimal(p.getTotalAmountShares())
                        .add(moneyToDecimal(p.getTotalAmountBonds()))
                        .add(moneyToDecimal(p.getTotalAmountEtfs()))
                        .add(moneyToDecimal(p.getTotalAmountFutures()))
                        .add(moneyToDecimal(p.getTotalAmountOptions()));
                BigDecimal total = cash.add(portfolioValue);

                AccountBalanceSnapshot s = new AccountBalanceSnapshot();
                s.setAccountId(accountId);
                s.setCapturedAt(LocalDateTime.now());
                s.setCashTotal(cash);
                s.setPortfolioValue(portfolioValue);
                s.setTotalValue(total);
                s.setCurrency(p.getTotalAmountPortfolio() != null ? safeCurrencyFromMoney(p.getTotalAmountPortfolio()) : null);

                snapshotRepository.save(s);
                log.info("Баланс зафиксирован: acc={}, total={}, cash={}, portfolio={}", accountId, total, cash, portfolioValue);
            }
        } catch (Exception e) {
            log.warn("Ошибка фиксации баланса: {}", e.getMessage());
        }
    }

    private BigDecimal moneyToDecimal(Object obj) {
        try {
            if (obj == null) return BigDecimal.ZERO;
            if (obj instanceof Money) return ((Money) obj).getValue();
            String s = obj.toString();
            if (s.contains("value=")) {
                String value = s.substring(s.indexOf("value=") + 6);
                value = value.substring(0, value.indexOf(','));
                return new BigDecimal(value);
            }
            return BigDecimal.ZERO;
        } catch (Exception ignore) {
            return BigDecimal.ZERO;
        }
    }

    private String safeCurrencyFromMoney(Object moneyLike) {
        try {
            String s = moneyLike != null ? moneyLike.toString() : "";
            if (s.contains("currency=")) {
                String c = s.substring(s.indexOf("currency=") + 9);
                int end = c.indexOf(',');
                if (end > 0) c = c.substring(0, end);
                return c;
            }
        } catch (Exception ignore) {}
        return null;
    }
}


