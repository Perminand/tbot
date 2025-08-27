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
        // Опираемся на текущий режим InvestApiManager, чтобы исключить рассинхрон
        String currentMode = investApiManager.getCurrentMode();
        log.info("AccountService.getAccounts() - текущий режим: {}", currentMode);
        
        if (currentMode != null && currentMode.equalsIgnoreCase("sandbox")) {
            log.info("Используем SandboxService.getAccounts()");
            List<Account> accounts = investApiManager.getCurrentInvestApi().getSandboxService().getAccounts().join();
            log.info("Получено {} аккаунтов из песочницы", accounts.size());
            return accounts;
        } else {
            log.info("Используем UserService.getAccounts() для режима: {}", currentMode);
            try {
                List<Account> accounts = investApiManager.getCurrentInvestApi().getUserService().getAccounts().join();
                log.info("Получено {} аккаунтов из UserService", accounts.size());
                return accounts;
            } catch (Exception e) {
                log.error("Ошибка при получении аккаунтов через UserService: {}", e.getMessage());
                log.error("Текущий режим: {}, но получена ошибка API", currentMode);
                
                // НЕ переключаемся автоматически на sandbox!
                // Вместо этого логируем ошибку и пробрасываем исключение
                log.error("⚠️ ВНИМАНИЕ: Ошибка API в режиме {}. НЕ переключаемся автоматически!", currentMode);
                log.error("Проверьте валидность токена и права доступа к API");
                
                throw new RuntimeException("Ошибка получения аккаунтов в режиме " + currentMode + ": " + e.getMessage(), e);
            }
        }
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