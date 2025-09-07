package ru.perminov.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.perminov.service.InstrumentNameService;
import ru.perminov.service.InvestApiManager;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/instrument-debug")
@RequiredArgsConstructor
@Slf4j
public class InstrumentDebugController {
    
    private final InstrumentNameService instrumentNameService;
    private final InvestApiManager investApiManager;
    
    /**
     * Отладка названий инструментов по FIGI
     */
    @GetMapping("/name")
    public ResponseEntity<?> debugInstrumentName(@RequestParam("figi") String figi,
                                                @RequestParam("type") String instrumentType) {
        try {
            log.info("=== ОТЛАДКА НАЗВАНИЯ ИНСТРУМЕНТА ===");
            log.info("FIGI: {}", figi);
            log.info("Тип: {}", instrumentType);
            
            String name = instrumentNameService.getInstrumentName(figi, instrumentType);
            String ticker = instrumentNameService.getTicker(figi, instrumentType);
            
            log.info("Полученное название: {}", name);
            log.info("Полученный тикер: {}", ticker);
            
            Map<String, Object> response = new HashMap<>();
            response.put("figi", figi);
            response.put("instrumentType", instrumentType);
            response.put("name", name);
            response.put("ticker", ticker);
            response.put("message", "Отладка завершена");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при отладке названия инструмента: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Ошибка при отладке: " + e.getMessage());
            error.put("errorType", e.getClass().getSimpleName());
            return ResponseEntity.status(500).body(error);
        }
    }
    
    /**
     * Проверка всех известных FIGI кодов
     */
    @GetMapping("/known-figis")
    public ResponseEntity<?> checkKnownFigis() {
        try {
            log.info("=== ПРОВЕРКА ИЗВЕСТНЫХ FIGI КОДОВ ===");
            
            String[] knownFigis = {
                "TCS00A106YF0", // Тинькофф
                "BBG004730N88", // Сбербанк
                "BBG0047315Y7", // Газпром
                "BBG004731354", // Лукойл
                "BBG004731489", // Новатэк
                "BBG004731032", // Роснефть
                "BBG0047315D0", // Магнит
                "BBG0047312Z9", // Яндекс
                "BBG0047319J7", // ВкусВилл
                "BBG0047319J8", // Ozon
                "BBG000B9XRY4", // VK
                "BBG000B9XRY5", // VKontakte
                "BBG000B9XRY6"  // VK Group
            };
            
            Map<String, Object> results = new HashMap<>();
            
            for (String figi : knownFigis) {
                Map<String, String> instrumentInfo = new HashMap<>();
                instrumentInfo.put("name", instrumentNameService.getInstrumentName(figi, "share"));
                instrumentInfo.put("ticker", instrumentNameService.getTicker(figi, "share"));
                results.put(figi, instrumentInfo);
                
                log.info("FIGI: {} -> Название: {}, Тикер: {}", 
                    figi, instrumentInfo.get("name"), instrumentInfo.get("ticker"));
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("knownFigis", results);
            response.put("message", "Проверка известных FIGI завершена");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при проверке известных FIGI: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Ошибка при проверке: " + e.getMessage());
            error.put("errorType", e.getClass().getSimpleName());
            return ResponseEntity.status(500).body(error);
        }
    }
    
    /**
     * Очистка кэша названий инструментов
     */
    @PostMapping("/clear-cache")
    public ResponseEntity<?> clearCache() {
        try {
            log.info("=== ОЧИСТКА КЭША НАЗВАНИЙ ИНСТРУМЕНТОВ ===");
            
            instrumentNameService.clearCache();
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Кэш названий инструментов очищен");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при очистке кэша: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Ошибка при очистке кэша: " + e.getMessage());
            error.put("errorType", e.getClass().getSimpleName());
            return ResponseEntity.status(500).body(error);
        }
    }
    
    /**
     * Статистика кэша
     */
    @GetMapping("/cache-stats")
    public ResponseEntity<?> getCacheStats() {
        try {
            log.info("=== СТАТИСТИКА КЭША НАЗВАНИЙ ИНСТРУМЕНТОВ ===");
            
            Map<String, Object> stats = instrumentNameService.getCacheStats();
            
            Map<String, Object> response = new HashMap<>();
            response.put("stats", stats);
            response.put("message", "Статистика кэша получена");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при получении статистики кэша: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Ошибка при получении статистики: " + e.getMessage());
            error.put("errorType", e.getClass().getSimpleName());
            return ResponseEntity.status(500).body(error);
        }
    }
    
    /**
     * Проверка размера лота для инструмента
     */
    @GetMapping("/lot-size")
    public ResponseEntity<?> checkLotSize(@RequestParam("figi") String figi,
                                         @RequestParam(value = "type", defaultValue = "share") String instrumentType) {
        try {
            log.info("=== ПРОВЕРКА РАЗМЕРА ЛОТА ===");
            log.info("FIGI: {}", figi);
            log.info("Тип: {}", instrumentType);
            
            var api = investApiManager.getCurrentInvestApi();
            int lotSize = 1; // дефолт
            String instrumentName = "Неизвестно";
            String ticker = "N/A";
            
            try {
                instrumentName = instrumentNameService.getInstrumentName(figi, instrumentType);
                ticker = instrumentNameService.getTicker(figi, instrumentType);
                
                if ("bond".equalsIgnoreCase(instrumentType)) {
                    var bond = api.getInstrumentsService().getBondByFigiSync(figi);
                    if (bond != null && bond.getLot() > 0) {
                        lotSize = bond.getLot();
                        log.info("Размер лота для облигации {}: {}", instrumentName, lotSize);
                    }
                } else if ("etf".equalsIgnoreCase(instrumentType)) {
                    var etf = api.getInstrumentsService().getEtfByFigiSync(figi);
                    if (etf != null && etf.getLot() > 0) {
                        lotSize = etf.getLot();
                        log.info("Размер лота для ETF {}: {}", instrumentName, lotSize);
                    }
                } else {
                    var share = api.getInstrumentsService().getShareByFigiSync(figi);
                    if (share != null && share.getLot() > 0) {
                        lotSize = share.getLot();
                        log.info("📊 Размер лота для акции {} ({}): {}", instrumentName, figi, lotSize);
                    }
                }
            } catch (Exception e) {
                log.warn("Ошибка получения размера лота для {} ({}): {}", instrumentName, figi, e.getMessage());
            }
            
            // Тестируем конвертацию
            int testShares = 1000;
            int calculatedLots = Math.max(testShares / Math.max(lotSize, 1), 0);
            
            log.info("🔢 ТЕСТ конвертации: {} акций ÷ {} (размер лота) = {} лотов", testShares, lotSize, calculatedLots);
            
            Map<String, Object> response = new HashMap<>();
            response.put("figi", figi);
            response.put("instrumentType", instrumentType);
            response.put("name", instrumentName);
            response.put("ticker", ticker);
            response.put("lotSize", lotSize);
            response.put("testConversion", Map.of(
                "shares", testShares,
                "lots", calculatedLots,
                "formula", testShares + " ÷ " + lotSize + " = " + calculatedLots
            ));
            response.put("message", "Проверка размера лота завершена");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при проверке размера лота: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Ошибка при проверке: " + e.getMessage());
            error.put("errorType", e.getClass().getSimpleName());
            return ResponseEntity.status(500).body(error);
        }
    }
}





