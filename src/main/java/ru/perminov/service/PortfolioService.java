package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.core.InvestApi;
import ru.tinkoff.piapi.core.models.Portfolio;

@Service
@RequiredArgsConstructor
public class PortfolioService {
    private final InvestApi investApi;

    public Portfolio getPortfolio(String accountId) {
        return investApi.getOperationsService().getPortfolio(accountId).join();
    }
} 