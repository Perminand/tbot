package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.contract.v1.Share;
import ru.tinkoff.piapi.contract.v1.Bond;
import ru.tinkoff.piapi.contract.v1.Etf;
import ru.tinkoff.piapi.contract.v1.Currency;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class InstrumentNameService {
    
    private final InvestApiManager investApiManager;
    
    // Кэш для названий инструментов
    private final Map<String, String> instrumentNameCache = new ConcurrentHashMap<>();
    private final Map<String, String> tickerCache = new ConcurrentHashMap<>();
    
    /**
     * Получение реального названия инструмента по FIGI
     */
    public String getInstrumentName(String figi, String instrumentType) {
        try {
            // Проверяем кэш
            String cachedName = instrumentNameCache.get(figi);
            if (cachedName != null) {
                return cachedName;
            }
            
            // Отладочное логирование для VK
            if (figi.startsWith("TCS") && figi.length() > 20) {
                log.info("DEBUG: Получение названия для VK-подобного инструмента (тип: {})", instrumentType);
            }
            
            String name = null;
            
            switch (instrumentType) {
                case "share":
                    name = getShareName(figi);
                    break;
                case "bond":
                    name = getBondName(figi);
                    break;
                case "etf":
                    name = getEtfName(figi);
                    break;
                case "currency":
                    name = getCurrencyName(figi);
                    break;
                default:
                    name = getFallbackName(figi, instrumentType);
                    break;
            }
            
            // Сохраняем в кэш
            if (name != null && !name.isEmpty()) {
                instrumentNameCache.put(figi, name);
                log.debug("Получено название: {}", name);
            }
            
            return name;
            
        } catch (Exception e) {
            log.warn("Ошибка получения названия: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Получение тикера по FIGI
     */
    public String getTicker(String figi, String instrumentType) {
        try {
            // Проверяем кэш
            String cachedTicker = tickerCache.get(figi);
            if (cachedTicker != null) {
                return cachedTicker;
            }
            
            String ticker = null;
            
            switch (instrumentType) {
                case "share":
                    ticker = getShareTicker(figi);
                    break;
                case "bond":
                    ticker = getBondTicker(figi);
                    break;
                case "etf":
                    ticker = getEtfTicker(figi);
                    break;
                case "currency":
                    ticker = getCurrencyTicker(figi);
                    break;
                default:
                    ticker = getFallbackTicker(figi);
                    break;
            }
            
            // Сохраняем в кэш
            if (ticker != null && !ticker.isEmpty()) {
                tickerCache.put(figi, ticker);
                log.debug("Получен тикер: {}", ticker);
            }
            
            return ticker;
            
        } catch (Exception e) {
            log.warn("Ошибка получения тикера: {}", e.getMessage());
            return null;
        }
    }
    
    private String getShareName(String figi) {
        try {
            Share share = investApiManager.getCurrentInvestApi()
                .getInstrumentsService()
                .getShareByFigiSync(figi);
            
            if (share != null) {
                return share.getName();
            }
        } catch (Exception e) {
            log.debug("Не удалось получить акцию: {}", e.getMessage());
            return null;
        }
        return null;
    }
    
    private String getBondName(String figi) {
        try {
            Bond bond = investApiManager.getCurrentInvestApi()
                .getInstrumentsService()
                .getBondByFigiSync(figi);
            
            if (bond != null) {
                return bond.getName();
            }
        } catch (Exception e) {
            log.debug("Не удалось получить облигацию: {}", e.getMessage());
            return null;
        }
        return null;
    }
    
    private String getEtfName(String figi) {
        try {
            Etf etf = investApiManager.getCurrentInvestApi()
                .getInstrumentsService()
                .getEtfByFigiSync(figi);
            
            if (etf != null) {
                return etf.getName();
            }
        } catch (Exception e) {
            log.debug("Не удалось получить ETF: {}", e.getMessage());
            return null;
        }
        return null;
    }
    
    private String getCurrencyName(String figi) {
        try {
            Currency currency = investApiManager.getCurrentInvestApi()
                .getInstrumentsService()
                .getCurrencyByFigiSync(figi);
            
            if (currency != null) {
                return currency.getName();
            }
        } catch (Exception e) {
            log.debug("Не удалось получить валюту: {}", e.getMessage());
            return null;
        }
        return null;
    }
    
    private String getShareTicker(String figi) {
        try {
            Share share = investApiManager.getCurrentInvestApi()
                .getInstrumentsService()
                .getShareByFigiSync(figi);
            
            if (share != null) {
                return share.getTicker();
            }
        } catch (Exception e) {
            log.debug("Не удалось получить тикер акции: {}", e.getMessage());
            return null;
        }
        return null;
    }
    
    private String getBondTicker(String figi) {
        try {
            Bond bond = investApiManager.getCurrentInvestApi()
                .getInstrumentsService()
                .getBondByFigiSync(figi);
            
            if (bond != null) {
                return bond.getTicker();
            }
        } catch (Exception e) {
            log.debug("Не удалось получить тикер облигации: {}", e.getMessage());
            return null;
        }
        return null;
    }
    
    private String getEtfTicker(String figi) {
        try {
            Etf etf = investApiManager.getCurrentInvestApi()
                .getInstrumentsService()
                .getEtfByFigiSync(figi);
            
            if (etf != null) {
                return etf.getTicker();
            }
        } catch (Exception e) {
            log.debug("Не удалось получить тикер ETF: {}", e.getMessage());
            return null;
        }
        return null;
    }
    
    private String getCurrencyTicker(String figi) {
        try {
            Currency currency = investApiManager.getCurrentInvestApi()
                .getInstrumentsService()
                .getCurrencyByFigiSync(figi);
            
            if (currency != null) {
                return currency.getTicker();
            }
        } catch (Exception e) {
            log.debug("Не удалось получить тикер валюты: {}", e.getMessage());
            return null;
        }
        return null;
    }
    
    private String getFallbackName(String figi, String instrumentType) {
        // Отладочное логирование
        log.debug("DEBUG: getFallbackName для инструмента (тип: {})", instrumentType);
        
        // Резервные названия для известных инструментов
        switch (figi) {
            case "TCS00A106YF0":
                return "Тинькофф Банк";
            case "BBG004730N88":
                return "Сбербанк России";
            case "BBG0047315Y7":
                return "Газпром";
            case "BBG004731354":
                return "Лукойл";
            case "BBG004731489":
                return "Новатэк";
            case "BBG004731032":
                return "Роснефть";
            case "BBG0047315D0":
                return "Магнит";
            case "BBG0047312Z9":
                return "Яндекс";
            case "BBG0047319J7":
                return "ВкусВилл";
            case "BBG0047319J8":
                return "Ozon";
            case "BBG000B9XRY4":
                return "VK";
            case "BBG000B9XRY5":
                return "VKontakte";
            case "BBG000B9XRY6":
                return "VK Group";
            default:
                // Для неизвестных FIGI используем общее название
                return getInstrumentTypeDisplayName(instrumentType) + " " + getFallbackTicker(figi);
        }
    }
    
    private String getFallbackTicker(String figi) {
        // Резервные тикеры для известных инструментов
        switch (figi) {
            case "TCS00A106YF0":
                return "TCS";
            case "BBG004730N88":
                return "SBER";
            case "BBG0047315Y7":
                return "GAZP";
            case "BBG004731354":
                return "LKOH";
            case "BBG004731489":
                return "NVTK";
            case "BBG004731032":
                return "ROSN";
            case "BBG0047315D0":
                return "MGNT";
            case "BBG0047312Z9":
                return "YNDX";
            case "BBG0047319J7":
                return "VKUS";
            case "BBG0047319J8":
                return "OZON";
            case "BBG000B9XRY4":
                return "VKCO";
            case "BBG000B9XRY5":
                return "VK";
            case "BBG000B9XRY6":
                return "VKGP";
            default:
                // Для неизвестных FIGI извлекаем из кода
                if (figi.startsWith("BBG") && figi.length() > 7) {
                    return figi.substring(4, 8);
                }
                return figi.substring(0, Math.min(8, figi.length()));
        }
    }
    
    private String getInstrumentTypeDisplayName(String instrumentType) {
        switch (instrumentType) {
            case "share":
                return "Акция";
            case "bond":
                return "Облигация";
            case "etf":
                return "ETF";
            case "currency":
                return "Валюта";
            default:
                return "Инструмент";
        }
    }
    
    /**
     * Очистка кэша
     */
    public void clearCache() {
        instrumentNameCache.clear();
        tickerCache.clear();
        log.info("Кэш названий инструментов очищен");
    }
    
    /**
     * Получение статистики кэша
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("instrumentNamesCount", instrumentNameCache.size());
        stats.put("tickersCount", tickerCache.size());
        stats.put("totalCacheSize", instrumentNameCache.size() + tickerCache.size());
        return stats;
    }
}
