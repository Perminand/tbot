package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.core.InvestApi;
import ru.tinkoff.piapi.contract.v1.Share;
import ru.tinkoff.piapi.contract.v1.Bond;
import ru.tinkoff.piapi.contract.v1.Etf;
import ru.tinkoff.piapi.contract.v1.Currency;
import ru.tinkoff.piapi.contract.v1.SecurityTradingStatus;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InstrumentService {
    private final InvestApi investApi;

    public List<Share> getAllShares() throws ExecutionException, InterruptedException {
        return investApi.getInstrumentsService().getAllShares().get();
    }

    public List<Bond> getAllBonds() throws ExecutionException, InterruptedException {
        return investApi.getInstrumentsService().getAllBonds().get();
    }

    public List<Etf> getAllEtfs() throws ExecutionException, InterruptedException {
        return investApi.getInstrumentsService().getAllEtfs().get();
    }

    public List<Currency> getAllCurrencies() throws ExecutionException, InterruptedException {
        return investApi.getInstrumentsService().getAllCurrencies().get();
    }

    public List<Share> getTradableShares() throws ExecutionException, InterruptedException {
        List<Share> allShares = getAllShares();
        log.info("Получено {} акций, фильтруем доступные для торговли", allShares.size());
        
        List<Share> tradableShares = allShares.stream()
                .filter(share -> share.getTradingStatus() == SecurityTradingStatus.SECURITY_TRADING_STATUS_NORMAL_TRADING)
                .collect(Collectors.toList());
        
        log.info("Найдено {} акций, доступных для торговли", tradableShares.size());
        return tradableShares;
    }

    public List<Bond> getTradableBonds() throws ExecutionException, InterruptedException {
        List<Bond> allBonds = getAllBonds();
        log.info("Получено {} облигаций, фильтруем доступные для торговли", allBonds.size());
        
        List<Bond> tradableBonds = allBonds.stream()
                .filter(bond -> bond.getTradingStatus() == SecurityTradingStatus.SECURITY_TRADING_STATUS_NORMAL_TRADING)
                .collect(Collectors.toList());
        
        log.info("Найдено {} облигаций, доступных для торговли", tradableBonds.size());
        return tradableBonds;
    }

    public List<Etf> getTradableEtfs() throws ExecutionException, InterruptedException {
        List<Etf> allEtfs = getAllEtfs();
        log.info("Получено {} ETF, фильтруем доступные для торговли", allEtfs.size());
        
        List<Etf> tradableEtfs = allEtfs.stream()
                .filter(etf -> etf.getTradingStatus() == SecurityTradingStatus.SECURITY_TRADING_STATUS_NORMAL_TRADING)
                .collect(Collectors.toList());
        
        log.info("Найдено {} ETF, доступных для торговли", tradableEtfs.size());
        return tradableEtfs;
    }

    public Share getShareByFigi(String figi) throws ExecutionException, InterruptedException {
        List<Share> shares = getAllShares();
        return shares.stream()
                .filter(share -> share.getFigi().equals(figi))
                .findFirst()
                .orElse(null);
    }

    public Bond getBondByFigi(String figi) throws ExecutionException, InterruptedException {
        List<Bond> bonds = getAllBonds();
        return bonds.stream()
                .filter(bond -> bond.getFigi().equals(figi))
                .findFirst()
                .orElse(null);
    }

    public Etf getEtfByFigi(String figi) throws ExecutionException, InterruptedException {
        List<Etf> etfs = getAllEtfs();
        return etfs.stream()
                .filter(etf -> etf.getFigi().equals(figi))
                .findFirst()
                .orElse(null);
    }
} 