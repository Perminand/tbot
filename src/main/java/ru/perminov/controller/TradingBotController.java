package ru.perminov.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.perminov.service.MarketAnalysisService;
import ru.perminov.service.PortfolioManagementService;
import ru.perminov.service.TradingBotScheduler;
import ru.perminov.service.BotLogService;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/trading-bot")
@RequiredArgsConstructor
@Slf4j
public class TradingBotController {
    
    private final TradingBotScheduler tradingBotScheduler;
    private final MarketAnalysisService marketAnalysisService;
    private final PortfolioManagementService portfolioManagementService;
    private final BotLogService botLogService;
    
    /**
     * Получение анализа тренда для инструмента
     */
    @GetMapping("/analysis/{figi}")
    public ResponseEntity<?> getTrendAnalysis(@PathVariable("figi") String figi) {
        try {
            log.info("Получение анализа тренда для инструмента: {}", figi);
            
            MarketAnalysisService.TrendAnalysis analysis = 
                marketAnalysisService.analyzeTrend(figi, ru.tinkoff.piapi.contract.v1.CandleInterval.CANDLE_INTERVAL_DAY);
            
            Map<String, Object> response = new HashMap<>();
            response.put("figi", figi);
            response.put("trend", analysis.getTrend().name());
            response.put("signal", analysis.getSignal());
            response.put("currentPrice", analysis.getCurrentPrice());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при получении анализа тренда: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("Ошибка при получении анализа тренда: " + e.getMessage());
        }
    }
    
    /**
     * Получение анализа портфеля
     */
    @GetMapping("/portfolio/{accountId}")
    public ResponseEntity<?> getPortfolioAnalysis(@PathVariable("accountId") String accountId) {
        try {
            log.info("Получение анализа портфеля для аккаунта: {}", accountId);
            
            PortfolioManagementService.PortfolioAnalysis analysis = 
                portfolioManagementService.analyzePortfolio(accountId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("accountId", accountId);
            response.put("totalValue", analysis.getTotalValue());
            response.put("allocations", analysis.getAllocationPercentages());
            response.put("positionsCount", analysis.getPositions().size());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при получении анализа портфеля: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("Ошибка при получении анализа портфеля: " + e.getMessage());
        }
    }
    
    /**
     * Проверка необходимости ребалансировки
     */
    @GetMapping("/rebalancing/{accountId}")
    public ResponseEntity<?> checkRebalancing(@PathVariable("accountId") String accountId) {
        try {
            log.info("Проверка необходимости ребалансировки для аккаунта: {}", accountId);
            
            PortfolioManagementService.RebalancingDecision decision = 
                portfolioManagementService.checkRebalancing(accountId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("accountId", accountId);
            response.put("needsRebalancing", decision.isNeedsRebalancing());
            response.put("reason", decision.getReason());
            response.put("maxDeviation", decision.getMaxDeviation());
            response.put("deviations", decision.getDeviations());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при проверке ребалансировки: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("Ошибка при проверке ребалансировки: " + e.getMessage());
        }
    }
    
    /**
     * Ручной запуск ребалансировки портфеля
     */
    @PostMapping("/rebalancing/{accountId}")
    public ResponseEntity<?> executeRebalancing(@PathVariable("accountId") String accountId) {
        try {
            log.info("Ручной запуск ребалансировки для аккаунта: {}", accountId);
            
            portfolioManagementService.rebalancePortfolio(accountId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("accountId", accountId);
            response.put("status", "success");
            response.put("message", "Ребалансировка выполнена");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при выполнении ребалансировки: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("Ошибка при выполнении ребалансировки: " + e.getMessage());
        }
    }
    
    /**
     * Ручной запуск торговой стратегии
     */
    @PostMapping("/strategy/{accountId}/{figi}")
    public ResponseEntity<?> executeTradingStrategy(
            @PathVariable("accountId") String accountId,
            @PathVariable("figi") String figi) {
        try {
            log.info("Ручной запуск торговой стратегии для {} в аккаунте {}", figi, accountId);
            
            tradingBotScheduler.manualTradingStrategy(accountId, figi);
            
            Map<String, Object> response = new HashMap<>();
            response.put("accountId", accountId);
            response.put("figi", figi);
            response.put("status", "success");
            response.put("message", "Торговая стратегия выполнена");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при выполнении торговой стратегии: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("Ошибка при выполнении торговой стратегии: " + e.getMessage());
        }
    }
    
    /**
     * Получение статуса торгового бота
     */
    @GetMapping("/status")
    public ResponseEntity<?> getBotStatus() {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "running");
            response.put("scheduler", "active");
            response.put("lastUpdate", System.currentTimeMillis());
            response.put("features", Map.of(
                "marketAnalysis", true,
                "portfolioManagement", true,
                "automaticTrading", true,
                "rebalancing", true
            ));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при получении статуса бота: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("Ошибка при получении статуса бота: " + e.getMessage());
        }
    }
    
    /**
     * Получение технических индикаторов
     */
    @GetMapping("/indicators/{figi}")
    public ResponseEntity<?> getTechnicalIndicators(@PathVariable("figi") String figi) {
        try {
            log.info("Получение технических индикаторов для: {}", figi);
            
            BigDecimal sma20 = marketAnalysisService.calculateSMA(figi, 
                ru.tinkoff.piapi.contract.v1.CandleInterval.CANDLE_INTERVAL_DAY, 20);
            BigDecimal sma50 = marketAnalysisService.calculateSMA(figi, 
                ru.tinkoff.piapi.contract.v1.CandleInterval.CANDLE_INTERVAL_DAY, 50);
            BigDecimal rsi = marketAnalysisService.calculateRSI(figi, 
                ru.tinkoff.piapi.contract.v1.CandleInterval.CANDLE_INTERVAL_DAY, 14);
            
            Map<String, Object> response = new HashMap<>();
            response.put("figi", figi);
            response.put("sma20", sma20);
            response.put("sma50", sma50);
            response.put("rsi", rsi);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при получении технических индикаторов: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("Ошибка при получении технических индикаторов: " + e.getMessage());
        }
    }
    
    /**
     * Получение лучших торговых возможностей
     */
    @GetMapping("/opportunities")
    public ResponseEntity<?> getTradingOpportunities() {
        try {
            log.info("Получение лучших торговых возможностей");
            
            // Получаем первый доступный аккаунт
            String accountId = getFirstAccountId();
            if (accountId == null) {
                return ResponseEntity.badRequest().body("Нет доступных аккаунтов");
            }
            
            List<PortfolioManagementService.TradingOpportunity> opportunities = 
                portfolioManagementService.findBestTradingOpportunities(accountId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("accountId", accountId);
            response.put("opportunities", opportunities);
            response.put("count", opportunities.size());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при получении торговых возможностей: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("Ошибка при получении торговых возможностей: " + e.getMessage());
        }
    }
    
    /**
     * Автоматическая торговля
     */
    @PostMapping("/auto-trade")
    public ResponseEntity<?> executeAutomaticTrading() {
        try {
            log.info("Запуск автоматической торговли");
            
            // Получаем первый доступный аккаунт
            String accountId = getFirstAccountId();
            if (accountId == null) {
                return ResponseEntity.badRequest().body("Нет доступных аккаунтов");
            }
            
            portfolioManagementService.executeAutomaticTrading(accountId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("accountId", accountId);
            response.put("status", "success");
            response.put("message", "Автоматическая торговля выполнена");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при автоматической торговле: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("Ошибка при автоматической торговле: " + e.getMessage());
        }
    }
    
    /**
     * Получение первого доступного аккаунта
     */
    private String getFirstAccountId() {
        try {
            // Здесь можно добавить получение аккаунта из AccountService
            // Пока возвращаем тестовый аккаунт
            return "031c83e0-377c-48fc-ba06-8ee9aad06e98";
        } catch (Exception e) {
            log.error("Ошибка при получении аккаунта: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Получение лога действий бота
     */
    @GetMapping("/log")
    public ResponseEntity<?> getBotLog(@RequestParam(value = "limit", defaultValue = "50") int limit,
                                      @RequestParam(value = "level", required = false) String level,
                                      @RequestParam(value = "category", required = false) String category) {
        try {
            log.info("Получение лога бота. Лимит: {}, Уровень: {}, Категория: {}", limit, level, category);
            
            BotLogService.LogLevel logLevel = null;
            BotLogService.LogCategory logCategory = null;
            
            if (level != null) {
                try {
                    logLevel = BotLogService.LogLevel.valueOf(level.toUpperCase());
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.badRequest().body("Неверный уровень лога: " + level);
                }
            }
            
            if (category != null) {
                try {
                    logCategory = BotLogService.LogCategory.valueOf(category.toUpperCase());
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.badRequest().body("Неверная категория лога: " + category);
                }
            }
            
            List<BotLogService.BotLogEntry> logEntries = botLogService.getLogEntries(logLevel, logCategory, limit);
            BotLogService.LogStatistics statistics = botLogService.getLogStatistics();
            
            Map<String, Object> response = new HashMap<>();
            response.put("entries", logEntries);
            response.put("statistics", statistics);
            response.put("totalEntries", logEntries.size());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при получении лога бота: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("Ошибка при получении лога бота: " + e.getMessage());
        }
    }
    
    /**
     * Получение статистики лога
     */
    @GetMapping("/log/statistics")
    public ResponseEntity<?> getLogStatistics() {
        try {
            BotLogService.LogStatistics statistics = botLogService.getLogStatistics();
            return ResponseEntity.ok(statistics);
        } catch (Exception e) {
            log.error("Ошибка при получении статистики лога: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("Ошибка при получении статистики лога: " + e.getMessage());
        }
    }
    
    /**
     * Очистка лога
     */
    @DeleteMapping("/log")
    public ResponseEntity<?> clearBotLog() {
        try {
            botLogService.clearLog();
            return ResponseEntity.ok(Map.of("message", "Лог очищен"));
        } catch (Exception e) {
            log.error("Ошибка при очистке лога: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("Ошибка при очистке лога: " + e.getMessage());
        }
    }
    
    /**
     * Получение последних записей лога
     */
    @GetMapping("/log/recent")
    public ResponseEntity<?> getRecentLogEntries(@RequestParam(value = "count", defaultValue = "20") int count) {
        try {
            List<BotLogService.BotLogEntry> recentEntries = botLogService.getRecentLogEntries(count);
            return ResponseEntity.ok(recentEntries);
        } catch (Exception e) {
            log.error("Ошибка при получении последних записей лога: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("Ошибка при получении последних записей лога: " + e.getMessage());
        }
    }
} 