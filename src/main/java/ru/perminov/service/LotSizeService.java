package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.contract.v1.Share;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class LotSizeService {

    private final InvestApiManager investApiManager;

    // Кэш размеров лота по FIGI
    private final Map<String, Integer> lotCache = new ConcurrentHashMap<>();

    /**
     * Возвращает размер лота для инструмента. Для акций берём из Share.lot, иначе по умолчанию 1.
     */
    public int getLotSize(String figi, String instrumentType) {
        try {
            Integer cached = lotCache.get(figi);
            if (cached != null && cached > 0) return cached;

            int lotSize = 1; // значение по умолчанию
            if ("share".equalsIgnoreCase(instrumentType)) {
                try {
                    Share share = investApiManager.getCurrentInvestApi()
                        .getInstrumentsService()
                        .getShareByFigiSync(figi);
                    if (share != null && share.getLot() > 0) {
                        lotSize = share.getLot();
                    }
                } catch (Exception e) {
                    log.debug("Не удалось получить размер лота для {}: {}", figi, e.getMessage());
                }
            }

            lotCache.put(figi, lotSize);
            log.debug("Размер лота: {} → {}", figi, lotSize);
            return lotSize;
        } catch (Exception e) {
            log.warn("Ошибка LotSizeService.getLotSize для {}: {}", figi, e.getMessage());
            return 1;
        }
    }
}


