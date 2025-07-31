package ru.perminov.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import ru.perminov.service.AccountService;
import ru.perminov.dto.AccountDto;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
@Slf4j
public class AccountController {
    private final AccountService accountService;

    @GetMapping
    public List<AccountDto> getAccounts() {
        try {
            return accountService.getAccounts().stream()
                    .map(AccountDto::from)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error getting accounts", e);
            throw new RuntimeException("Error getting accounts: " + e.getMessage(), e);
        }
    }

    @PostMapping("/sandbox/open")
    public String openSandboxAccount() {
        try {
            return accountService.openSandboxAccount();
        } catch (Exception e) {
            log.error("Error opening sandbox account", e);
            throw new RuntimeException("Error opening sandbox account: " + e.getMessage(), e);
        }
    }

    @PostMapping("/sandbox/close")
    public void closeSandboxAccount(@RequestParam("accountId") String accountId) {
        try {
            accountService.closeSandboxAccount(accountId);
        } catch (Exception e) {
            log.error("Error closing sandbox account", e);
            throw new RuntimeException("Error closing sandbox account: " + e.getMessage(), e);
        }
    }

    @PostMapping("/sandbox/topup")
    public String topUpSandboxAccount(@RequestBody TopUpRequest request) {
        try {
            accountService.topUpSandboxAccount(request.accountId, request.currency, request.units, request.nano);
            return "Successfully topped up account with " + request.units + "." + String.format("%09d", request.nano) + " " + request.currency;
        } catch (Exception e) {
            log.error("Error topping up sandbox account", e);
            throw new RuntimeException("Error topping up sandbox account: " + e.getMessage(), e);
        }
    }
    
    public static class TopUpRequest {
        public String accountId;
        public String currency;
        public long units;
        public int nano;
    }
} 