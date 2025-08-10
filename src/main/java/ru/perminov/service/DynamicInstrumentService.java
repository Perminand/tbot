package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.perminov.dto.ShareDto;
import ru.tinkoff.piapi.contract.v1.Share;
import ru.tinkoff.piapi.contract.v1.Bond;
import ru.tinkoff.piapi.contract.v1.Etf;
import ru.tinkoff.piapi.contract.v1.SecurityTradingStatus;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DynamicInstrumentService {
    
    private final InstrumentService instrumentService;
    
    // Кэш доступных инструментов
    private final Map<String, ShareDto> availableInstrumentsCache = new ConcurrentHashMap<>();
    private final Map<String, Long> lastUpdateTime = new ConcurrentHashMap<>();
    
    // Время жизни кэша (5 минут)
    private static final long CACHE_TTL = 5 * 60 * 1000;
    
    // Максимальное количество инструментов для анализа
    private static final int MAX_SHARES = 30;
    private static final int MAX_BONDS = 15;
    private static final int MAX_ETFS = 10;
    
    /**
     * Получение динамического списка доступных инструментов
     */
    public List<ShareDto> getAvailableInstruments() {
        try {
            // Проверяем, нужно ли обновить кэш
            if (shouldUpdateCache()) {
                updateAvailableInstrumentsCache();
            }
            
            List<ShareDto> instruments = new ArrayList<>(availableInstrumentsCache.values());
            log.info("Получено {} доступных инструментов из кэша", instruments.size());
            return instruments;
            
        } catch (Exception e) {
            log.error("Ошибка при получении доступных инструментов: {}", e.getMessage());
            return getFallbackInstruments();
        }
    }
    
    /**
     * Проверка доступности конкретного инструмента
     */
    public boolean isInstrumentAvailable(String figi) {
        try {
            // Проверяем кэш
            if (availableInstrumentsCache.containsKey(figi)) {
                return true;
            }
            
            // Если нет в кэше, проверяем API
            return checkInstrumentAvailability(figi);
            
        } catch (Exception e) {
            log.warn("Ошибка проверки доступности инструмента {}: {}", figi, e.getMessage());
            return false;
        }
    }
    
    /**
     * Получение случайного доступного инструмента
     */
    public Optional<ShareDto> getRandomAvailableInstrument() {
        List<ShareDto> instruments = getAvailableInstruments();
        if (instruments.isEmpty()) {
            return Optional.empty();
        }
        
        Random random = new Random();
        ShareDto randomInstrument = instruments.get(random.nextInt(instruments.size()));
        return Optional.of(randomInstrument);
    }
    
    /**
     * Получение инструментов по типу
     */
    public List<ShareDto> getInstrumentsByType(String type) {
        return getAvailableInstruments().stream()
            .filter(instrument -> type.equalsIgnoreCase(instrument.getInstrumentType()))
            .collect(Collectors.toList());
    }
    
    /**
     * Проверка необходимости обновления кэша
     */
    private boolean shouldUpdateCache() {
        long currentTime = System.currentTimeMillis();
        long lastUpdate = lastUpdateTime.getOrDefault("cache", 0L);
        return (currentTime - lastUpdate) > CACHE_TTL;
    }
    
    /**
     * Обновление кэша доступных инструментов
     */
    private void updateAvailableInstrumentsCache() {
        log.info("Обновление кэша доступных инструментов...");
        
        try {
            Map<String, ShareDto> newCache = new ConcurrentHashMap<>();
            
            // Получаем акции
            List<Share> shares = instrumentService.getTradableShares();
            log.info("Получено {} акций, фильтруем доступные", shares.size());
            
            int addedShares = 0;
            for (Share share : shares) {
                if (addedShares >= MAX_SHARES) break;
                
                if (isInstrumentTradable(share)) {
                    ShareDto shareDto = convertToShareDto(share);
                    newCache.put(share.getFigi(), shareDto);
                    addedShares++;
                }
            }
            
            // Получаем облигации
            List<Bond> bonds = instrumentService.getTradableBonds();
            log.info("Получено {} облигаций, фильтруем доступные", bonds.size());
            
            int addedBonds = 0;
            for (Bond bond : bonds) {
                if (addedBonds >= MAX_BONDS) break;
                
                if (isInstrumentTradable(bond)) {
                    ShareDto bondDto = convertToShareDto(bond);
                    newCache.put(bond.getFigi(), bondDto);
                    addedBonds++;
                }
            }
            
            // Получаем ETF
            List<Etf> etfs = instrumentService.getTradableEtfs();
            log.info("Получено {} ETF, фильтруем доступные", etfs.size());
            
            int addedEtfs = 0;
            for (Etf etf : etfs) {
                if (addedEtfs >= MAX_ETFS) break;
                
                if (isInstrumentTradable(etf)) {
                    ShareDto etfDto = convertToShareDto(etf);
                    newCache.put(etf.getFigi(), etfDto);
                    addedEtfs++;
                }
            }
            
            // Обновляем кэш
            availableInstrumentsCache.clear();
            availableInstrumentsCache.putAll(newCache);
            lastUpdateTime.put("cache", System.currentTimeMillis());
            
            log.info("Кэш обновлен. Доступно инструментов: {}", availableInstrumentsCache.size());
            
        } catch (Exception e) {
            log.error("Ошибка при обновлении кэша инструментов: {}", e.getMessage());
            // В случае ошибки используем резервный список
            Map<String, ShareDto> fallbackCache = new ConcurrentHashMap<>();
            for (ShareDto instrument : getFallbackInstruments()) {
                fallbackCache.put(instrument.getFigi(), instrument);
            }
            availableInstrumentsCache.clear();
            availableInstrumentsCache.putAll(fallbackCache);
        }
    }
    
    /**
     * Проверка доступности инструмента через API
     */
    private boolean checkInstrumentAvailability(String figi) {
        try {
            // Пытаемся получить информацию об инструменте
            Share share = instrumentService.getShareByFigi(figi);
            Bond bond = instrumentService.getBondByFigi(figi);
            Etf etf = instrumentService.getEtfByFigi(figi);
            
            if (share != null) {
                return isInstrumentTradable(share);
            } else if (bond != null) {
                return isInstrumentTradable(bond);
            } else if (etf != null) {
                return isInstrumentTradable(etf);
            }
            
            return false;
            
        } catch (Exception e) {
            log.warn("Ошибка проверки доступности {}: {}", figi, e.getMessage());
            return false;
        }
    }
    
    /**
     * Проверка доступности акции для торговли
     */
    private boolean isInstrumentTradable(Share share) {
        return share.getTradingStatus() == SecurityTradingStatus.SECURITY_TRADING_STATUS_NORMAL_TRADING &&
               share.getBuyAvailableFlag() &&
               share.getSellAvailableFlag() &&
               share.getApiTradeAvailableFlag();
    }
    
    /**
     * Проверка доступности облигации для торговли
     */
    private boolean isInstrumentTradable(Bond bond) {
        return bond.getTradingStatus() == SecurityTradingStatus.SECURITY_TRADING_STATUS_NORMAL_TRADING &&
               bond.getBuyAvailableFlag() &&
               bond.getSellAvailableFlag() &&
               bond.getApiTradeAvailableFlag();
    }
    
    /**
     * Проверка доступности ETF для торговли
     */
    private boolean isInstrumentTradable(Etf etf) {
        return etf.getTradingStatus() == SecurityTradingStatus.SECURITY_TRADING_STATUS_NORMAL_TRADING &&
               etf.getBuyAvailableFlag() &&
               etf.getSellAvailableFlag() &&
               etf.getApiTradeAvailableFlag();
    }
    
    /**
     * Конвертация Share в ShareDto
     */
    private ShareDto convertToShareDto(Share share) {
        ShareDto dto = new ShareDto();
        dto.setFigi(share.getFigi());
        dto.setTicker(share.getTicker());
        dto.setName(share.getName());
        dto.setCurrency(share.getCurrency());
        dto.setExchange(share.getExchange());
        dto.setTradingStatus(share.getTradingStatus().name());
        dto.setInstrumentType("share");
        return dto;
    }
    
    /**
     * Конвертация Bond в ShareDto
     */
    private ShareDto convertToShareDto(Bond bond) {
        ShareDto dto = new ShareDto();
        dto.setFigi(bond.getFigi());
        dto.setTicker(bond.getTicker());
        dto.setName(bond.getName());
        dto.setCurrency(bond.getCurrency());
        dto.setExchange(bond.getExchange());
        dto.setTradingStatus(bond.getTradingStatus().name());
        dto.setInstrumentType("bond");
        return dto;
    }
    
    /**
     * Конвертация Etf в ShareDto
     */
    private ShareDto convertToShareDto(Etf etf) {
        ShareDto dto = new ShareDto();
        dto.setFigi(etf.getFigi());
        dto.setTicker(etf.getTicker());
        dto.setName(etf.getName());
        dto.setCurrency(etf.getCurrency());
        dto.setExchange(etf.getExchange());
        dto.setTradingStatus(etf.getTradingStatus().name());
        dto.setInstrumentType("etf");
        return dto;
    }
    
    /**
     * Резервный список инструментов
     */
    private List<ShareDto> getFallbackInstruments() {
        List<ShareDto> instruments = new ArrayList<>();
        
        // Тинькофф Банк
        ShareDto tinkoff = new ShareDto();
        tinkoff.setFigi("TCS00A106YF0");
        tinkoff.setTicker("TCS");
        tinkoff.setName("Тинькофф Банк");
        tinkoff.setCurrency("RUB");
        tinkoff.setExchange("MOEX");
        tinkoff.setTradingStatus("SECURITY_TRADING_STATUS_NORMAL_TRADING");
        tinkoff.setInstrumentType("share");
        instruments.add(tinkoff);
        
        // Сбербанк
        ShareDto sber = new ShareDto();
        sber.setFigi("BBG004730N88");
        sber.setTicker("SBER");
        sber.setName("Сбербанк России");
        sber.setCurrency("RUB");
        sber.setExchange("MOEX");
        sber.setTradingStatus("SECURITY_TRADING_STATUS_NORMAL_TRADING");
        sber.setInstrumentType("share");
        instruments.add(sber);
        
        // Облигация Тинькофф
        ShareDto bond = new ShareDto();
        bond.setFigi("TCS00A107D74");
        bond.setTicker("TCS00A10");
        bond.setName("Облигация Тинькофф");
        bond.setCurrency("RUB");
        bond.setExchange("MOEX");
        bond.setTradingStatus("SECURITY_TRADING_STATUS_NORMAL_TRADING");
        bond.setInstrumentType("bond");
        instruments.add(bond);
        
        log.info("Используется резервный список из {} инструментов", instruments.size());
        return instruments;
    }
    
    /**
     * Принудительное обновление кэша
     */
    public void forceUpdateCache() {
        log.info("Принудительное обновление кэша инструментов");
        lastUpdateTime.put("cache", 0L); // Сбрасываем время последнего обновления
        updateAvailableInstrumentsCache();
    }
    
    /**
     * Очистка кэша
     */
    public void clearCache() {
        log.info("Очистка кэша инструментов");
        availableInstrumentsCache.clear();
        lastUpdateTime.clear();
    }
}



