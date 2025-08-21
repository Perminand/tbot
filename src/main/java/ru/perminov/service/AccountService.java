package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.contract.v1.Account;
import ru.tinkoff.piapi.contract.v1.MoneyValue;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {
    private final InvestApiManager investApiManager;
    private final TradingModeService tradingModeService;

    public List<Account> getAccounts() {
        // В песочнице читаем список аккаунтов через SandboxService, в проде — через UserService
        if (tradingModeService.isSandboxMode()) {
            return investApiManager.getCurrentInvestApi().getSandboxService().getAccounts().join();
        }
        return investApiManager.getCurrentInvestApi().getUserService().getAccounts().join();
    }

    public String openSandboxAccount() {
        if (!tradingModeService.isSandboxMode()) {
            throw new IllegalStateException("Sandbox operations are only allowed in sandbox mode");
        }
        return investApiManager.getCurrentInvestApi().getSandboxService().openAccount().join();
    }

    public void closeSandboxAccount(String accountId) {
        if (!tradingModeService.isSandboxMode()) {
            throw new IllegalStateException("Sandbox operations are only allowed in sandbox mode");
        }
        investApiManager.getCurrentInvestApi().getSandboxService().closeAccount(accountId).join();
    }

    public void topUpSandboxAccount(String accountId, String currency, long units, int nano) {
        if (!tradingModeService.isSandboxMode()) {
            throw new IllegalStateException("Sandbox operations are only allowed in sandbox mode");
        }
        
        MoneyValue money = MoneyValue.newBuilder()
                .setCurrency(currency)
                .setUnits(units)
                .setNano(nano)
                .build();
        
        // Используем правильный метод из piapi SDK
        investApiManager.getCurrentInvestApi().getSandboxService().payIn(accountId, money).join();
    }
} 