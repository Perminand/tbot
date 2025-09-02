package ru.perminov.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.perminov.service.*;
import ru.perminov.service.BotLogService;
import ru.perminov.service.PortfolioManagementService;
import ru.perminov.service.RiskManagementService;
import ru.perminov.service.TradingModeProtectionService;
import ru.perminov.service.AdvancedPortfolioManagementService;
import ru.perminov.service.MarginService;
import ru.perminov.service.TradingSettingsService;
import ru.perminov.repository.OrderRepository;
import ru.perminov.model.Order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Slf4j
public class DashboardController {
    
    private final TradingModeProtectionService protectionService;
    private final PortfolioManagementService portfolioManagementService;
    private final AdvancedPortfolioManagementService advancedPortfolioManagementService;
    private final RiskManagementService riskManagementService;
    private final BotLogService botLogService;
    private final MarginService marginService;
    private final TradingSettingsService tradingSettingsService;
    private final MarketAnalysisService marketAnalysisService;
    private final OrderRepository orderRepository;
    
    /**
     * Получение статуса системы
     */
    @GetMapping("/system-status")
    public ResponseEntity<?> getSystemStatus() {
        try {
            Map<String, Object> response = new HashMap<>();
            
            // Статус защиты режима
            String protectionStatus = protectionService.getProtectionStatus();
            response.put("protectionStatus", protectionStatus);
            response.put("isProtected", protectionStatus.contains("Активна"));
            
            // Время последней проверки
            LocalDateTime now = LocalDateTime.now();
            response.put("lastCheck", now.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            response.put("lastCheckAgo", "2 мин назад"); // Можно сделать более точным
            
            // Активные процессы
            Map<String, Boolean> activeProcesses = new HashMap<>();
            activeProcesses.put("quickAnalysis", true);
            activeProcesses.put("fullAnalysis", true);
            activeProcesses.put("positionWatch", true);
            activeProcesses.put("riskMonitor", true);
            response.put("activeProcesses", activeProcesses);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при получении статуса системы: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("Ошибка при получении статуса системы: " + e.getMessage());
        }
    }
    
    /**
     * Получение рыночных условий
     */
    @GetMapping("/market-conditions")
    public ResponseEntity<?> getMarketConditions() {
        try {
            Map<String, Object> response = new HashMap<>();
            
            // Определение рыночных условий (упрощенная версия)
            String marketCondition = "BULL_MARKET"; // Можно сделать динамическим
            response.put("marketCondition", marketCondition);
            response.put("marketConditionDisplay", getMarketConditionDisplay(marketCondition));
            
            // Индикаторы рынка
            Map<String, String> indicators = new HashMap<>();
            indicators.put("SP500", "+2.1%");
            indicators.put("NASDAQ", "+1.8%");
            indicators.put("DOW", "+1.5%");
            response.put("marketIndicators", indicators);
            
            // Время последнего обновления
            response.put("lastUpdate", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при получении рыночных условий: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("Ошибка при получении рыночных условий: " + e.getMessage());
        }
    }
    
    /**
     * Получение анализа рисков
     */
    @GetMapping("/risk-analysis/{accountId}")
    public ResponseEntity<?> getRiskAnalysis(@PathVariable String accountId) {
        try {
            Map<String, Object> response = new HashMap<>();
            
            // Уровень риска
            String riskLevel = "MEDIUM";
            response.put("riskLevel", riskLevel);
            response.put("riskLevelDisplay", getRiskLevelDisplay(riskLevel));
            
            // Дневной P&L
            BigDecimal dailyPnL = new BigDecimal("1.2");
            response.put("dailyPnL", dailyPnL);
            response.put("dailyPnLPercent", "+1.2%");
            
            // Максимальные позиции
            Map<String, Object> maxPositions = new HashMap<>();
            maxPositions.put("maxPositionSize", "5%");
            maxPositions.put("maxSectorSize", "20%");
            maxPositions.put("maxDailyLoss", "2%");
            response.put("maxPositions", maxPositions);
            
            // Текущие риски
            List<Map<String, Object>> currentRisks = List.of(
                Map.of("type", "POSITION_SIZE", "description", "Позиция AAPL превышает 5%", "severity", "MEDIUM"),
                Map.of("type", "DAILY_LOSS", "description", "Дневной убыток близок к лимиту", "severity", "LOW")
            );
            response.put("currentRisks", currentRisks);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при получении анализа рисков: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("Ошибка при получении анализа рисков: " + e.getMessage());
        }
    }
    
    /**
     * Получение активных позиций с деталями
     */
    @GetMapping("/active-positions/{accountId}")
    public ResponseEntity<?> getActivePositions(@PathVariable String accountId) {
        try {
            Map<String, Object> response = new HashMap<>();
            
            // Получаем анализ портфеля
            PortfolioManagementService.PortfolioAnalysis analysis = 
                portfolioManagementService.analyzePortfolio(accountId);
            
            // Формируем список активных позиций
            List<Map<String, Object>> positions = analysis.getPositions().stream()
                .filter(position -> position.getQuantity().compareTo(BigDecimal.ZERO) != 0)
                .map(position -> {
                    Map<String, Object> pos = new HashMap<>();
                    pos.put("figi", position.getFigi());
                    pos.put("quantity", position.getQuantity());
                    pos.put("averagePrice", position.getAveragePositionPrice());
                    pos.put("currentPrice", position.getCurrentPrice());
                    
                    // Расчет P&L
                    BigDecimal quantity = position.getQuantity();
                    BigDecimal avgPrice = BigDecimal.ZERO;
                    BigDecimal currentPrice = BigDecimal.ZERO;
                    
                    // Извлекаем значения из Money объектов
                    if (position.getAveragePositionPrice() != null) {
                        try {
                            if (position.getAveragePositionPrice() instanceof ru.tinkoff.piapi.core.models.Money) {
                                ru.tinkoff.piapi.core.models.Money avgMoney = (ru.tinkoff.piapi.core.models.Money) position.getAveragePositionPrice();
                                avgPrice = avgMoney.getValue();
                            } else {
                                // Фоллбек на парсинг строки
                                String avgPriceStr = position.getAveragePositionPrice().toString();
                                if (avgPriceStr.contains("value=")) {
                                    String valuePart = avgPriceStr.substring(avgPriceStr.indexOf("value=") + 6);
                                    valuePart = valuePart.substring(0, valuePart.indexOf(","));
                                    avgPrice = new BigDecimal(valuePart);
                                }
                            }
                        } catch (Exception e) {
                            log.warn("Не удалось получить среднюю цену для позиции {}: {}", position.getFigi(), e.getMessage());
                            avgPrice = BigDecimal.ZERO;
                        }
                    }
                    
                    if (position.getCurrentPrice() != null) {
                        try {
                            if (position.getCurrentPrice() instanceof ru.tinkoff.piapi.core.models.Money) {
                                ru.tinkoff.piapi.core.models.Money currentMoney = (ru.tinkoff.piapi.core.models.Money) position.getCurrentPrice();
                                currentPrice = currentMoney.getValue();
                            } else {
                                // Фоллбек на парсинг строки
                                String currentPriceStr = position.getCurrentPrice().toString();
                                if (currentPriceStr.contains("value=")) {
                                    String valuePart = currentPriceStr.substring(currentPriceStr.indexOf("value=") + 6);
                                    valuePart = valuePart.substring(0, valuePart.indexOf(","));
                                    currentPrice = new BigDecimal(valuePart);
                                }
                            }
                        } catch (Exception e) {
                            log.warn("Не удалось получить текущую цену для позиции {}: {}", position.getFigi(), e.getMessage());
                            currentPrice = BigDecimal.ZERO;
                        }
                    }
                    
                    if (avgPrice.compareTo(BigDecimal.ZERO) > 0 && currentPrice.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal pnl = currentPrice.subtract(avgPrice).multiply(quantity);
                        BigDecimal pnlPercent = pnl.divide(avgPrice.multiply(quantity), 4, java.math.RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100));
                        
                        pos.put("pnl", pnl);
                        pos.put("pnlPercent", pnlPercent);
                    } else {
                        pos.put("pnl", BigDecimal.ZERO);
                        pos.put("pnlPercent", BigDecimal.ZERO);
                    }
                    
                    // Уровень риска
                    String riskLevel = getPositionRiskLevel(position);
                    pos.put("riskLevel", riskLevel);
                    
                    return pos;
                })
                .collect(Collectors.toList());
            
            response.put("positions", positions);
            response.put("totalPositions", positions.size());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при получении активных позиций: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("Ошибка при получении активных позиций: " + e.getMessage());
        }
    }
    
    /**
     * Получение последних событий
     */
    @GetMapping("/recent-events")
    public ResponseEntity<?> getRecentEvents() {
        try {
            Map<String, Object> response = new HashMap<>();
            
            // Получаем последние записи из лога
            List<BotLogService.BotLogEntry> recentEntries = botLogService.getRecentLogEntries(10);
            
            List<Map<String, Object>> events = recentEntries.stream()
                .map(entry -> {
                    Map<String, Object> event = new HashMap<>();
                    event.put("timestamp", entry.getFormattedTimestamp());
                    event.put("level", entry.getLevel().name());
                    event.put("category", entry.getCategory().name());
                    event.put("message", entry.getMessage());
                    event.put("details", entry.getDetails());
                    event.put("levelIcon", entry.getLevelIcon());
                    event.put("categoryIcon", entry.getCategoryIcon());
                    return event;
                })
                .collect(Collectors.toList());
            
            response.put("events", events);
            response.put("totalEvents", events.size());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при получении последних событий: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("Ошибка при получении последних событий: " + e.getMessage());
        }
    }
    
    /**
     * Получение статуса автоматической торговли
     */
    @GetMapping("/trading-status")
    public ResponseEntity<?> getTradingStatus() {
        try {
            Map<String, Object> response = new HashMap<>();
            
            // Статус различных процессов
            Map<String, Object> processes = new HashMap<>();
            processes.put("quickAnalysis", Map.of("status", "ACTIVE", "frequency", "30 сек"));
            processes.put("fullAnalysis", Map.of("status", "ACTIVE", "frequency", "2 мин"));
            processes.put("positionWatch", Map.of("status", "ACTIVE", "frequency", "15 сек"));
            processes.put("riskMonitor", Map.of("status", "ACTIVE", "frequency", "1 мин"));
            
            response.put("processes", processes);
            response.put("lastUpdate", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при получении статуса торговли: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("Ошибка при получении статуса торговли: " + e.getMessage());
        }
    }
    
    /**
     * Получение настроек системы
     */
    @GetMapping("/settings")
    public ResponseEntity<?> getSettings() {
        try {
            Map<String, Object> response = new HashMap<>();
            
            // Маржинальная торговля
            Map<String, Object> marginSettings = new HashMap<>();
            marginSettings.put("maxUtilization", marginService.getMaxUtilizationPct().multiply(BigDecimal.valueOf(100)) + "%");
            marginSettings.put("safety", marginService.getSafetyPct().multiply(BigDecimal.valueOf(100)) + "%");
            marginSettings.put("maxShort", marginService.getMaxShortPct().multiply(BigDecimal.valueOf(100)) + "%");
            response.put("marginSettings", marginSettings);
            
            // Риск-менеджмент
            Map<String, Object> riskSettings = new HashMap<>();
            riskSettings.put("maxPositionSize", "5%");
            riskSettings.put("dailyLossLimit", "2%");
            riskSettings.put("stopLossPct", "5%");
            response.put("riskSettings", riskSettings);
            
            // Ребалансировка
            Map<String, Object> rebalancingSettings = new HashMap<>();
            rebalancingSettings.put("threshold", "5%");
            rebalancingSettings.put("frequency", "Ежедневно");
            rebalancingSettings.put("minAmount", "1,000 ₽");
            response.put("rebalancingSettings", rebalancingSettings);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при получении настроек: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("Ошибка при получении настроек: " + e.getMessage());
        }
    }
    
    /**
     * Получение полной информации дашборда
     */
    @GetMapping("/full-status/{accountId}")
    public ResponseEntity<?> getFullDashboardStatus(@PathVariable String accountId) {
        try {
            Map<String, Object> response = new HashMap<>();
            
            // Объединяем все данные
            response.put("systemStatus", getSystemStatus().getBody());
            response.put("marketConditions", getMarketConditions().getBody());
            response.put("riskAnalysis", getRiskAnalysis(accountId).getBody());
            response.put("activePositions", getActivePositions(accountId).getBody());
            response.put("recentEvents", getRecentEvents().getBody());
            response.put("tradingStatus", getTradingStatus().getBody());
            response.put("settings", getSettings().getBody());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при получении полного статуса дашборда: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("Ошибка при получении полного статуса дашборда: " + e.getMessage());
        }
    }
    
    /**
     * Последние совершенные сделки (исполненные ордера)
     */
    @GetMapping("/recent-trades")
    public ResponseEntity<?> getRecentTrades(@RequestParam(value = "accountId", required = false) String accountId,
                                             @RequestParam(value = "limit", required = false, defaultValue = "10") int limit) {
        try {
            // Получаем последние записи из репозитория orders по дате
            List<Order> all = accountId == null || accountId.isEmpty()
                    ? orderRepository.findAll()
                    : orderRepository.findByAccountId(accountId);
            // Фильтруем только исполненные/частично исполненные
            List<Order> executed = all.stream()
                    .filter(o -> o.getStatus() != null && (o.getStatus().contains("EXECUTION_REPORT_STATUS_FILL")
                            || o.getStatus().contains("EXECUTION_REPORT_STATUS_PARTIALLY_FILLED")))
                    .sorted(java.util.Comparator.comparing(Order::getOrderDate).reversed())
                    .limit(limit)
                    .toList();

            List<java.util.Map<String, Object>> trades = executed.stream().map(o -> {
                java.util.Map<String, Object> t = new java.util.HashMap<>();
                t.put("orderId", o.getOrderId());
                t.put("figi", o.getFigi());
                t.put("operation", o.getOperation());
                t.put("status", o.getStatus());
                t.put("lotsRequested", o.getRequestedLots());
                t.put("lotsExecuted", o.getExecutedLots());
                t.put("price", o.getPrice());
                t.put("currency", o.getCurrency());
                t.put("orderDate", o.getOrderDate());
                t.put("orderType", o.getOrderType());
                return t;
            }).toList();

            return ResponseEntity.ok(java.util.Map.of("trades", trades));
        } catch (Exception e) {
            log.error("Ошибка при получении последних сделок: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("Ошибка при получении последних сделок: " + e.getMessage());
        }
    }
    
    // Вспомогательные методы
    private String getMarketConditionDisplay(String condition) {
        return switch (condition) {
            case "BULL_MARKET" -> "Бычий рынок";
            case "BEAR_MARKET" -> "Медвежий рынок";
            case "SIDEWAYS_MARKET" -> "Боковой рынок";
            default -> "Неизвестно";
        };
    }
    
    private String getRiskLevelDisplay(String riskLevel) {
        return switch (riskLevel) {
            case "LOW" -> "Низкий";
            case "MEDIUM" -> "Средний";
            case "HIGH" -> "Высокий";
            default -> "Неизвестно";
        };
    }
    
    private String getPositionRiskLevel(ru.tinkoff.piapi.core.models.Position position) {
        // Упрощенная логика определения риска позиции
        BigDecimal quantity = position.getQuantity();
        if (quantity.compareTo(BigDecimal.ZERO) == 0) {
            return "LOW";
        }
        
        // Можно добавить более сложную логику на основе размера позиции
        return "MEDIUM";
    }
}
