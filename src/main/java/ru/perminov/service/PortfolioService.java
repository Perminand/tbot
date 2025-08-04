package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.core.InvestApi;
import ru.tinkoff.piapi.core.models.Portfolio;
import ru.tinkoff.piapi.core.models.Position;
import ru.tinkoff.piapi.contract.v1.Share;
import ru.tinkoff.piapi.contract.v1.Bond;
import ru.tinkoff.piapi.contract.v1.Etf;

import java.util.List;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioService {
    private final InvestApi investApi;
    // private final InstrumentService instrumentService;

    public Portfolio getPortfolio(String accountId) {
        return investApi.getOperationsService().getPortfolio(accountId).join();
    }

    public Portfolio getEnrichedPortfolio(String accountId) {
        return getPortfolio(accountId);
    }
} 