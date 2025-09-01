package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.perminov.model.Instrument;
import ru.perminov.repository.InstrumentRepository;
import ru.tinkoff.piapi.contract.v1.Share;
import ru.tinkoff.piapi.contract.v1.Bond;
import ru.tinkoff.piapi.contract.v1.Etf;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class InstrumentSyncService {

    private final InstrumentService instrumentService;
    private final InstrumentRepository instrumentRepository;

    @Scheduled(fixedRate = 3600_000)
    public void syncInstrumentsHourly() {
        try {
            log.info("Синхронизация инструментов в БД...");
            List<Instrument> toSave = new ArrayList<>();

            for (Share s : instrumentService.getAllShares()) {
                Instrument i = mapShare(s);
                toSave.add(i);
            }
            for (Bond b : instrumentService.getAllBonds()) {
                Instrument i = mapBond(b);
                toSave.add(i);
            }
            for (Etf e : instrumentService.getAllEtfs()) {
                Instrument i = mapEtf(e);
                toSave.add(i);
            }

            instrumentRepository.saveAll(toSave);
            log.info("Сохранено/обновлено инструментов: {}", toSave.size());
        } catch (Exception e) {
            log.warn("Ошибка синхронизации инструментов: {}", e.getMessage());
        }
    }

    private Instrument mapShare(Share s) {
        Instrument i = new Instrument();
        i.setFigi(s.getFigi());
        i.setTicker(s.getTicker());
        i.setIsin(s.getIsin());
        i.setName(s.getName());
        i.setCurrency(s.getCurrency());
        i.setExchange(s.getExchange());
        i.setSector(s.getSector());
        i.setCountryOfRisk(s.getCountryOfRisk());
        i.setCountryOfRiskName(s.getCountryOfRiskName());
        i.setInstrumentType("share");
        i.setInstrumentKind(null);
        i.setShareType(null);
        i.setNominal(null);
        i.setNominalCurrency(null);
        i.setTradingStatus(s.getTradingStatus().name());
        i.setOtcFlag(null);
        i.setBuyAvailableFlag(null);
        i.setSellAvailableFlag(null);
        i.setMinPriceIncrement(null);
        i.setApiTradeAvailableFlag(null);
        i.setUid(null);
        i.setRealExchange(null);
        i.setPositionUid(null);
        i.setForIisFlag(null);
        i.setForQualInvestorFlag(null);
        i.setWeekendFlag(null);
        i.setBlockedTcaFlag(null);
        i.setFirst1minCandleDate(null);
        i.setFirst1dayCandleDate(null);
        i.setRiskLevel(null);
        return i;
    }

    private Instrument mapBond(Bond b) {
        Instrument i = new Instrument();
        i.setFigi(b.getFigi());
        i.setTicker(b.getTicker());
        i.setIsin(b.getIsin());
        i.setName(b.getName());
        i.setCurrency(b.getCurrency());
        i.setExchange(b.getExchange());
        i.setSector(b.getSector());
        i.setCountryOfRisk(b.getCountryOfRisk());
        i.setCountryOfRiskName(b.getCountryOfRiskName());
        i.setInstrumentType("bond");
        i.setInstrumentKind(null);
        i.setNominal(null);
        i.setNominalCurrency(null);
        i.setTradingStatus(b.getTradingStatus().name());
        i.setOtcFlag(null);
        i.setBuyAvailableFlag(null);
        i.setSellAvailableFlag(null);
        i.setMinPriceIncrement(null);
        i.setApiTradeAvailableFlag(null);
        i.setUid(null);
        i.setRealExchange(null);
        i.setPositionUid(null);
        i.setForIisFlag(null);
        i.setForQualInvestorFlag(null);
        i.setWeekendFlag(null);
        i.setBlockedTcaFlag(null);
        i.setFirst1minCandleDate(null);
        i.setFirst1dayCandleDate(null);
        i.setRiskLevel(null);
        return i;
    }

    private Instrument mapEtf(Etf e) {
        Instrument i = new Instrument();
        i.setFigi(e.getFigi());
        i.setTicker(e.getTicker());
        i.setIsin(e.getIsin());
        i.setName(e.getName());
        i.setCurrency(e.getCurrency());
        i.setExchange(e.getExchange());
        i.setSector(e.getSector());
        i.setCountryOfRisk(e.getCountryOfRisk());
        i.setCountryOfRiskName(e.getCountryOfRiskName());
        i.setInstrumentType("etf");
        i.setInstrumentKind(null);
        i.setNominal(null);
        i.setNominalCurrency(null);
        i.setTradingStatus(e.getTradingStatus().name());
        i.setOtcFlag(null);
        i.setBuyAvailableFlag(null);
        i.setSellAvailableFlag(null);
        i.setMinPriceIncrement(null);
        i.setApiTradeAvailableFlag(null);
        i.setUid(null);
        i.setRealExchange(null);
        i.setPositionUid(null);
        i.setForIisFlag(null);
        i.setForQualInvestorFlag(null);
        i.setWeekendFlag(null);
        i.setBlockedTcaFlag(null);
        i.setFirst1minCandleDate(null);
        i.setFirst1dayCandleDate(null);
        i.setRiskLevel(null);
        return i;
    }
}


