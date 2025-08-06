package ru.perminov.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.perminov.service.MarketAnalysisService;
import ru.perminov.service.PortfolioManagementService;
import ru.perminov.service.TradingBotScheduler;
import ru.perminov.service.BotLogService;
import ru.perminov.service.AdvancedTechnicalAnalysisService;
import ru.perminov.service.AdvancedTradingStrategyService;
import ru.perminov.service.AdvancedPortfolioManagementService;
import ru.perminov.service.RiskManagementService;
import ru.perminov.service.AccountService;

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
    private final AdvancedTechnicalAnalysisService advancedAnalysisService;
    private final AdvancedTradingStrategyService advancedTradingStrategyService;
    private final AdvancedPortfolioManagementService advancedPortfolioManagementService;
    private final RiskManagementService riskManagementService;
    private final AccountService accountService;
    
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
     * Получение списка доступных аккаунтов
     */
    @GetMapping("/accounts")
    public ResponseEntity<?> getAvailableAccounts() {
        try {
            List<ru.tinkoff.piapi.contract.v1.Account> accounts = accountService.getAccounts();
            
            Map<String, Object> response = new HashMap<>();
            response.put("accounts", accounts.stream()
                .map(account -> Map.of(
                    "id", account.getId(),
                    "name", account.getName(),
                    "type", account.getType().name(),
                    "status", account.getStatus().name()
                ))
                .collect(java.util.stream.Collectors.toList()));
            response.put("count", accounts.size());
            response.put("currentAccount", getFirstAccountId());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при получении списка аккаунтов: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("Ошибка при получении списка аккаунтов: " + e.getMessage());
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
            
            // Продвинутые индикаторы
            AdvancedTechnicalAnalysisService.MACDResult macd = 
                advancedAnalysisService.calculateMACD(figi, ru.tinkoff.piapi.contract.v1.CandleInterval.CANDLE_INTERVAL_DAY);
            AdvancedTechnicalAnalysisService.BollingerBandsResult bb = 
                advancedAnalysisService.calculateBollingerBands(figi, ru.tinkoff.piapi.contract.v1.CandleInterval.CANDLE_INTERVAL_DAY, 20);
            AdvancedTechnicalAnalysisService.StochasticResult stoch = 
                advancedAnalysisService.calculateStochastic(figi, ru.tinkoff.piapi.contract.v1.CandleInterval.CANDLE_INTERVAL_DAY, 14);
            AdvancedTechnicalAnalysisService.VolumeAnalysisResult volume = 
                advancedAnalysisService.analyzeVolume(figi, ru.tinkoff.piapi.contract.v1.CandleInterval.CANDLE_INTERVAL_DAY);
            AdvancedTechnicalAnalysisService.SupportResistanceResult sr = 
                advancedAnalysisService.findSupportResistance(figi, ru.tinkoff.piapi.contract.v1.CandleInterval.CANDLE_INTERVAL_DAY);
            
            Map<String, Object> response = new HashMap<>();
            response.put("figi", figi);
            response.put("sma20", sma20);
            response.put("sma50", sma50);
            response.put("rsi", rsi);
            response.put("macd", Map.of(
                "macdLine", macd.getMacdLine(),
                "signalLine", macd.getSignalLine(),
                "histogram", macd.getHistogram()
            ));
            response.put("bollingerBands", Map.of(
                "upperBand", bb.getUpperBand(),
                "middleBand", bb.getMiddleBand(),
                "lowerBand", bb.getLowerBand()
            ));
            response.put("stochastic", Map.of(
                "kPercent", stoch.getKPercent(),
                "dPercent", stoch.getDPercent()
            ));
            response.put("volume", Map.of(
                "currentVolume", volume.getCurrentVolume(),
                "volumeRatio", volume.getVolumeRatio(),
                "volumeSignal", volume.getVolumeSignal()
            ));
            response.put("supportResistance", Map.of(
                "support", sr.getSupport(),
                "resistance", sr.getResistance()
            ));
            
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
     * Включение автоматического мониторинга
     */
    @PostMapping("/start-monitoring")
    public ResponseEntity<?> startAutoMonitoring() {
        try {
            log.info("Включение автоматического мониторинга");
            
            // Получаем первый доступный аккаунт
            String accountId = getFirstAccountId();
            if (accountId == null) {
                return ResponseEntity.badRequest().body("Нет доступных аккаунтов");
            }
            
            portfolioManagementService.startAutoMonitoring(accountId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("accountId", accountId);
            response.put("status", "success");
            response.put("message", "Автоматический мониторинг включен");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при включении автоматического мониторинга: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("Ошибка при включении автоматического мониторинга: " + e.getMessage());
        }
    }
    
    /**
     * Выключение автоматического мониторинга
     */
    @PostMapping("/stop-monitoring")
    public ResponseEntity<?> stopAutoMonitoring() {
        try {
            log.info("Выключение автоматического мониторинга");
            
            portfolioManagementService.stopAutoMonitoring();
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Автоматический мониторинг выключен");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при выключении автоматического мониторинга: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("Ошибка при выключении автоматического мониторинга: " + e.getMessage());
        }
    }
    
    /**
     * Получение статуса автоматического мониторинга
     */
    @GetMapping("/monitoring-status")
    public ResponseEntity<?> getMonitoringStatus() {
        try {
            boolean isEnabled = portfolioManagementService.isAutoMonitoringEnabled();
            
            Map<String, Object> response = new HashMap<>();
            response.put("monitoringEnabled", isEnabled);
            response.put("status", "success");
            response.put("message", isEnabled ? "Мониторинг активен" : "Мониторинг неактивен");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при получении статуса мониторинга: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("Ошибка при получении статуса мониторинга: " + e.getMessage());
        }
    }
    
    /**
     * Получение первого доступного аккаунта
     */
    private String getFirstAccountId() {
        try {
            List<ru.tinkoff.piapi.contract.v1.Account> accounts = accountService.getAccounts();
            if (accounts != null && !accounts.isEmpty()) {
                String accountId = accounts.get(0).getId();
                log.info("Используется аккаунт: {}", accountId);
                return accountId;
            } else {
                log.warn("Нет доступных аккаунтов");
                return null;
            }
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
    
    /**
     * Продвинутый анализ торговых сигналов
     */
    @GetMapping("/advanced-signal/{figi}")
    public ResponseEntity<?> getAdvancedTradingSignal(@PathVariable("figi") String figi) {
        try {
            log.info("Получение продвинутого торгового сигнала для: {}", figi);
            
            String accountId = getFirstAccountId();
            if (accountId == null) {
                return ResponseEntity.badRequest().body("Нет доступных аккаунтов");
            }
            
            AdvancedTradingStrategyService.TradingSignal signal = 
                advancedTradingStrategyService.analyzeTradingSignal(figi, accountId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("figi", figi);
            response.put("action", signal.getAction());
            response.put("strength", signal.getStrength());
            response.put("signals", signal.getSignals());
            response.put("riskLevel", signal.getRiskLevel());
            response.put("riskAction", signal.getRiskAction());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при получении продвинутого торгового сигнала: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("Ошибка при получении продвинутого торгового сигнала: " + e.getMessage());
        }
    }
    
    /**
     * Анализ следования за трендом
     */
    @GetMapping("/trend-following/{figi}")
    public ResponseEntity<?> getTrendFollowingAnalysis(@PathVariable("figi") String figi) {
        try {
            log.info("Получение анализа следования за трендом для: {}", figi);
            
            AdvancedTradingStrategyService.TrendFollowingSignal signal = 
                advancedTradingStrategyService.analyzeTrendFollowing(figi);
            
            Map<String, Object> response = new HashMap<>();
            response.put("figi", figi);
            response.put("signal", signal.getSignal());
            response.put("reason", signal.getReason());
            response.put("dailyTrend", signal.getDailyTrend());
            response.put("weeklyTrend", signal.getWeeklyTrend());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при получении анализа следования за трендом: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("Ошибка при получении анализа следования за трендом: " + e.getMessage());
        }
    }
    
    /**
     * Анализ уровней поддержки и сопротивления
     */
    @GetMapping("/support-resistance/{figi}")
    public ResponseEntity<?> getSupportResistanceAnalysis(@PathVariable("figi") String figi) {
        try {
            log.info("Получение анализа уровней поддержки/сопротивления для: {}", figi);
            
            AdvancedTradingStrategyService.SupportResistanceSignal signal = 
                advancedTradingStrategyService.analyzeSupportResistance(figi);
            
            Map<String, Object> response = new HashMap<>();
            response.put("figi", figi);
            response.put("signal", signal.getSignal());
            response.put("reason", signal.getReason());
            response.put("support", signal.getSupport());
            response.put("resistance", signal.getResistance());
            response.put("currentPrice", signal.getCurrentPrice());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при получении анализа уровней поддержки/сопротивления: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("Ошибка при получении анализа уровней поддержки/сопротивления: " + e.getMessage());
        }
    }
    
    /**
     * Динамическая ребалансировка портфеля
     */
    @PostMapping("/dynamic-rebalancing/{accountId}")
    public ResponseEntity<?> performDynamicRebalancing(@PathVariable("accountId") String accountId) {
        try {
            log.info("Выполнение динамической ребалансировки для аккаунта: {}", accountId);
            
            AdvancedPortfolioManagementService.DynamicRebalancingResult result = 
                advancedPortfolioManagementService.performDynamicRebalancing(accountId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("accountId", accountId);
            response.put("marketCondition", result.getMarketCondition());
            response.put("targetAllocation", Map.of(
                "shares", result.getTargetAllocation().getSharesAllocation(),
                "bonds", result.getTargetAllocation().getBondsAllocation(),
                "etf", result.getTargetAllocation().getEtfAllocation()
            ));
            response.put("actions", result.getActions().stream()
                .map(action -> Map.of(
                    "type", action.getType(),
                    "amount", action.getAmount(),
                    "description", action.getDescription()
                ))
                .collect(java.util.stream.Collectors.toList()));
            response.put("totalValue", result.getTotalValue());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при динамической ребалансировке: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("Ошибка при динамической ребалансировке: " + e.getMessage());
        }
    }
    
    /**
     * Анализ диверсификации портфеля
     */
    @GetMapping("/diversification/{accountId}")
    public ResponseEntity<?> getDiversificationAnalysis(@PathVariable("accountId") String accountId) {
        try {
            log.info("Получение анализа диверсификации для аккаунта: {}", accountId);
            
            AdvancedPortfolioManagementService.DiversificationAnalysis analysis = 
                advancedPortfolioManagementService.analyzeDiversification(accountId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("accountId", accountId);
            response.put("sectorAllocation", analysis.getSectorAllocation());
            response.put("countryAllocation", analysis.getCountryAllocation());
            response.put("correlationRisk", analysis.getCorrelationRisk());
            response.put("diversificationScore", analysis.getScore());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при получении анализа диверсификации: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("Ошибка при получении анализа диверсификации: " + e.getMessage());
        }
    }
    
    /**
     * Получение рекомендаций по риск-менеджменту
     */
    @GetMapping("/risk-recommendation/{accountId}")
    public ResponseEntity<?> getRiskRecommendation(@PathVariable("accountId") String accountId) {
        try {
            log.info("Получение рекомендаций по риск-менеджменту для аккаунта: {}", accountId);
            
            RiskManagementService.RiskRecommendation recommendation = 
                riskManagementService.getRiskRecommendation(accountId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("accountId", accountId);
            response.put("recommendation", recommendation.getRecommendation());
            response.put("action", recommendation.getAction());
            response.put("drawdown", recommendation.getDrawdown());
            response.put("dailyLoss", recommendation.getDailyLoss());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при получении рекомендаций по риск-менеджменту: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("Ошибка при получении рекомендаций по риск-менеджменту: " + e.getMessage());
        }
    }
    
    /**
     * Проверка рисков для позиции
     */
    @PostMapping("/risk-check/{accountId}")
    public ResponseEntity<?> checkPositionRisk(
            @PathVariable("accountId") String accountId,
            @RequestParam("figi") String figi,
            @RequestParam("price") BigDecimal price,
            @RequestParam("lots") int lots) {
        try {
            log.info("Проверка рисков для позиции: {} в аккаунте {}", figi, accountId);
            
            RiskManagementService.RiskCheckResult result = 
                riskManagementService.checkPositionRisk(accountId, figi, price, lots);
            
            Map<String, Object> response = new HashMap<>();
            response.put("accountId", accountId);
            response.put("figi", figi);
            response.put("approved", result.isApproved());
            response.put("reason", result.getReason());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при проверке рисков позиции: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("Ошибка при проверке рисков позиции: " + e.getMessage());
        }
    }
    
    /**
     * Расчет стоп-лосса и тейк-профита
     */
    @GetMapping("/stop-loss-take-profit/{figi}")
    public ResponseEntity<?> getStopLossTakeProfit(
            @PathVariable("figi") String figi,
            @RequestParam("entryPrice") BigDecimal entryPrice,
            @RequestParam("direction") String direction) {
        try {
            log.info("Расчет стоп-лосса и тейк-профита для: {}", figi);
            
            RiskManagementService.StopLossTakeProfit result = 
                riskManagementService.calculateStopLossTakeProfit(figi, entryPrice, direction);
            
            Map<String, Object> response = new HashMap<>();
            response.put("figi", figi);
            response.put("entryPrice", entryPrice);
            response.put("direction", direction);
            response.put("stopLoss", result.getStopLoss());
            response.put("takeProfit", result.getTakeProfit());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при расчете стоп-лосса и тейк-профита: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("Ошибка при расчете стоп-лосса и тейк-профита: " + e.getMessage());
        }
    }
} 