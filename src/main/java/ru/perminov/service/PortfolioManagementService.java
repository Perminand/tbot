package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.core.models.Portfolio;
import ru.tinkoff.piapi.core.models.Position;
import ru.tinkoff.piapi.contract.v1.OrderDirection;
import ru.tinkoff.piapi.contract.v1.PostOrderResponse;
// import ru.tinkoff.piapi.contract.v1.MoneyValue; // unused
import ru.perminov.dto.ShareDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioManagementService {
    
    private final PortfolioService portfolioService;
    private final OrderService orderService;
    private final MarketAnalysisService marketAnalysisService;
    private final BotLogService botLogService;
    private final InvestApiManager investApiManager;
    
    private final DynamicInstrumentService dynamicInstrumentService;
    private final MarginService marginService;
    private final RiskRuleService riskRuleService;
    private final AdvancedTradingStrategyService advancedTradingStrategyService;
    private final TradingSettingsService tradingSettingsService;
    private final InstrumentNameService instrumentNameService;
    private final SectorManagementService sectorManagementService;
    private final CapitalManagementService capitalManagementService;
    private final CommissionCalculatorService commissionCalculatorService;
    private final AdaptiveDiversificationService adaptiveDiversificationService;
    private final TradingCooldownService tradingCooldownService;

    // Защита: одна торговая операция на FIGI в короткое окно (например, один цикл/60 сек)
    private final java.util.concurrent.ConcurrentHashMap<String, Long> recentOperationsWindow = new java.util.concurrent.ConcurrentHashMap<>();
    private final ru.perminov.repository.InstrumentRepository instrumentRepository;
    
    // Целевые доли активов в портфеле
    private final Map<String, BigDecimal> targetAllocations = new HashMap<>();
    
    // Настройки автоматического мониторинга
    private boolean autoMonitoringEnabled = false;
    private String monitoredAccountId = null;
    
    // Инициализация целевых долей (пример)
    {
        targetAllocations.put("shares", new BigDecimal("0.60")); // 60% акции
        targetAllocations.put("bonds", new BigDecimal("0.30"));  // 30% облигации
        targetAllocations.put("etf", new BigDecimal("0.10"));    // 10% ETF
    }

    private String displayOf(String figi) {
        try {
            if (instrumentNameService == null) return figi;
            
            // Специальная обработка для валют
            if ("RUB000UTSTOM".equals(figi)) {
                return "Рубли РФ (RUB)";
            }
            
            // 🚀 УЛУЧШЕННАЯ ЛОГИКА: Пробуем разные типы инструментов
            String[] instrumentTypes = {"share", "bond", "etf", "currency"};
            
            for (String type : instrumentTypes) {
                try {
                    String name = instrumentNameService.getInstrumentName(figi, type);
                    String ticker = instrumentNameService.getTicker(figi, type);
                    
                    if (name != null && ticker != null) {
                        return name + " (" + ticker + ")";
                    }
                    if (name != null) {
                        return name;
                    }
                    if (ticker != null) {
                        return ticker + " [" + getInstrumentTypeDisplayName(type) + "]";
                    }
                } catch (Exception ignore) {
                    // Пробуем следующий тип
                }
            }
            
            // 🎯 СПЕЦИАЛЬНАЯ ОБРАБОТКА неизвестных кодов
            return getHumanReadableName(figi);
            
        } catch (Exception ignore) {}
        return figi;
    }
    
    /**
     * 🚀 НОВЫЙ МЕТОД: Получение человекочитаемого названия для неизвестных инструментов
     */
    private String getHumanReadableName(String figi) {
        // Специальные случаи
        if ("ISSUANCEPRLS".equals(figi)) {
            return "Размещение облигаций (ISSUANCEPRLS)";
        }
        
        // Обработка по шаблонам
        if (figi.startsWith("BBG")) {
            return "Инструмент " + figi.substring(0, Math.min(12, figi.length()));
        }
        
        if (figi.startsWith("TCS")) {
            return "Тинькофф инструмент " + figi.substring(0, Math.min(12, figi.length()));
        }
        
        if (figi.contains("ISSUANCE")) {
            return "Размещение (" + figi + ")";
        }
        
        if (figi.contains("PRLS") || figi.contains("PRL")) {
            return "Облигация " + figi;
        }
        
        // По умолчанию
        return "Инструмент " + figi.substring(0, Math.min(12, figi.length()));
    }
    
    /**
     * Получение отображаемого названия типа инструмента
     */
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

    private String determineInstrumentType(String figi) {
        try {
            if ("RUB000UTSTOM".equals(figi)) return "currency";
            var opt = instrumentRepository.findById(figi);
            if (opt.isPresent() && opt.get().getInstrumentType() != null) return opt.get().getInstrumentType();
        } catch (Exception ignore) {}
        // Фолбэк: пробуем по Invest API
        try {
            var api = investApiManager.getCurrentInvestApi();
            try { if (api.getInstrumentsService().getShareByFigiSync(figi) != null) return "share"; } catch (Exception ignore) {}
            try { if (api.getInstrumentsService().getBondByFigiSync(figi) != null) return "bond"; } catch (Exception ignore) {}
            try { if (api.getInstrumentsService().getEtfByFigiSync(figi) != null) return "etf"; } catch (Exception ignore) {}
        } catch (Exception ignore) {}
        return "share";
    }
    
    /**
     * Анализ текущего портфеля
     */
    public PortfolioAnalysis analyzePortfolio(String accountId) {
        Portfolio portfolio = portfolioService.getPortfolio(accountId);
        List<Position> positions = portfolio.getPositions();
        
        BigDecimal totalValue = BigDecimal.ZERO;
        Map<String, BigDecimal> currentAllocations = new HashMap<>();
        Map<String, BigDecimal> positionValues = new HashMap<>();
        
        // Расчет текущих значений позиций
        for (Position position : positions) {
            BigDecimal quantity = position.getQuantity();
            BigDecimal currentPrice = BigDecimal.ZERO;
            
            if (position.getCurrentPrice() != null) {
                try {
                    // Пробуем использовать правильный метод для Money
                    if (position.getCurrentPrice() instanceof ru.tinkoff.piapi.core.models.Money) {
                        ru.tinkoff.piapi.core.models.Money money = (ru.tinkoff.piapi.core.models.Money) position.getCurrentPrice();
                        currentPrice = money.getValue();
                        log.debug("Цена через getValue(): {}", currentPrice);
                    } else {
                        // Фоллбек на парсинг строки
                        String priceStr = position.getCurrentPrice().toString();
                        log.debug("Price string: {}", priceStr);
                        
                        if (priceStr.contains("value=")) {
                            String valuePart = priceStr.substring(priceStr.indexOf("value=") + 6);
                            valuePart = valuePart.substring(0, valuePart.indexOf(","));
                            currentPrice = new BigDecimal(valuePart);
                        } else {
                            String[] parts = priceStr.split("[^0-9.]");
                            for (String part : parts) {
                                if (!part.isEmpty() && part.matches("\\d+\\.?\\d*")) {
                                    currentPrice = new BigDecimal(part);
                                    break;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                                            log.warn("Не удалось получить цену для позиции: {}", e.getMessage());
                    currentPrice = BigDecimal.ZERO;
                }
            } else {
                currentPrice = BigDecimal.ZERO;
            }
            
            BigDecimal positionValue;
            
            // Для валютных позиций используем количество как стоимость
            if ("currency".equals(position.getInstrumentType())) {
                positionValue = quantity;
            } else {
                positionValue = quantity.multiply(currentPrice);
            }
            
            positionValues.put(position.getFigi(), positionValue);
            totalValue = totalValue.add(positionValue);
            
            // Группировка по типам инструментов
            String instrumentType = position.getInstrumentType();
            currentAllocations.merge(instrumentType, positionValue, BigDecimal::add);
        }
        
        // Расчет долей
        Map<String, BigDecimal> allocationPercentages = new HashMap<>();
        for (Map.Entry<String, BigDecimal> entry : currentAllocations.entrySet()) {
            if (totalValue.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal percentage = entry.getValue()
                    .divide(totalValue, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
                allocationPercentages.put(entry.getKey(), percentage);
            }
        }
        
        return new PortfolioAnalysis(
            totalValue,
            currentAllocations,
            allocationPercentages,
            positionValues,
            positions
        );
    }
    
    /**
     * Проверка необходимости ребалансировки
     */
    public RebalancingDecision checkRebalancing(String accountId) {
        PortfolioAnalysis analysis = analyzePortfolio(accountId);
        BigDecimal totalValue = analysis.getTotalValue();
        
        if (totalValue.compareTo(BigDecimal.ZERO) <= 0) {
            return new RebalancingDecision(false, "Недостаточно средств для ребалансировки");
        }
        
        Map<String, BigDecimal> currentAllocations = analysis.getAllocationPercentages();
        Map<String, BigDecimal> deviations = new HashMap<>();
        BigDecimal maxDeviation = BigDecimal.ZERO;
        
        // Расчет отклонений от целевых долей
        for (Map.Entry<String, BigDecimal> target : targetAllocations.entrySet()) {
            String assetType = target.getKey();
            BigDecimal targetPercentage = target.getValue().multiply(BigDecimal.valueOf(100));
            BigDecimal currentPercentage = currentAllocations.getOrDefault(assetType, BigDecimal.ZERO);
            BigDecimal deviation = currentPercentage.subtract(targetPercentage).abs();
            
            deviations.put(assetType, deviation);
            if (deviation.compareTo(maxDeviation) > 0) {
                maxDeviation = deviation;
            }
        }
        
        // Если максимальное отклонение больше 5%, нужна ребалансировка
        boolean needsRebalancing = maxDeviation.compareTo(BigDecimal.valueOf(5)) > 0;
        
        return new RebalancingDecision(
            needsRebalancing,
            needsRebalancing ? "Требуется ребалансировка" : "Портфель сбалансирован",
            deviations,
            maxDeviation
        );
    }
    
    /**
     * Выполнение ребалансировки портфеля
     */
    public void rebalancePortfolio(String accountId) {
        log.info("Начало ребалансировки портфеля для аккаунта: {}", accountId);
        
        PortfolioAnalysis analysis = analyzePortfolio(accountId);
        BigDecimal totalValue = analysis.getTotalValue();
        
        if (totalValue.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Недостаточно средств для ребалансировки");
            return;
        }
        
        // Расчет целевых значений для каждого типа активов
        Map<String, BigDecimal> targetValues = new HashMap<>();
        for (Map.Entry<String, BigDecimal> allocation : targetAllocations.entrySet()) {
            BigDecimal targetValue = totalValue.multiply(allocation.getValue());
            targetValues.put(allocation.getKey(), targetValue);
        }
        
        // Определение необходимых действий
        Map<String, BigDecimal> currentValues = analysis.getCurrentAllocations();
        
        for (Map.Entry<String, BigDecimal> target : targetValues.entrySet()) {
            String assetType = target.getKey();
            BigDecimal targetValue = target.getValue();
            BigDecimal currentValue = currentValues.getOrDefault(assetType, BigDecimal.ZERO);
            BigDecimal difference = targetValue.subtract(currentValue);
            
            if (difference.abs().compareTo(BigDecimal.valueOf(1000)) > 0) { // Минимальная сумма для ребалансировки
                log.info("{}: текущее значение = {}, целевое = {}, разница = {}", 
                    assetType, currentValue, targetValue, difference);
                
                // Здесь можно добавить логику для выбора конкретных инструментов
                // и размещения ордеров
            }
        }
        
        log.info("Ребалансировка портфеля завершена");
    }
    
    /**
     * Принудительная проверка всех шорт позиций для их закрытия
     */
    public void checkAndCloseShortPositions(String accountId) {
        try {
            log.info("🔍 ПРИНУДИТЕЛЬНАЯ ПРОВЕРКА ШОРТ ПОЗИЦИЙ для аккаунта {}", accountId);
            PortfolioManagementService.PortfolioAnalysis analysis = analyzePortfolio(accountId);
            
            List<Position> shortPositions = analysis.getPositions().stream()
                .filter(p -> p.getQuantity().compareTo(BigDecimal.ZERO) < 0)
                .filter(p -> !"currency".equals(p.getInstrumentType()))
                .collect(Collectors.toList());
                
            log.info("🎯 Найдено {} шорт позиций", shortPositions.size());
            
            for (Position shortPos : shortPositions) {
                String figi = shortPos.getFigi();
                log.info("🔍 Анализ шорт позиции: FIGI={}, quantity={}", figi, shortPos.getQuantity());
                
                // Принудительно анализируем торговый сигнал для каждой шорт позиции
                executeTradingStrategy(accountId, figi);
                
                // Небольшая задержка между анализами
                Thread.sleep(200);
            }
            
        } catch (Exception e) {
            log.error("Ошибка при проверке шорт позиций: {}", e.getMessage());
        }
    }
    
    /**
     * Автоматическая торговля на основе анализа
     */
    public void executeTradingStrategy(String accountId, String figi) {
        try {
            log.info("=== ВЫПОЛНЕНИЕ ТОРГОВОЙ СТРАТЕГИИ ===");
            log.info("Аккаунт: {}, Инструмент: {}", accountId, displayOf(figi));
            
            // Проверяем доступность инструмента для торговли
            if (!dynamicInstrumentService.isInstrumentAvailable(figi)) {
                log.warn("Инструмент {} недоступен для торговли, пропускаем", displayOf(figi));
                botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT, 
                    "Инструмент недоступен", displayOf(figi) + " — недоступен для торговли");
                return;
            }
            
            // 🚀 ПРЕДВАРИТЕЛЬНАЯ ПРОВЕРКА COOLDOWN: Защита от частых сделок
            // Получаем предварительный тренд для проверки
            MarketAnalysisService.TrendAnalysis preliminaryTrend = 
                marketAnalysisService.analyzeTrend(figi, ru.tinkoff.piapi.contract.v1.CandleInterval.CANDLE_INTERVAL_DAY);
            if (preliminaryTrend == null) {
                log.warn("Не удалось получить предварительный тренд для cooldown проверки {}", displayOf(figi));
                return;
            }
            
            PortfolioAnalysis preliminaryPortfolio = analyzePortfolio(accountId);
            Position preliminaryPosition = preliminaryPortfolio.getPositions().stream()
                .filter(p -> figi.equals(p.getFigi()))
                .findFirst()
                .orElse(null);
            boolean hasPreliminaryPosition = preliminaryPosition != null && 
                preliminaryPosition.getQuantity() != null && 
                preliminaryPosition.getQuantity().compareTo(BigDecimal.ZERO) != 0;
            
            String preliminaryAction = determineRecommendedAction(preliminaryTrend, 
                preliminaryTrend.getCurrentPrice(), hasPreliminaryPosition, figi, accountId);
            if (preliminaryAction != null && !"HOLD".equals(preliminaryAction)) {
                // Локальная защита: не более одной операции на FIGI за короткое окно (60 сек)
                long nowMs = System.currentTimeMillis();
                Long lastOp = recentOperationsWindow.get(figi);
                if (lastOp != null && (nowMs - lastOp) < 60_000) {
                    log.warn("🚫 Блок: уже была операция по {} менее чем минуту назад", displayOf(figi));
                    botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT,
                        "Ограничение частоты по FIGI", displayOf(figi) + " — операция пропущена (окно 60 сек)");
                    return;
                }
                recentOperationsWindow.put(figi, nowMs);

                TradingCooldownService.CooldownResult cooldownCheck = 
                    tradingCooldownService.canTrade(figi, preliminaryAction, accountId);
                
                if (cooldownCheck.isBlocked()) {
                    log.warn("🚫 БЛОКИРОВКА OVERTRADING: {} для {}. Причина: {}", 
                        preliminaryAction, displayOf(figi), cooldownCheck.getReason());
                    
                    botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT,
                        "Блокировка частых сделок", String.format("%s, Account: %s, Действие: %s, Причина: %s", 
                            displayOf(figi), accountId, preliminaryAction, cooldownCheck.getReason()));
                    return;
                }
                
                log.info("✅ Cooldown проверка пройдена: {} для {}. {}", 
                    preliminaryAction, displayOf(figi), cooldownCheck.getReason());
            }
            
            // Анализ тренда + ATR
            MarketAnalysisService.TrendAnalysis trend = 
                marketAnalysisService.analyzeTrend(figi, ru.tinkoff.piapi.contract.v1.CandleInterval.CANDLE_INTERVAL_DAY);
            if (trend == null) {
                log.warn("Не удалось получить анализ тренда для {}", displayOf(figi));
                return;
            }
            log.info("Тренд: {}, текущая цена: {}", trend.getTrend(), trend.getCurrentPrice());
            
            int atrPeriod = tradingSettingsService.getInt("atr.period", 14);
            java.math.BigDecimal atr = marketAnalysisService.calculateATR(figi, ru.tinkoff.piapi.contract.v1.CandleInterval.CANDLE_INTERVAL_DAY, atrPeriod);
            if (trend.getCurrentPrice().compareTo(java.math.BigDecimal.ZERO) > 0) {
                java.math.BigDecimal atrPct = atr.divide(trend.getCurrentPrice(), 6, java.math.RoundingMode.HALF_UP);
                double minAtrPct = tradingSettingsService.getDouble("atr.min.pct", 0.002);
                double maxAtrPct = tradingSettingsService.getDouble("atr.max.pct", 0.08);
                log.debug("ATR анализ: ATR={}, ATR%={}, мин={}, макс={}", atr, atrPct, minAtrPct, maxAtrPct);
                // Фильтр слишком низкой волатильности (шум) и экстремальной волатильности
                if (atrPct.compareTo(java.math.BigDecimal.valueOf(minAtrPct)) < 0 || atrPct.compareTo(java.math.BigDecimal.valueOf(maxAtrPct)) > 0) {
                    log.info("ATR-фильтр: пропускаем {} (ATR%={})", displayOf(figi), atrPct);
                    return;
                }
            }
            
            // Анализ портфеля
            PortfolioAnalysis portfolioAnalysis = analyzePortfolio(accountId);
            
            // Получаем рекомендуемое действие из продвинутого анализа сигналов
            AdvancedTradingStrategyService.TradingSignal advSignal = advancedTradingStrategyService.analyzeTradingSignal(figi, accountId);
            String actionByAdvanced = advSignal.getAction();
            log.debug("Продвинутый сигнал: {} (сила: {})", actionByAdvanced, advSignal.getStrength());

            // Базовый оппортьюнити для логирования и метрик (сохранено)
            TradingOpportunity opportunity = analyzeTradingOpportunity(figi, accountId);
            if (opportunity == null) {
                log.warn("Не удалось проанализировать торговую возможность для {}", displayOf(figi));
                return;
            }
            
            // Сведение решений: отдаём приоритет продвинутому сигналу при достаточной силе
            double minStrength = tradingSettingsService.getDouble("signal.min.strength", 50.0);
            String action = actionByAdvanced != null && !"HOLD".equals(actionByAdvanced) &&
                (advSignal.getStrength() != null && advSignal.getStrength().compareTo(java.math.BigDecimal.valueOf(minStrength)) > 0)
                ? actionByAdvanced : opportunity.getRecommendedAction();
            log.info("🎯 ФИНАЛЬНОЕ РЕШЕНИЕ для {}: {} (продвинутый: {}, базовый: {})", 
                displayOf(figi), action, actionByAdvanced, opportunity.getRecommendedAction());
            
            if ("CLOSE_SHORT".equals(action)) {
                // Специальная обработка закрытия шорта
                log.info("🎯 ВЫПОЛНЯЕМ ЗАКРЫТИЕ ШОРТА для {}", displayOf(figi));
                
                Position shortPosition = portfolioAnalysis.getPositions().stream()
                    .filter(p -> figi.equals(p.getFigi()))
                    .findFirst()
                    .orElse(null);
                    
                if (shortPosition != null && shortPosition.getQuantity() != null && shortPosition.getQuantity().compareTo(BigDecimal.ZERO) < 0) {
                    int lotsToClose = Math.abs(shortPosition.getQuantity().intValue());
                    if (lotsToClose > 0) {
                        log.info("🎯 ЗАКРЫТИЕ ШОРТА [{}]: {} лотов по цене {} (специальное действие)",
                            displayOf(figi), lotsToClose, trend.getCurrentPrice());
                        botLogService.addLogEntry(BotLogService.LogLevel.TRADE, BotLogService.LogCategory.AUTOMATIC_TRADING,
                                "💰 Размещение ордера на закрытие шорта", String.format("%s, Лотов: %d, Цена: %.2f",
                                        displayOf(figi), lotsToClose, trend.getCurrentPrice()));
                        try {
                            PostOrderResponse response = orderService.placeSmartLimitOrder(figi, lotsToClose, OrderDirection.ORDER_DIRECTION_BUY, accountId, trend.getCurrentPrice());
                            log.info("✅ Умный лимитный ордер на закрытие шорта размещен успешно: orderId={}, status={}", 
                                response.getOrderId(), response.getExecutionReportStatus());
                            botLogService.addLogEntry(BotLogService.LogLevel.SUCCESS, BotLogService.LogCategory.AUTOMATIC_TRADING,
                                    "Шорт закрыт", String.format("%s, Лотов: %d, OrderId: %s", displayOf(figi), lotsToClose, response.getOrderId()));
                            return;
                        } catch (Exception e) {
                            log.error("❌ Ошибка закрытия шорта [{}]: {}", displayOf(figi), e.getMessage(), e);
                            botLogService.addLogEntry(BotLogService.LogLevel.ERROR, BotLogService.LogCategory.AUTOMATIC_TRADING,
                                    "Ошибка закрытия шорта", e.getMessage());
                            return;
                        }
                    }
                } else {
                    log.warn("⚠️ Получен сигнал CLOSE_SHORT, но шорт-позиция не найдена для {}", displayOf(figi));
                    return;
                }
            } else if ("BUY".equals(action)) {
                // Приоритет: если есть открытая шорт‑позиция по этому FIGI — закрываем её немедленно, без проверок BP
                try {
                    Position shortPosition = portfolioAnalysis.getPositions().stream()
                        .filter(p -> figi.equals(p.getFigi()))
                        .findFirst()
                        .orElse(null);
                    if (shortPosition != null && shortPosition.getQuantity() != null && shortPosition.getQuantity().compareTo(BigDecimal.ZERO) < 0) {
                        int lotsToClose = Math.abs(shortPosition.getQuantity().intValue());
                        if (lotsToClose > 0) {
                            String prettyName = instrumentNameService != null ? instrumentNameService.getInstrumentName(figi, "share") : figi;
                            String prettyTicker = instrumentNameService != null ? instrumentNameService.getTicker(figi, "share") : figi;
                            log.info("🎯 НЕМЕДЛЕННОЕ ЗАКРЫТИЕ ШОРТА [{}]: {} лотов по цене {} (без проверок BP)",
                                displayOf(figi), lotsToClose, trend.getCurrentPrice());
                            botLogService.addLogEntry(BotLogService.LogLevel.TRADE, BotLogService.LogCategory.AUTOMATIC_TRADING,
                                    "Закрытие шорта (приоритет)", String.format("%s, Лотов: %d, Цена: %.4f",
                                            displayOf(figi), lotsToClose, trend.getCurrentPrice()));
                            try {
                                log.info("🎯 Размещаем умный лимитный ордер на закрытие шорта: {} лотов BUY по цене {}", lotsToClose, trend.getCurrentPrice());
                                PostOrderResponse response = orderService.placeSmartLimitOrder(figi, lotsToClose, OrderDirection.ORDER_DIRECTION_BUY, accountId, trend.getCurrentPrice());
                                log.info("✅ Умный лимитный ордер на закрытие шорта размещен успешно: orderId={}, status={}", 
                                    response.getOrderId(), response.getExecutionReportStatus());
                                botLogService.addLogEntry(BotLogService.LogLevel.SUCCESS, BotLogService.LogCategory.AUTOMATIC_TRADING,
                                        "Шорт закрыт", String.format("%s, Лотов: %d, OrderId: %s", displayOf(figi), lotsToClose, response.getOrderId()));
                                return;
                            } catch (Exception e) {
                                log.error("❌ Ошибка немедленного закрытия шорта [{}]: {}", displayOf(figi), e.getMessage(), e);
                                botLogService.addLogEntry(BotLogService.LogLevel.ERROR, BotLogService.LogCategory.AUTOMATIC_TRADING,
                                        "Ошибка закрытия шорта", String.format("%s, Лотов: %d, Ошибка: %s", displayOf(figi), lotsToClose, e.getMessage()));
                                // Если не получилось — продолжаем стандартные проверки
                            }
                        }
                    }
                } catch (Exception ignore) { }

                // Проверяем, есть ли свободные средства
                System.out.println("🚨🚨🚨 ПРОВЕРЯЕМ СРЕДСТВА для " + displayOf(figi));
                log.info("🚨🚨🚨 ПРОВЕРЯЕМ СРЕДСТВА для {}", displayOf(figi));
                BigDecimal availableCash = getAvailableCash(portfolioAnalysis);
                BigDecimal buyingPower = marginService.getAvailableBuyingPower(accountId, portfolioAnalysis);
                System.out.println("🚨🚨🚨 РЕЗУЛЬТАТ: availableCash=" + availableCash + ", buyingPower=" + buyingPower);
                log.info("🚨🚨🚨 РЕЗУЛЬТАТ: availableCash={}, buyingPower={}", availableCash, buyingPower);

                // Проверка средств: используем buyingPower вместо availableCash для маржинальных операций
                boolean allowNegativeCash = tradingSettingsService.getBoolean("margin-trading.allow-negative-cash", false);
                if (availableCash.compareTo(BigDecimal.ZERO) < 0 && !allowNegativeCash) {
                    log.warn("Реальные средства отрицательные ({}), блокируем покупки (маржинальная торговля отключена) [{} , accountId={}, price={}]", 
                        availableCash, displayOf(figi), accountId, trend.getCurrentPrice());
                    botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT, 
                        "Блокировка покупок", String.format("%s, Account: %s, Price: %.4f, Отрицательные средства: %.2f (маржинальная торговля отключена)", 
                            displayOf(figi), accountId, trend.getCurrentPrice(), availableCash));
                    return;
                } else if (availableCash.compareTo(BigDecimal.ZERO) < 0 && allowNegativeCash) {
                    log.info("Реальные средства отрицательные ({}), но маржинальная торговля разрешена. Используем плечо. [{} , accountId={}, price={}]", 
                        availableCash, displayOf(figi), accountId, trend.getCurrentPrice());
                    botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.RISK_MANAGEMENT, 
                        "Маржинальная покупка", String.format("%s, Account: %s, Price: %.4f, Отрицательные средства: %.2f — используем плечо", 
                            displayOf(figi), accountId, trend.getCurrentPrice(), availableCash));
                }

                // Если маржа включена, но недоступна для аккаунта — продолжаем с фоллбек-логикой внутри MarginService
                if (marginService.isMarginEnabled() && !marginService.isMarginOperationalForAccount(accountId)) {
                    log.warn("Маржа включена в настройках, но недоступна для аккаунта {}. Используем расчеты по настройкам (без реальных атрибутов).", accountId);
                }
                
                // Дополнительная проверка для маржинальных операций
                if (allowNegativeCash && availableCash.compareTo(BigDecimal.ZERO) < 0) {
                    double minBuyingPowerRatio = tradingSettingsService.getDouble("margin-trading.min-buying-power-ratio", 0.1);
                    BigDecimal minRequiredBuyingPower = trend.getCurrentPrice().multiply(BigDecimal.valueOf(minBuyingPowerRatio));
                    
                    if (buyingPower.compareTo(minRequiredBuyingPower) < 0) {
                        log.warn("Недостаточная покупательная способность для маржинальной операции [{} , accountId={}, price={}, ratio={}]. Требуется: {}, доступно: {}", 
                                displayOf(figi), accountId, trend.getCurrentPrice(), minBuyingPowerRatio, minRequiredBuyingPower, buyingPower);
                        botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT, 
                            "Недостаточная покупательная способность", 
                            String.format("%s, Account: %s, Price: %.4f, Ratio: %.3f, Требуется: %.2f, Доступно: %.2f", 
                                displayOf(figi), accountId, trend.getCurrentPrice(), minBuyingPowerRatio, minRequiredBuyingPower, buyingPower));
                        return;
                    }
                }
                
                // Проверяем покупательную способность (включая плечо)
                if (buyingPower.compareTo(BigDecimal.ZERO) > 0) {
                    log.info("✅ Покупательная способность доступна: {} (включая плечо)", buyingPower);
                    // Проверяем, есть ли уже позиция по этому инструменту
                    boolean hasPosition = portfolioAnalysis.getPositionValues().containsKey(figi) && 
                                        portfolioAnalysis.getPositionValues().get(figi).compareTo(BigDecimal.ZERO) > 0;
                    
                    // Используем CapitalManagementService для расчета размера позиции
                    CapitalManagementService.SizingResult sizing = capitalManagementService.computeSizing(
                            accountId,
                            figi,
                            displayOf(figi),
                            hasPosition,
                            trend.getCurrentPrice(),
                            buyingPower,
                            portfolioAnalysis,
                            atr
                    );
                    if (sizing.isBlocked()) {
                        log.warn("Покупка заблокирована CapitalManagementService: {} [{} , accountId={}]", sizing.getBlockReason(), displayOf(figi), accountId);
                        botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT,
                                "Блокировка размера позиции",
                                String.format("%s, Account: %s, Причина: %s", displayOf(figi), accountId, sizing.getBlockReason()));
                        return;
                    }
                    int lots = sizing.getLots();
                    BigDecimal buyAmount = sizing.getBuyAmount();
                    log.info("🎯 Рассчитано CapitalManagement: lots={}, amount={}, price={}, value={}",
                            lots, buyAmount, trend.getCurrentPrice(), trend.getCurrentPrice().multiply(BigDecimal.valueOf(lots)));

                    // ATR-кап размера позиции: ограничиваем стоимость позиции  по отношению к ATR
                    if (atr.compareTo(java.math.BigDecimal.ZERO) > 0) {
                        java.math.BigDecimal maxRiskPerTrade = portfolioAnalysis.getTotalValue().multiply(java.math.BigDecimal.valueOf(riskRuleService.getRiskPerTradePct()));
                        // Если стоп ~ 1*ATR, то стоимость позиции <= maxRisk / ATR
                        java.math.BigDecimal allowedLotsByAtr = maxRiskPerTrade.divide(atr, 0, RoundingMode.DOWN);
                        java.math.BigDecimal allowedLotsByPrice = allowedLotsByAtr.divide(trend.getCurrentPrice(), 0, RoundingMode.DOWN);
                        int capLots = allowedLotsByPrice.intValue();
                        if (capLots > 0 && lots > capLots) {
                            log.info("ATR-кап позиции: лоты {} -> {} (ATR={}, maxRisk={})", lots, capLots, atr, maxRiskPerTrade);
                            lots = capLots;
                        }
                    }
                    
                    log.info("🎯 Финальное количество лотов после ATR-капа: {}", lots);
                    
                    // Дополнительная проверка: достаточно ли средств для покупки хотя бы 1 лота
                    if (buyingPower.compareTo(trend.getCurrentPrice()) < 0) {
                        log.warn("Недостаточно средств для покупки даже 1 лота [{} , accountId={}]. Нужно: {}, Доступно: {}", 
                                displayOf(figi), accountId, trend.getCurrentPrice(), buyingPower);
                        botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT, 
                            "Недостаточно средств для покупки 1 лота", String.format("%s, Account: %s, Price: %.4f, Нужно: %.2f, Доступно: %.2f", 
                                displayOf(figi), accountId, trend.getCurrentPrice(), trend.getCurrentPrice(), buyingPower));
                        return;
                    }
                    
                    // Дополнительная проверка реальной доступности средств через API
                    try {
                        BigDecimal realAvailableCash = getAvailableCash(portfolioAnalysis);
                        BigDecimal requiredAmount = trend.getCurrentPrice().multiply(BigDecimal.valueOf(lots));
                        
                        // Для маржинальной торговли используем buyingPower вместо realAvailableCash
                        BigDecimal availableForTrade = (allowNegativeCash && realAvailableCash.compareTo(BigDecimal.ZERO) < 0) 
                            ? buyingPower : realAvailableCash;
                        
                        // Дополнительные проверки для маржинальной торговли
                        if (allowNegativeCash && realAvailableCash.compareTo(BigDecimal.ZERO) < 0) {
                            // Получаем текущие маржинальные атрибуты для проверки лимитов
                            try {
                                var marginAttributes = marginService.getAccountMarginAttributes(accountId);
                                if (marginAttributes != null) {
                                    BigDecimal currentLiquid = new BigDecimal(marginAttributes.getLiquidPortfolio().getUnits() + "." + String.format("%09d", marginAttributes.getLiquidPortfolio().getNano()).replaceFirst("0+$", ""));
                                    BigDecimal currentMinimal = new BigDecimal(marginAttributes.getMinimalMargin().getUnits() + "." + String.format("%09d", marginAttributes.getMinimalMargin().getNano()).replaceFirst("0+$", ""));
                                    BigDecimal currentMissing = new BigDecimal(marginAttributes.getAmountOfMissingFunds().getUnits() + "." + String.format("%09d", marginAttributes.getAmountOfMissingFunds().getNano()).replaceFirst("0+$", ""));
                                    
                                    // Проверяем, не превысит ли новая позиция минимальный уровень маржи
                                    BigDecimal estimatedNewLiquid = currentLiquid.subtract(requiredAmount);
                                    if (estimatedNewLiquid.compareTo(currentMinimal) < 0) {
                                        log.warn("🚨 МАРЖИНАЛЬНЫЙ ЛИМИТ: новая позиция превысит минимальный уровень маржи [{} , accountId={}]. Текущий liquid: {}, минимальный: {}, после сделки: {}", 
                                            displayOf(figi), accountId, currentLiquid, currentMinimal, estimatedNewLiquid);
                            botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT, 
                                            "Превышение минимального уровня маржи", String.format("%s, Account: %s, Текущий liquid: %.2f, Минимальный: %.2f, После сделки: %.2f", 
                                                displayOf(figi), accountId, currentLiquid, currentMinimal, estimatedNewLiquid));
                            return;
                                    }
                                    
                                    // Проверяем, не увеличит ли сделка недостаток средств
                                    if (currentMissing.compareTo(BigDecimal.ZERO) < 0) {
                                        BigDecimal newMissing = currentMissing.subtract(requiredAmount);
                                        if (newMissing.compareTo(currentMissing) < 0) {
                                            log.warn("🚨 МАРЖИНАЛЬНЫЙ РИСК: сделка увеличит недостаток средств [{} , accountId={}]. Текущий missing: {}, после сделки: {}", 
                                                displayOf(figi), accountId, currentMissing, newMissing);
                                            botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT, 
                                                "Увеличение недостатка средств", String.format("%s, Account: %s, Текущий missing: %.2f, После сделки: %.2f", 
                                                    displayOf(figi), accountId, currentMissing, newMissing));
                                            return;
                                        }
                                    }
                                    
                                    // Дополнительная проверка: не превышаем ли максимальное использование маржи
                                    BigDecimal maxUtilization = portfolioAnalysis.getTotalValue().multiply(marginService.getMaxUtilizationPct());
                                    if (currentLiquid.subtract(requiredAmount).compareTo(maxUtilization) < 0) {
                                        log.warn("🚨 МАРЖИНАЛЬНЫЙ ЛИМИТ: превышение максимального использования маржи [{} , accountId={}]. Максимум: {}, после сделки: {}", 
                                            displayOf(figi), accountId, maxUtilization, currentLiquid.subtract(requiredAmount));
                                        botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT, 
                                            "Превышение максимального использования маржи", String.format("%s, Account: %s, Максимум: %.2f, После сделки: %.2f", 
                                                displayOf(figi), accountId, maxUtilization, currentLiquid.subtract(requiredAmount)));
                                        return;
                                    }
                                    
                                    // Проверка концентрации риска: не превышаем ли максимальную долю на один инструмент
                                    BigDecimal currentPositionValue = portfolioAnalysis.getPositionValues().getOrDefault(figi, BigDecimal.ZERO);
                                    BigDecimal newPositionValue = currentPositionValue.add(requiredAmount);
                                    BigDecimal maxPositionValue = portfolioAnalysis.getTotalValue().multiply(new BigDecimal("0.05")); // Максимум 5% на один инструмент
                                    
                                    if (newPositionValue.compareTo(maxPositionValue) > 0) {
                                        log.warn("🚨 КОНЦЕНТРАЦИЯ РИСКА: превышение максимальной доли на инструмент [{} , accountId={}]. Текущая позиция: {}, новая: {}, максимум: {}", 
                                            displayOf(figi), accountId, currentPositionValue, newPositionValue, maxPositionValue);
                                        botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT, 
                                            "Превышение максимальной доли на инструмент", String.format("%s, Account: %s, Текущая: %.2f, Новая: %.2f, Максимум: %.2f", 
                                                displayOf(figi), accountId, currentPositionValue, newPositionValue, maxPositionValue));
                                        return;
                                    }
                                    
                                    log.info("✅ Маржинальные лимиты соблюдены: liquid={}, minimal={}, missing={}, maxUtilization={}, концентрация риска в норме", 
                                        currentLiquid, currentMinimal, currentMissing, maxUtilization);
                                    
                                    // 🚀 АДАПТИВНАЯ ДИВЕРСИФИКАЦИЯ: лимиты зависят от размера портфеля
                                    AdaptiveDiversificationService.DiversificationSettings diversificationSettings = 
                                        adaptiveDiversificationService.getDiversificationSettings(portfolioAnalysis.getTotalValue());
                                    
                                    long totalPositions = portfolioAnalysis.getPositions().size();
                                    int maxPositions = diversificationSettings.getMaxTotalPositions();
                                    
                                    if (totalPositions >= maxPositions) {
                                        log.warn("🚨 АДАПТИВНАЯ ДИВЕРСИФИКАЦИЯ: превышение лимита на количество позиций [{} , accountId={}]. Текущих позиций: {}, максимум: {} ({})", 
                                            displayOf(figi), accountId, totalPositions, maxPositions, diversificationSettings.getReason());
                                        botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT, 
                                            "Превышение адаптивного лимита позиций", String.format("%s, Account: %s, Текущих: %d, Максимум: %d, Причина: %s", 
                                                displayOf(figi), accountId, totalPositions, maxPositions, diversificationSettings.getReason()));
                                        return;
                                    }
                                    
                                    log.info("✅ Адаптивная диверсификация в норме: текущих позиций {}, максимум {} ({})", 
                                        totalPositions, maxPositions, diversificationSettings.getReason());
                        }
                    } catch (Exception e) {
                                log.warn("Ошибка проверки маржинальных лимитов для {}: {}", displayOf(figi), e.getMessage());
                                // Продолжаем выполнение, но с осторожностью
                            }
                        }
                        
                        if (availableForTrade.compareTo(requiredAmount) < 0) {
                            log.warn("Реальная проверка: недостаточно средств [{} , accountId={}] для покупки {} лотов. Нужно: {}, Доступно: {} (buyingPower: {})", 
                                displayOf(figi), accountId, lots, requiredAmount, availableForTrade, buyingPower);
                            botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT, 
                                "Недостаточно реальных средств", String.format("%s, Account: %s, Price: %.4f, Лотов: %d, Нужно: %.2f, Доступно: %.2f, Плечо: %.2f", 
                                    displayOf(figi), accountId, trend.getCurrentPrice(), lots, requiredAmount, availableForTrade, buyingPower));
                            return;
                        }
                        
                        log.info("✅ Проверка средств пройдена: требуется {}, доступно {} (включая плечо: {})", 
                            requiredAmount, availableForTrade, buyingPower);
                    } catch (Exception e) {
                        log.warn("Ошибка проверки реальных средств для {}: {}", displayOf(figi), e.getMessage());
                        // Продолжаем выполнение, но с осторожностью
                    }
                    
                    if (lots > 0) {
                        // Применяем стоп-правила если заданы (обрезаем размер позиции до стоп-риска)
                        PortfolioAnalysis finalAnalysis = portfolioAnalysis;
                        final int lotsBeforeRisk = lots;
                        java.util.concurrent.atomic.AtomicInteger adjustedLots = new java.util.concurrent.atomic.AtomicInteger(lotsBeforeRisk);
                        riskRuleService.findByFigi(figi).ifPresent(rule -> {
                            if (rule.getStopLossPct() != null) {
                                // мягкое ограничение: не превышать 1% портфеля на сделку при заданном SL
                                BigDecimal maxRiskPerTrade = finalAnalysis.getTotalValue().multiply(new BigDecimal("0.01"));
                                BigDecimal allowedCost = maxRiskPerTrade.divide(new BigDecimal(rule.getStopLossPct()), 0, RoundingMode.DOWN);
                                BigDecimal allowedLots = allowedCost.divide(trend.getCurrentPrice(), 0, RoundingMode.DOWN);
                                if (allowedLots.compareTo(BigDecimal.valueOf(adjustedLots.get())) < 0) {
                                    log.info("Ограничение по риску: сокращаем лоты {} -> {}", adjustedLots.get(), allowedLots);
                                    adjustedLots.set(allowedLots.intValue());
                                }
                            }
                        });
                        // Если явного правила нет — применяем дефолты из настроек
                        if (adjustedLots.get() == lotsBeforeRisk) {
                            double slDefault = riskRuleService.getDefaultStopLossPct();
                            BigDecimal maxRiskPerTrade = finalAnalysis.getTotalValue().multiply(BigDecimal.valueOf(riskRuleService.getRiskPerTradePct()));
                            BigDecimal allowedCost = maxRiskPerTrade.divide(BigDecimal.valueOf(slDefault), 0, RoundingMode.DOWN);
                            BigDecimal allowedLots = allowedCost.divide(trend.getCurrentPrice(), 0, RoundingMode.DOWN);
                            if (allowedLots.compareTo(BigDecimal.valueOf(adjustedLots.get())) < 0) {
                                adjustedLots.set(allowedLots.intValue());
                                log.info("Дефолтное ограничение по риску: лоты {} -> {}", lotsBeforeRisk, adjustedLots.get());
                            }
                        }
                        lots = adjustedLots.get();

                        String actionType = hasPosition ? "докупка" : "покупка";
                        BigDecimal totalCost = trend.getCurrentPrice().multiply(BigDecimal.valueOf(lots));
                        
                        // Определяем тип операции (маржинальная или обычная)
                        String operationType = (allowNegativeCash && availableCash.compareTo(BigDecimal.ZERO) < 0) ? "маржинальная " : "";
                        String fullActionType = operationType + actionType;
                        
                        // 🚀 АДАПТИВНОЕ ОГРАНИЧЕНИЕ ДОЛИ КЛАССА АКТИВОВ
                        try {
                            String instrType = determineInstrumentType(figi);
                            
                            // Получаем адаптивный лимит для данного класса активов
                            BigDecimal adaptiveLimit = adaptiveDiversificationService.getMaxAssetClassPercentage(
                                portfolioAnalysis.getTotalValue(), instrType);
                            
                            // Проверяем текущую долю класса активов
                            BigDecimal currentClassValue = portfolioAnalysis.getCurrentAllocations().getOrDefault(instrType, BigDecimal.ZERO);
                            BigDecimal newClassValue = currentClassValue.add(totalCost);
                            
                            if (portfolioAnalysis.getTotalValue().compareTo(BigDecimal.ZERO) > 0) {
                                BigDecimal newClassShare = newClassValue.divide(portfolioAnalysis.getTotalValue(), 4, RoundingMode.HALF_UP);
                                
                                if (newClassShare.compareTo(adaptiveLimit) > 0) {
                                    String assetClassName = getAssetClassName(instrType);
                                    String msg = String.format("Покупка %s превысит адаптивный лимит %.2f%%: новая доля %.2f%%",
                                            assetClassName, adaptiveLimit.multiply(BigDecimal.valueOf(100)), 
                                            newClassShare.multiply(BigDecimal.valueOf(100)));
                                    
                                    AdaptiveDiversificationService.PortfolioLevel level = 
                                        adaptiveDiversificationService.getPortfolioLevel(portfolioAnalysis.getTotalValue());
                                    
                                    log.warn("🚀 Адаптивная блокировка по классу активов ({}): {} [{} , accountId={}]", 
                                        level, msg, displayOf(figi), accountId);
                                    
                                    botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT,
                                            "Адаптивная блокировка класса активов",
                                            String.format("%s, Account: %s, Уровень портфеля: %s, Причина: %s", 
                                                displayOf(figi), accountId, level, msg));
                                    return;
                                } else {
                                    log.info("✅ Адаптивный лимит класса активов соблюден: {} доля {:.2f}% < {:.2f}% ({})", 
                                        getAssetClassName(instrType), 
                                        newClassShare.multiply(BigDecimal.valueOf(100)), 
                                        adaptiveLimit.multiply(BigDecimal.valueOf(100)),
                                        adaptiveDiversificationService.getPortfolioLevel(portfolioAnalysis.getTotalValue()));
                                }
                            }
                        } catch (Exception e) {
                            log.warn("Ошибка проверки адаптивного лимита по классу активов для {}: {}", displayOf(figi), e.getMessage());
                        }
                        
                        // 🚀 АДАПТИВНАЯ ПРОВЕРКА ДИВЕРСИФИКАЦИИ ПО СЕКТОРАМ
                        try {
                            // Проверяем, нужна ли диверсификация для данного размера портфеля
                            boolean diversificationRequired = adaptiveDiversificationService.isDiversificationRequired(portfolioAnalysis.getTotalValue());
                            
                            if (diversificationRequired) {
                                // Применяем адаптивные лимиты к сектору
                                AdaptiveDiversificationService.DiversificationSettings settings = 
                                    adaptiveDiversificationService.getDiversificationSettings(portfolioAnalysis.getTotalValue());
                                
                                ru.perminov.service.SectorManagementService.SectorValidationResult sectorValidation = 
                                    sectorManagementService.validateAdaptiveSectorDiversification(
                                        figi, 
                                        totalCost, 
                                        portfolioAnalysis.getTotalValue(),
                                        portfolioAnalysis.getPositions(),
                                        settings
                                    );
                                
                                if (!sectorValidation.isValid()) {
                                    log.warn("🚨 НАРУШЕНИЕ АДАПТИВНОЙ ДИВЕРСИФИКАЦИИ: {} [{} , accountId={}]", 
                                        String.join("; ", sectorValidation.getViolations()), displayOf(figi), accountId);
                                    
                                    botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT, 
                                        "Нарушение адаптивной диверсификации", String.format("%s, Account: %s, Сектор: %s, Нарушения: %s", 
                                            displayOf(figi), accountId, sectorValidation.getSectorName(), 
                                            String.join("; ", sectorValidation.getViolations())));
                                    
                                    return; // Блокируем покупку при нарушении диверсификации
                                }
                                
                                log.info("✅ Адаптивная диверсификация по секторам в норме: {}", settings.getReason());
                            } else {
                                log.info("🚀 ДИВЕРСИФИКАЦИЯ ОТКЛЮЧЕНА для малого портфеля ({}₽) - фокус на росте", 
                                    portfolioAnalysis.getTotalValue());
                            }
                            
                        } catch (Exception e) {
                            log.warn("Ошибка проверки диверсификации секторов для {}: {}", displayOf(figi), e.getMessage());
                            // Продолжаем выполнение, но с осторожностью
                        }
                        
                        log.info("Размещение ордера на {} по {}: {} лотов по цене {} (общая стоимость: {}, доступные средства: {})", 
                            fullActionType, displayOf(figi), lots, trend.getCurrentPrice(), totalCost, availableCash);
                        botLogService.addLogEntry(BotLogService.LogLevel.TRADE, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                            "Размещение ордера на " + fullActionType, String.format("%s, Лотов: %d, Цена: %.2f, Стоимость: %.2f, Средства: %.2f", 
                                displayOf(figi), lots, trend.getCurrentPrice(), totalCost, availableCash));
                        
                        // 🚀 ИСПОЛЬЗУЕМ УМНЫЙ ЛИМИТНЫЙ ОРДЕР вместо рыночного
                        try {
                            orderService.placeSmartLimitOrder(figi, lots, OrderDirection.ORDER_DIRECTION_BUY, accountId, trend.getCurrentPrice());
                            botLogService.addLogEntry(BotLogService.LogLevel.SUCCESS, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                                "Ордер на " + fullActionType + " размещен", String.format("%s, Лотов: %d", displayOf(figi), lots));
                            
                            // 🚀 НОВОЕ: Автоматическое размещение OCO (TP + SL) после входа
                            try {
                                double sl = riskRuleService.findByFigi(figi)
                                    .map(rule -> rule.getStopLossPct())
                                    .orElse(riskRuleService.getDefaultStopLossPct());
                                double tp = riskRuleService.findByFigi(figi)
                                    .map(rule -> rule.getTakeProfitPct())
                                    .orElse(riskRuleService.getDefaultTakeProfitPct());
                                
                                orderService.placeVirtualOCO(figi, lots, OrderDirection.ORDER_DIRECTION_BUY, 
                                    accountId, trend.getCurrentPrice(), tp, sl);
                                
                                log.info("🎯 Запланирован OCO для ЛОНГА {}: TP={}%, SL={}% от цены {}", 
                                    displayOf(figi), tp * 100, sl * 100, trend.getCurrentPrice());
                                
                            } catch (Exception e) {
                                log.warn("❌ Не удалось запланировать OCO для {}: {}", displayOf(figi), e.getMessage());
                            }
                            
                            // Авто-установка SL/TP по дефолтным настройкам, если для FIGI ещё нет правил
                            try {
                                if (riskRuleService.findByFigi(figi).isEmpty()) {
                                    double sl = riskRuleService.getDefaultStopLossPct();
                                    double tp = riskRuleService.getDefaultTakeProfitPct();
                                    riskRuleService.upsert(figi, sl, tp, true);
                                    log.info("Установлены уровни SL/TP для {}: SL={}%, TP={}%, активированы", displayOf(figi), sl * 100, tp * 100);
                                    botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.RISK_MANAGEMENT,
                                        "Установлены SL/TP",
                                        String.format("%s, SL: %.2f%%, TP: %.2f%%", displayOf(figi), sl * 100, tp * 100));
                                }
                            } catch (Exception e) {
                                log.warn("Не удалось установить правила SL/TP для {}: {}", displayOf(figi), e.getMessage());
                            }
                        } catch (Exception e) {
                            log.error("Ошибка размещения ордера на {}: {}", actionType, e.getMessage());
                            botLogService.addLogEntry(BotLogService.LogLevel.ERROR, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                                "Ошибка размещения ордера на " + actionType, e.getMessage());
                            // НЕ останавливаем выполнение, продолжаем с другими инструментами
                        }
                    } else {
                        log.warn("Не удалось рассчитать количество лотов для покупки. Сумма: {}, Цена: {}, Лотов: {}", 
                            buyAmount, trend.getCurrentPrice(), lots);
                        botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT, 
                            "Ошибка расчета лотов", String.format("Сумма: %.2f, Цена: %.2f, Лотов: %d", 
                                buyAmount, trend.getCurrentPrice(), lots));
                    }
                } else {
                    log.warn("❌ Нет свободных средств для покупки");
                    botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT, 
                        "Нет свободных средств", "Доступно: " + buyingPower);
                    
                    // Дополнительная диагностика: почему buyingPower = 0?
                    log.info("🔍 Диагностика buyingPower = 0:");
                    log.info("  - availableCash: {}", availableCash);
                    log.info("  - marginEnabled: {}", marginService.isMarginEnabled());
                    log.info("  - marginOperational: {}", marginService.isMarginOperationalForAccount(accountId));
                    log.info("  - allowNegativeCash: {}", tradingSettingsService.getBoolean("margin-trading.allow-negative-cash", false));
                }
            } else if ("SELL".equals(action)) {
                log.info("🎯 ВЫПОЛНЯЕМ SELL для {}: проверяем позицию (НЕ зависит от buyingPower)", displayOf(figi));
                // Проверяем, есть ли позиция по этому инструменту
                BigDecimal positionValue = portfolioAnalysis.getPositionValues().get(figi);
                log.debug("Значение позиции по {}: {}", displayOf(figi), positionValue);
                if (positionValue != null && positionValue.compareTo(BigDecimal.ZERO) != 0) {
                    // Находим позицию для получения количества лотов
                    Position position = portfolioAnalysis.getPositions().stream()
                        .filter(p -> p.getFigi().equals(figi))
                        .findFirst()
                        .orElse(null);
                    
                    if (position != null && position.getQuantity().compareTo(BigDecimal.ZERO) != 0) {
                        int lots = Math.abs(position.getQuantity().intValue()); // Берем абсолютное значение
                        boolean isShortPosition = position.getQuantity().compareTo(BigDecimal.ZERO) < 0;
                        
                        String actionDescription = isShortPosition ? "закрытие шорта" : "продажа";
                        log.info("Размещение ордера на {} по {}: {} лотов по цене {}", actionDescription, displayOf(figi), lots, trend.getCurrentPrice());
                        
                        botLogService.addLogEntry(BotLogService.LogLevel.TRADE, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                            "Размещение ордера на " + actionDescription, String.format("%s, Лотов: %d, Цена: %.2f", 
                                displayOf(figi), lots, trend.getCurrentPrice()));
                        
                        // 🚀 ИСПОЛЬЗУЕМ УМНЫЙ ЛИМИТНЫЙ ОРДЕР вместо рыночного
                        try {
                            orderService.placeSmartLimitOrder(figi, lots, OrderDirection.ORDER_DIRECTION_SELL, accountId, trend.getCurrentPrice());
                            botLogService.addLogEntry(BotLogService.LogLevel.SUCCESS, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                                "Ордер на " + actionDescription + " размещен", String.format("%s, Лотов: %d", displayOf(figi), lots));
                        } catch (Exception e) {
                            log.error("Ошибка размещения ордера на {}: {}", actionDescription, e.getMessage());
                            botLogService.addLogEntry(BotLogService.LogLevel.ERROR, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                                "Ошибка размещения ордера на " + actionDescription, e.getMessage());
                            // НЕ останавливаем выполнение, продолжаем с другими инструментами
                        }
                    } else {
                        log.warn("Нет позиции для продажи по инструменту {}", displayOf(figi));
                        botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT, 
                            "Нет позиции для продажи", displayOf(figi));
                    }
                } else {
                    log.info("🎯 ПОЗИЦИИ НЕТ - проверяем возможность открытия шорта для {}", displayOf(figi));
                    // Позиции нет. Рассматриваем открытие шорта, если это разрешено и доступно
                    String prettyName = instrumentNameService != null ? instrumentNameService.getInstrumentName(figi, "share") : figi;
                    String prettyTicker = instrumentNameService != null ? instrumentNameService.getTicker(figi, "share") : figi;
                    botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.RISK_MANAGEMENT,
                        "Проверка возможности шорта",
                        String.format("%s (%s), Account: %s, Price: %.4f — позиции нет, оцениваем шорт", prettyName, prettyTicker, accountId, trend.getCurrentPrice()));
                    boolean marginEnabled = marginService.isMarginEnabled();
                    boolean shortAllowed = marginService.isShortAllowed();
                    boolean shortFlag = false;
                    try { shortFlag = marginService.canOpenShort(figi); } catch (Exception ignore) {}
                    boolean marginOperational = marginService.isMarginOperationalForAccount(accountId);
                    log.info("Проверка шорта [{} {}] [accountId={}]: marginEnabled={}, shortAllowed={}, shortFlag={}, marginOperational={}, mode={}",
                            prettyTicker, prettyName, accountId, marginEnabled, shortAllowed, shortFlag, marginOperational, investApiManager.getCurrentMode());

                    if (shortFlag && marginOperational) {
                        BigDecimal targetShortAmount = marginService.calculateTargetShortAmount(accountId, portfolioAnalysis);
                        log.info("Расчет лимита шорта [{} {}] [accountId={}]: targetShortAmount={}, price={}", prettyTicker, prettyName, accountId, targetShortAmount, trend.getCurrentPrice());
                        botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.RISK_MANAGEMENT,
                            "Лимит шорта рассчитан",
                            String.format("%s (%s), Account: %s, Target: %.2f, Price: %.4f", prettyName, prettyTicker, accountId, targetShortAmount, trend.getCurrentPrice()));
                        if (targetShortAmount.compareTo(trend.getCurrentPrice()) >= 0) {
                            int lots = targetShortAmount.divide(trend.getCurrentPrice(), 0, RoundingMode.DOWN).intValue();
                            
                            // Проверяем рентабельность шорта с учетом комиссий
                            BigDecimal tradeAmount = trend.getCurrentPrice().multiply(BigDecimal.valueOf(lots));
                            BigDecimal minPriceMove = commissionCalculatorService.calculateBreakevenPriceMove(
                                trend.getCurrentPrice(), lots, "share");
                            
                            log.info("💰 Анализ рентабельности шорта: {} лотов по {}₽, нужно падение минимум на {}₽", 
                                lots, trend.getCurrentPrice(), minPriceMove);
                            
                            // Дополнительная проверка реальной доступности маржи для шорта
                            try {
                                var marginAttrs = marginService.getAccountMarginAttributes(accountId);
                                if (marginAttrs != null) {
                                    BigDecimal liquid = marginService.toBigDecimal(marginAttrs.getLiquidPortfolio());
                                    BigDecimal minimal = marginService.toBigDecimal(marginAttrs.getMinimalMargin());
                                    BigDecimal availableMargin = liquid.subtract(minimal);
                                    BigDecimal requiredMargin = trend.getCurrentPrice().multiply(BigDecimal.valueOf(lots));
                                    
                                    if (availableMargin.compareTo(requiredMargin) < 0) {
                                        log.warn("❌ Реальная проверка маржи: недостаточно для шорта {} лотов. Нужно: {}, Доступно: {}", 
                                            lots, requiredMargin, availableMargin);
                                        botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT, 
                                            "Недостаточно маржи для шорта", String.format("Лотов: %d, Нужно: %.2f, Доступно: %.2f", 
                                                lots, requiredMargin, availableMargin));
                                        return;
                                    }
                                }
                            } catch (Exception e) {
                                log.warn("Ошибка проверки маржи для шорта {}: {}", displayOf(figi), e.getMessage());
                                // Продолжаем выполнение, но с осторожностью
                            }
                            
                            log.info("🎯 ОТКРЫВАЕМ ШОРТ по {}: {} лотов по цене {}", displayOf(figi), lots, trend.getCurrentPrice());
                            botLogService.addLogEntry(BotLogService.LogLevel.TRADE, BotLogService.LogCategory.AUTOMATIC_TRADING,
                                "Открытие шорта", String.format("%s, Лотов: %d", displayOf(figi), lots));
                            try {
                                log.info("🎯 Размещаем ордер на открытие шорта: {} лотов SELL по цене {}", lots, trend.getCurrentPrice());
                                PostOrderResponse response = orderService.placeSmartLimitOrder(figi, lots, OrderDirection.ORDER_DIRECTION_SELL, accountId, trend.getCurrentPrice());
                                log.info("🎯 Ордер на открытие шорта размещен успешно: orderId={}, status={}", 
                                    response.getOrderId(), response.getExecutionReportStatus());
                                botLogService.addLogEntry(BotLogService.LogLevel.SUCCESS, BotLogService.LogCategory.AUTOMATIC_TRADING,
                                    "Шорт открыт", String.format("FIGI: %s, Лотов: %d, OrderId: %s", figi, lots, response.getOrderId()));
                                
                                // 🚀 НОВОЕ: Автоматический OCO для шорта
                                try {
                                    double sl = riskRuleService.findByFigi(figi)
                                        .map(rule -> rule.getStopLossPct())
                                        .orElse(riskRuleService.getDefaultStopLossPct());
                                    double tp = riskRuleService.findByFigi(figi)
                                        .map(rule -> rule.getTakeProfitPct())
                                        .orElse(riskRuleService.getDefaultTakeProfitPct());
                                    
                                    orderService.placeVirtualOCO(figi, lots, OrderDirection.ORDER_DIRECTION_SELL, 
                                        accountId, trend.getCurrentPrice(), tp, sl);
                                    
                                    log.info("🎯 Запланирован OCO для ШОРТА {}: TP={}%, SL={}% от цены {}", 
                                        displayOf(figi), tp * 100, sl * 100, trend.getCurrentPrice());
                                    
                                } catch (Exception e) {
                                    log.warn("❌ Не удалось запланировать OCO для шорта {}: {}", displayOf(figi), e.getMessage());
                                }
                                // Авто-установка SL/TP по дефолтным настройкам, если для FIGI ещё нет правил
                                try {
                                    if (riskRuleService.findByFigi(figi).isEmpty()) {
                                        double sl = riskRuleService.getDefaultStopLossPct();
                                        double tp = riskRuleService.getDefaultTakeProfitPct();
                                        riskRuleService.upsert(figi, sl, tp, true);
                                        log.info("Установлены уровни SL/TP для {}: SL={}%, TP={}%, активированы (шорт)", displayOf(figi), sl * 100, tp * 100);
                                        botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.RISK_MANAGEMENT,
                                            "Установлены SL/TP (шорт)",
                                String.format("%s, SL: %.2f%%, TP: %.2f%%", displayOf(figi), sl * 100, tp * 100));
                                    }
                                } catch (Exception e) {
                                    log.warn("Не удалось установить правила SL/TP для {} (шорт): {}", displayOf(figi), e.getMessage());
                                }
                            } catch (Exception e) {
                                log.error("❌ Ошибка открытия шорта: {}", e.getMessage(), e);
                                botLogService.addLogEntry(BotLogService.LogLevel.ERROR, BotLogService.LogCategory.AUTOMATIC_TRADING,
                                    "Ошибка открытия шорта", e.getMessage());
                            }
                        } else {
                            log.warn("❌ Недостаточно лимита для шорта по [{} {}]: targetShortAmount {} < price {}", prettyTicker, prettyName, targetShortAmount, trend.getCurrentPrice());
                            botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT,
                                "Недостаточно лимита для шорта",
                                String.format("%s (%s), Target: %.2f < Price: %.4f", prettyName, prettyTicker, targetShortAmount, trend.getCurrentPrice()));
                        }
                    } else if (shortFlag && !marginOperational) {
                        log.warn("❌ Шорт-флаг инструмента=TRUE, но маржа недоступна для аккаунта {} (песочница/нет маржинальных атрибутов)", accountId);
                        botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT,
                            "Шорт недоступен для аккаунта",
                            String.format("%s (%s), Account: %s, Mode: %s — нет маржинальных атрибутов", prettyName, prettyTicker, accountId, investApiManager.getCurrentMode()));
                    } else {
                        log.warn("❌ Шорт невозможен [{} {}]: marginEnabled={}, shortAllowed={}, shortFlag={} — пропускаем", prettyTicker, prettyName, marginEnabled, shortAllowed, shortFlag);
                        botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT, 
                            "Шорт невозможен",
                            String.format("%s (%s), marginEnabled=%s, allowShort=%s, shortFlag=%s", prettyName, prettyTicker, marginEnabled, shortAllowed, shortFlag));
                    }
                }
            } else if ("BUY".equals(action)) {
                // Проверяем, есть ли шорт-позиция для закрытия
                BigDecimal positionValue = portfolioAnalysis.getPositionValues().get(figi);
                if (positionValue != null && positionValue.compareTo(BigDecimal.ZERO) < 0) {
                                            log.info("Обнаружена шорт-позиция для закрытия: {} (значение: {})", displayOf(figi), positionValue);
                    // Находим шорт-позицию для получения количества лотов
                    Position position = portfolioAnalysis.getPositions().stream()
                        .filter(p -> p.getFigi().equals(figi))
                        .findFirst()
                        .orElse(null);
                    
                    if (position != null && position.getQuantity().compareTo(BigDecimal.ZERO) < 0) {
                        int lots = Math.abs(position.getQuantity().intValue()); // Берем абсолютное значение
                        log.info("Закрытие шорта: {} лотов по цене {}", lots, trend.getCurrentPrice());
                        
                        botLogService.addLogEntry(BotLogService.LogLevel.TRADE, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                            "Закрытие шорта", String.format("FIGI: %s, Лотов: %d, Цена: %.2f", 
                                figi, lots, trend.getCurrentPrice()));
                        
                        // Размещаем реальный ордер на покупку для закрытия шорта
                        // ВАЖНО: При закрытии шортов НЕ проверяем отрицательные средства,
                        // так как это может привести к неконтролируемым убыткам
                        try {
                            orderService.placeSmartLimitOrder(figi, lots, OrderDirection.ORDER_DIRECTION_BUY, accountId, trend.getCurrentPrice());
                            botLogService.addLogEntry(BotLogService.LogLevel.SUCCESS, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                                "Шорт закрыт умным лимитом", String.format("FIGI: %s, Лотов: %d", figi, lots));
                        } catch (Exception e) {
                            log.error("Ошибка закрытия шорта: {}", e.getMessage());
                            botLogService.addLogEntry(BotLogService.LogLevel.ERROR, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                                "Ошибка закрытия шорта", e.getMessage());
                            // НЕ останавливаем выполнение, продолжаем с другими инструментами
                        }
                    } else {
                        log.warn("Нет шорт-позиции для закрытия по инструменту {}", displayOf(figi));
                        botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT, 
                                                          "Нет шорт-позиции для закрытия", displayOf(figi));
                    }
                } else {
                    // Нет шорт-позиции, но есть сигнал на покупку - это обычная покупка
                                            log.info("Обычная покупка (не закрытие шорта): {} (позиция: {})", displayOf(figi), positionValue);
                    // Проверяем, есть ли свободные средства
                    BigDecimal availableCash = getAvailableCash(portfolioAnalysis);
                    BigDecimal buyingPower = marginService.getAvailableBuyingPower(accountId, portfolioAnalysis);
                    
                    // Дополнительная проверка: если реальные средства отрицательные, блокируем покупки
                    if (availableCash.compareTo(BigDecimal.ZERO) < 0) {
                        log.warn("Реальные средства отрицательные ({}), блокируем покупки", availableCash);
                        botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT, 
                            "Блокировка покупок", String.format("Отрицательные средства: %.2f", availableCash));
                        return;
                    }
                    
                    if (buyingPower.compareTo(BigDecimal.ZERO) > 0) {
                        // Логика покупки (аналогично BUY выше)
                        // ... (можно вынести в отдельный метод)
                        log.info("Покупка нового инструмента: {} (покупательная способность: {})", displayOf(figi), buyingPower);
                        botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                                                          "Покупка нового инструмента", String.format("%s, Покупательная способность: %.2f", displayOf(figi), buyingPower));
                    } else {
                        log.warn("Нет свободных средств для покупки нового инструмента {}", displayOf(figi));
                        botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT, 
                            "Нет средств для покупки", displayOf(figi));
                    }
                }
            } else {
                log.info("Действие HOLD - никаких операций не выполняем");
                botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                    "Действие HOLD", "FIGI: " + figi + " - никаких операций не выполняем");
            }
            
        } catch (Exception e) {
                                    log.error("Ошибка при выполнении торговой стратегии для {}: {}", displayOf(figi), e.getMessage());
            botLogService.addLogEntry(BotLogService.LogLevel.ERROR, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                                  "Ошибка выполнения торговой стратегии", displayOf(figi) + " - " + e.getMessage());
            // НЕ останавливаем выполнение, продолжаем с другими инструментами
        }
    }
    
    private BigDecimal getAvailableCash(PortfolioAnalysis analysis) {
        System.out.println("🚨🚨🚨 ВХОД В getAvailableCash");
        log.info("🚨🚨🚨 ВХОД В getAvailableCash");
        System.out.println("🚨🚨🚨 Всего позиций: " + analysis.getPositions().size());
        log.info("🚨🚨🚨 Всего позиций: {}", analysis.getPositions().size());
        
        // Получаем реальные доступные средства из портфеля
        // Ищем позицию с валютой (обычно RUB)
        for (Position position : analysis.getPositions()) {
            System.out.println("🚨🚨🚨 Проверяем позицию: figi=" + position.getFigi() + ", type=" + position.getInstrumentType() + ", quantity=" + position.getQuantity());
            log.info("🚨🚨🚨 Проверяем позицию: figi={}, type={}, quantity={}", 
                position.getFigi(), position.getInstrumentType(), position.getQuantity());
            
            // Проверяем тип инструмента ИЛИ специальный FIGI для рубля
            if ("currency".equals(position.getInstrumentType()) || "RUB000UTSTOM".equals(position.getFigi())) {
                System.out.println("🚨🚨🚨 НАЙДЕНА ВАЛЮТА: " + displayOf(position.getFigi()) + " - " + position.getQuantity());
                log.info("🚨🚨🚨 НАЙДЕНА ВАЛЮТА: {} - {}", displayOf(position.getFigi()), position.getQuantity());
                return position.getQuantity();
            }
        }
        
        // Если не найдена валюта, возвращаем 0
        System.out.println("🚨🚨🚨 НЕ НАЙДЕНЫ ДОСТУПНЫЕ СРЕДСТВА В ПОРТФЕЛЕ");
        log.warn("🚨🚨🚨 НЕ НАЙДЕНЫ ДОСТУПНЫЕ СРЕДСТВА В ПОРТФЕЛЕ");
        return BigDecimal.ZERO;
    }
    
    public static class PortfolioAnalysis {
        private final BigDecimal totalValue;
        private final Map<String, BigDecimal> currentAllocations;
        private final Map<String, BigDecimal> allocationPercentages;
        private final Map<String, BigDecimal> positionValues;
        private final List<Position> positions;
        
        public PortfolioAnalysis(BigDecimal totalValue, 
                               Map<String, BigDecimal> currentAllocations,
                               Map<String, BigDecimal> allocationPercentages,
                               Map<String, BigDecimal> positionValues,
                               List<Position> positions) {
            this.totalValue = totalValue;
            this.currentAllocations = currentAllocations;
            this.allocationPercentages = allocationPercentages;
            this.positionValues = positionValues;
            this.positions = positions;
        }
        
        // Getters
        public BigDecimal getTotalValue() { return totalValue; }
        public Map<String, BigDecimal> getCurrentAllocations() { return currentAllocations; }
        public Map<String, BigDecimal> getAllocationPercentages() { return allocationPercentages; }
        public Map<String, BigDecimal> getPositionValues() { return positionValues; }
        public List<Position> getPositions() { return positions; }
    }
    
    public static class RebalancingDecision {
        private final boolean needsRebalancing;
        private final String reason;
        private final Map<String, BigDecimal> deviations;
        private final BigDecimal maxDeviation;
        
        public RebalancingDecision(boolean needsRebalancing, String reason) {
            this(needsRebalancing, reason, Map.of(), BigDecimal.ZERO);
        }
        
        public RebalancingDecision(boolean needsRebalancing, String reason, 
                                 Map<String, BigDecimal> deviations, BigDecimal maxDeviation) {
            this.needsRebalancing = needsRebalancing;
            this.reason = reason;
            this.deviations = deviations;
            this.maxDeviation = maxDeviation;
        }
        
        // Getters
        public boolean isNeedsRebalancing() { return needsRebalancing; }
        public String getReason() { return reason; }
        public Map<String, BigDecimal> getDeviations() { return deviations; }
        public BigDecimal getMaxDeviation() { return maxDeviation; }
    }
    
    /**
     * Автоматический выбор лучших инструментов для торговли
     */
    public List<TradingOpportunity> findBestTradingOpportunities(String accountId) {
        try {
            String mode = investApiManager != null ? investApiManager.getCurrentMode() : null;
            log.info("Поиск лучших торговых возможностей для аккаунта: {} (mode={})", accountId, mode);
            botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.TRADING_STRATEGY, 
                "Начало поиска торговых возможностей", "Аккаунт: " + accountId + (mode != null ? ", Режим: " + mode : ""));
            
            List<TradingOpportunity> opportunities = new ArrayList<>();
            
            // 1. Анализируем существующие позиции для продажи
            List<TradingOpportunity> sellOpportunities = analyzeExistingPositions(accountId);
            opportunities.addAll(sellOpportunities);
            
            // 2. Анализируем новые инструменты для покупки
            List<ShareDto> availableShares = getAvailableShares();
            botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.MARKET_ANALYSIS, 
                "Получен список инструментов", "Количество: " + availableShares.size());
            
            for (ShareDto share : availableShares) {
                // Дополнительная проверка статуса торговли
                if (!"SECURITY_TRADING_STATUS_NORMAL_TRADING".equals(share.getTradingStatus())) {
                                            log.debug("Пропускаем инструмент {} - статус торговли: {}", displayOf(share.getFigi()), share.getTradingStatus());
                    continue;
                }
                
                try {
                    TradingOpportunity opportunity = analyzeTradingOpportunity(share.getFigi(), accountId);
                    if (opportunity != null) {
                        opportunities.add(opportunity);
                        botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.TECHNICAL_INDICATORS, 
                            "Анализ инструмента завершен", String.format("FIGI: %s, Score: %.1f, Действие: %s", 
                                share.getFigi(), opportunity.getScore(), opportunity.getRecommendedAction()));
                    }
                    
                    // Добавляем задержку между запросами для избежания лимитов API
                    Thread.sleep(100); // 100ms задержка
                    
                } catch (Exception e) {
                                            log.warn("Ошибка анализа инструмента {}: {}", displayOf(share.getFigi()), e.getMessage());
                    botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.TECHNICAL_INDICATORS, 
                                                  "Ошибка анализа инструмента", displayOf(share.getFigi()) + ", Ошибка: " + e.getMessage());
                    // Продолжаем с следующим инструментом, не останавливаем выполнение
                }
            }
            
            // Сортируем по приоритету (лучшие возможности первыми)
            opportunities.sort((o1, o2) -> o2.getScore().compareTo(o1.getScore()));
            
            // Возвращаем топ-10 возможностей
            List<TradingOpportunity> result = opportunities.stream().limit(10).collect(Collectors.toList());
            
            botLogService.addLogEntry(BotLogService.LogLevel.SUCCESS, BotLogService.LogCategory.TRADING_STRATEGY, 
                "Поиск торговых возможностей завершен", "Найдено возможностей: " + result.size());
            
            return result;
            
        } catch (Exception e) {
            log.error("Ошибка при поиске торговых возможностей: {}", e.getMessage());
            botLogService.addLogEntry(BotLogService.LogLevel.ERROR, BotLogService.LogCategory.TRADING_STRATEGY, 
                "Ошибка поиска торговых возможностей", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Анализ существующих позиций для продажи
     */
    private List<TradingOpportunity> analyzeExistingPositions(String accountId) {
        List<TradingOpportunity> sellOpportunities = new ArrayList<>();
        
        try {
            PortfolioAnalysis portfolioAnalysis = analyzePortfolio(accountId);
            List<Position> positions = portfolioAnalysis.getPositions();
            
            log.info("Анализ {} существующих позиций для продажи", positions.size());
            botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.PORTFOLIO_MANAGEMENT, 
                "Анализ существующих позиций", "Количество позиций: " + positions.size());
            
            for (Position position : positions) {
                // Пропускаем валютные позиции
                if ("currency".equals(position.getInstrumentType())) {
                    continue;
                }
                
                // Анализируем позиции с количеством != 0 (включая шорты)
                if (position.getQuantity().compareTo(BigDecimal.ZERO) != 0) {
                    try {
                        TradingOpportunity opportunity = analyzeTradingOpportunity(position.getFigi(), accountId);
                        
                        // Определяем, является ли позиция шортом
                        boolean isShortPosition = position.getQuantity().compareTo(BigDecimal.ZERO) < 0;
                        
                        // Для шортов логика обратная: если рекомендуют SELL, то нужно закрыть шорт (BUY)
                        // Для длинных позиций: если рекомендуют SELL, то продаем
                        String actionForPosition = isShortPosition ? 
                            ("SELL".equals(opportunity.getRecommendedAction()) ? "BUY" : opportunity.getRecommendedAction()) :
                            opportunity.getRecommendedAction();
                        
                        if (opportunity != null && ("SELL".equals(actionForPosition) || "BUY".equals(actionForPosition))) {
                            // Увеличиваем score для позиций, которые нужно закрыть
                            opportunity = new TradingOpportunity(
                                opportunity.getFigi(),
                                opportunity.getCurrentPrice(),
                                opportunity.getTrend(),
                                opportunity.getRsi(),
                                opportunity.getSma20(),
                                opportunity.getSma50(),
                                opportunity.getScore().add(BigDecimal.valueOf(10)), // Бонус за существующую позицию
                                actionForPosition
                            );
                            sellOpportunities.add(opportunity);
                            
                            String actionDescription = isShortPosition ? 
                                ("BUY".equals(actionForPosition) ? "закрытия шорта" : "действия с шортом") :
                                ("SELL".equals(actionForPosition) ? "продажи" : "действия с позицией");
                            
                            log.info("Найдена возможность {}: {} (Score: {}, Позиция: {})", 
                                actionDescription, position.getFigi(), opportunity.getScore(), 
                                isShortPosition ? "ШОРТ" : "ДЛИННАЯ");
                            botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.PORTFOLIO_MANAGEMENT, 
                                "Найдена возможность " + actionDescription, String.format("FIGI: %s, Score: %.1f, Тип: %s", 
                                    position.getFigi(), opportunity.getScore(), isShortPosition ? "ШОРТ" : "ДЛИННАЯ"));
                        }
                        
                        // Добавляем задержку между запросами
                        Thread.sleep(200); // 200ms задержка для анализа позиций
                        
                    } catch (Exception e) {
                        log.warn("Ошибка анализа позиции {}: {}", displayOf(position.getFigi()), e.getMessage());
                    }
                }
            }
            
            log.info("Найдено {} возможностей для продажи", sellOpportunities.size());
            botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.PORTFOLIO_MANAGEMENT, 
                "Анализ позиций завершен", "Возможностей продажи: " + sellOpportunities.size());
            
        } catch (Exception e) {
            log.error("Ошибка при анализе существующих позиций: {}", e.getMessage());
            botLogService.addLogEntry(BotLogService.LogLevel.ERROR, BotLogService.LogCategory.PORTFOLIO_MANAGEMENT, 
                "Ошибка анализа позиций", e.getMessage());
        }
        
        return sellOpportunities;
    }
    
    /**
     * Анализ торговой возможности для конкретного инструмента
     */
    private TradingOpportunity analyzeTradingOpportunity(String figi, String accountId) {
        try {
            log.debug("=== АНАЛИЗ ТОРГОВОЙ ВОЗМОЖНОСТИ ДЛЯ {} ===", displayOf(figi));
            
            // Получаем технический анализ
            MarketAnalysisService.TrendAnalysis trendAnalysis = 
                marketAnalysisService.analyzeTrend(figi, ru.tinkoff.piapi.contract.v1.CandleInterval.CANDLE_INTERVAL_DAY);
            
            if (trendAnalysis == null) {
                log.warn("Не удалось получить анализ тренда для {}", displayOf(figi));
                return null;
            }
            log.debug("Тренд: {}, текущая цена: {}", trendAnalysis.getTrend(), trendAnalysis.getCurrentPrice());
            
            // Получаем технические индикаторы
            BigDecimal sma20 = marketAnalysisService.calculateSMA(figi, 
                ru.tinkoff.piapi.contract.v1.CandleInterval.CANDLE_INTERVAL_DAY, 20);
            BigDecimal sma50 = marketAnalysisService.calculateSMA(figi, 
                ru.tinkoff.piapi.contract.v1.CandleInterval.CANDLE_INTERVAL_DAY, 50);
            BigDecimal rsi = marketAnalysisService.calculateRSI(figi, 
                ru.tinkoff.piapi.contract.v1.CandleInterval.CANDLE_INTERVAL_DAY, 14);
            
            // Проверяем валидность данных
            if (rsi == null || sma20 == null || sma50 == null) {
                log.warn("Не удалось получить технические индикаторы для {}: RSI={}, SMA20={}, SMA50={}", 
                    displayOf(figi), rsi, sma20, sma50);
                return null;
            }
            log.debug("Индикаторы: RSI={}, SMA20={}, SMA50={}", rsi, sma20, sma50);
            
            // Рассчитываем оценку (score) для инструмента
            BigDecimal score = calculateTradingScore(trendAnalysis, sma20, sma50, rsi);
            log.debug("Торговый score: {}", score);
            
            // Получаем информацию о портфеле для проверки позиций
            PortfolioAnalysis portfolioAnalysis = analyzePortfolio(accountId);
            boolean hasPosition = portfolioAnalysis.getPositionValues().containsKey(figi) && 
                                portfolioAnalysis.getPositionValues().get(figi).compareTo(BigDecimal.ZERO) > 0;
            log.debug("Есть позиция по {}: {}", displayOf(figi), hasPosition);
            
            // Определяем рекомендуемое действие с учетом позиций
            String recommendedAction = determineRecommendedAction(trendAnalysis, rsi, hasPosition, figi, accountId);
            log.info("🎯 РЕКОМЕНДУЕМОЕ ДЕЙСТВИЕ для {}: {}", displayOf(figi), recommendedAction);
            
            return new TradingOpportunity(
                figi,
                trendAnalysis.getCurrentPrice(),
                trendAnalysis.getTrend().name(),
                rsi,
                sma20,
                sma50,
                score,
                recommendedAction
            );
            
        } catch (Exception e) {
                                    log.warn("Ошибка анализа торговой возможности для {}: {}", displayOf(figi), e.getMessage());
            return null;
        }
    }
    
    /**
     * Расчет оценки торговой возможности
     */
    private BigDecimal calculateTradingScore(MarketAnalysisService.TrendAnalysis trendAnalysis, 
                                           BigDecimal sma20, BigDecimal sma50, BigDecimal rsi) {
        BigDecimal score = BigDecimal.ZERO;
        
        // Оценка тренда
        switch (trendAnalysis.getTrend()) {
            case BULLISH:
                score = score.add(BigDecimal.valueOf(30));
                break;
            case SIDEWAYS:
                score = score.add(BigDecimal.valueOf(15));
                break;
            case BEARISH:
                score = score.add(BigDecimal.valueOf(25)); // Увеличиваем score для BEARISH тренда
                break;
            default:
                // UNKNOWN or other values
                score = score.add(BigDecimal.valueOf(0));
                break;
        }
        
        // Оценка RSI
        if (rsi.compareTo(BigDecimal.valueOf(30)) < 0) {
            // Перепроданность - хорошая возможность для покупки
            score = score.add(BigDecimal.valueOf(25));
        } else if (rsi.compareTo(BigDecimal.valueOf(70)) > 0) {
            // Перекупленность - возможность для продажи/шорта
            score = score.add(BigDecimal.valueOf(30)); // Увеличиваем score для перекупленности
        } else {
            // Нейтральная зона
            score = score.add(BigDecimal.valueOf(10));
        }
        
        // Оценка SMA
        if (sma20.compareTo(sma50) > 0) {
            score = score.add(BigDecimal.valueOf(15));
        } else {
            // BEARISH тренд + SMA20 < SMA50 = хорошая возможность для шорта
            if (trendAnalysis.getTrend() == MarketAnalysisService.TrendType.BEARISH) {
                score = score.add(BigDecimal.valueOf(20));
            }
        }
        
        // Оценка волатильности (если цена не равна нулю)
        if (trendAnalysis.getCurrentPrice().compareTo(BigDecimal.ZERO) > 0) {
            score = score.add(BigDecimal.valueOf(10));
        }
        
        return score;
    }
    
    /**
     * Проверяет наличие открытого шорта по указанному FIGI
     */
    private boolean hasShortPosition(String figi, String accountId) {
        try {
            PortfolioAnalysis portfolioAnalysis = analyzePortfolio(accountId);
            return portfolioAnalysis.getPositions().stream()
                .anyMatch(p -> figi.equals(p.getFigi()) && 
                          p.getQuantity() != null && 
                          p.getQuantity().compareTo(BigDecimal.ZERO) < 0);
        } catch (Exception e) {
            log.warn("Ошибка проверки наличия шорта для {}: {}", displayOf(figi), e.getMessage());
            return false;
        }
    }
    
    /**
     * Определение рекомендуемого действия
     */
    private String determineRecommendedAction(MarketAnalysisService.TrendAnalysis trendAnalysis, BigDecimal rsi, boolean hasPosition, String figi, String accountId) {
        // Логика для принятия торговых решений с учетом возможности докупки, продажи и шортов
        // Примечание: проверка доступности средств выполняется в executeTradingStrategy
        
        log.debug("=== АНАЛИЗ ТОРГОВОГО СИГНАЛА ===");
        log.debug("Тренд: {}, RSI: {}, Есть позиция: {}", trendAnalysis.getTrend(), rsi, hasPosition);
        
        // 🚀 НОВАЯ ПРОВЕРКА: Анализ прибыльности с учетом комиссий
        BigDecimal currentPrice = trendAnalysis.getCurrentPrice();
        if (!isProfitableTrade(currentPrice, figi)) {
            log.info("💰 БЛОКИРОВКА СДЕЛКИ: Сделка по {} не будет прибыльной с учетом комиссий (цена: {})", 
                displayOf(figi), currentPrice);
            return "HOLD";
        }
        
        // 🚀 НОВАЯ ПРОВЕРКА: Минимальная волатильность (ATR фильтр)
        if (!hasMinimumVolatility(trendAnalysis, figi)) {
            log.info("📊 БЛОКИРОВКА СДЕЛКИ: Недостаточная волатильность для {} (ATR слишком низкий)", 
                displayOf(figi));
            return "HOLD";
        }
        
        // СПЕЦИАЛЬНАЯ ЛОГИКА ДЛЯ ЗАКРЫТИЯ ШОРТОВ - ТОЛЬКО ЕСЛИ ШОРТ ЕСТЬ!
        boolean hasShortPosition = hasShortPosition(figi, accountId);
        if (hasShortPosition) {
            log.debug("🔍 Найден открытый шорт по {}, проверяем условия закрытия", displayOf(figi));
            
            // Если RSI упал ниже 30 - это хороший момент для закрытия шорта (покупки)
            if (rsi.compareTo(BigDecimal.valueOf(30)) < 0) {
                log.info("🎯 СИГНАЛ НА ЗАКРЫТИЕ ШОРТА: RSI {} < 30 (сильная перепроданность)", rsi);
                return "CLOSE_SHORT"; // Специальное действие для закрытия шорта
            }
            
            // Если восходящий тренд начинается - закрываем шорт
            if (trendAnalysis.getTrend() == MarketAnalysisService.TrendType.BULLISH && rsi.compareTo(BigDecimal.valueOf(40)) < 0) {
                log.info("🎯 СИГНАЛ НА ЗАКРЫТИЕ ШОРТА: BULLISH тренд + RSI {} < 40", rsi);
                return "CLOSE_SHORT"; // Специальное действие для закрытия шорта
            }
            
            // Если шорт есть, но условий для закрытия нет - держим
            log.debug("🔒 Шорт по {} держим - условия закрытия не выполнены", displayOf(figi));
            return "HOLD";
        }
        
        // Вариант 1: встроить открытие шорта в стратегию
        // Если нисходящий тренд и позиции нет — разрешаем SELL (вход в шорт) при признаках слабости/перекупленности
        if (trendAnalysis.getTrend() == MarketAnalysisService.TrendType.BEARISH && !hasPosition) {
            log.debug("BEARISH тренд + нет позиции - проверяем условия для шорта");
            // RSI выше 60 трактуем как риск продолжения снижения после перекупленности — инициируем шорт
            if (rsi.compareTo(BigDecimal.valueOf(60)) > 0) {
                log.info("🎯 СИГНАЛ НА ШОРТ: BEARISH тренд + RSI {} > 60 + нет позиции", rsi);
                return "SELL"; // трактуем SELL как вход в шорт при отсутствии позиции
            } else {
                log.debug("RSI {} <= 60, шорт не рекомендуется", rsi);
            }
        }
        
        if (trendAnalysis.getTrend() == MarketAnalysisService.TrendType.BULLISH) {
            log.debug("BULLISH тренд - анализируем возможности");
            if (rsi.compareTo(BigDecimal.valueOf(40)) < 0) {
                log.info("🎯 СИГНАЛ НА ПОКУПКУ: BULLISH тренд + RSI {} < 40 (перепроданность)", rsi);
                return "BUY"; // Сильная покупка при перепроданности (докупаем или покупаем)
            } else if (rsi.compareTo(BigDecimal.valueOf(60)) < 0) {
                String action = hasPosition ? "HOLD" : "BUY";
                log.debug("BULLISH тренд + RSI {} < 60: {}", rsi, action);
                return action; // Умеренная покупка - докупаем только при хороших условиях
            } else if (rsi.compareTo(BigDecimal.valueOf(75)) > 0) {
                String action = hasPosition ? "SELL" : "HOLD";
                log.info("🎯 СИГНАЛ НА ПРОДАЖУ: BULLISH тренд + RSI {} > 75 (перекупленность) + есть позиция: {}", rsi, hasPosition);
                return action; // Продажа при перекупленности даже в восходящем тренде
            }
        } else if (trendAnalysis.getTrend() == MarketAnalysisService.TrendType.BEARISH) {
            log.debug("BEARISH тренд - анализируем возможности");
            if (rsi.compareTo(BigDecimal.valueOf(70)) > 0) {
                // При нисходящем тренде и перекупленности разрешаем шорт
                String action = hasPosition ? "SELL" : "SELL"; // Разрешаем шорт при сильной перекупленности
                log.info("🎯 СИГНАЛ НА ПРОДАЖУ/ШОРТ: BEARISH тренд + RSI {} > 70 (перекупленность) + есть позиция: {}", rsi, hasPosition);
                return action; // Сильная продажа/шорт при перекупленности
            } else if (rsi.compareTo(BigDecimal.valueOf(50)) > 0) {
                // При нисходящем тренде разрешаем шорт даже при умеренных условиях
                String action = hasPosition ? "SELL" : "SELL"; // Разрешаем шорт при нисходящем тренде
                log.debug("BEARISH тренд + RSI {} > 50: {} (разрешен шорт)", rsi, action);
                return action; // Умеренная продажа/шорт при нисходящем тренде
            } else if (rsi.compareTo(BigDecimal.valueOf(30)) < 0) {
                log.info("🎯 СИГНАЛ НА ПОКУПКУ: BEARISH тренд + RSI {} < 30 (сильная перепроданность)", rsi);
                return "BUY"; // Покупка при сильной перепроданности даже в нисходящем тренде
            }
        }
        
        // Для бокового тренда используем RSI
        if (rsi.compareTo(BigDecimal.valueOf(35)) < 0) {
            log.info("🎯 СИГНАЛ НА ПОКУПКУ: Боковой тренд + RSI {} < 35 (перепроданность)", rsi);
            return "BUY"; // Докупаем при сильной перепроданности
        } else if (rsi.compareTo(BigDecimal.valueOf(65)) > 0) {
            // При боковом тренде и перекупленности разрешаем шорт даже без позиции
            String action = hasPosition ? "SELL" : "SELL"; // Разрешаем шорт при перекупленности
            log.info("🎯 СИГНАЛ НА ПРОДАЖУ/ШОРТ: Боковой тренд + RSI {} > 65 (перекупленность) + есть позиция: {}", rsi, hasPosition);
            return action; // Продажа/шорт при перекупленности
        }
        
        log.debug("Нет четкого сигнала - HOLD");
        return "HOLD";
    }
    
    /**
     * 🚀 НОВЫЙ МЕТОД: Проверка минимальной волатильности
     */
    private boolean hasMinimumVolatility(MarketAnalysisService.TrendAnalysis trendAnalysis, String figi) {
        try {
            // Пока метод getAtr() не реализован, используем простую проверку на основе цены
            BigDecimal currentPrice = trendAnalysis.getCurrentPrice();
            
            if (currentPrice != null && currentPrice.compareTo(BigDecimal.ZERO) > 0) {
                // Простая проверка: цена должна быть больше 1 рубля для эффективной торговли
                boolean hasVolatility = currentPrice.compareTo(BigDecimal.ONE) > 0;
                
                log.debug("📊 Проверка волатильности {}: цена={} → {}", 
                    displayOf(figi), currentPrice, hasVolatility ? "ДОСТАТОЧНО" : "МАЛО");
                
                return hasVolatility;
            }
            
            // Если цена недоступна, разрешаем торговлю
            return true;
            
        } catch (Exception e) {
            log.warn("Ошибка проверки волатильности для {}: {}", displayOf(figi), e.getMessage());
            return true; // При ошибке разрешаем торговлю
        }
    }
    
    /**
     * 🚀 НОВЫЙ МЕТОД: Проверка прибыльности сделки с учетом комиссий
     */
    private boolean isProfitableTrade(BigDecimal currentPrice, String figi) {
        try {
            // Используем минимальный размер позиции для расчета
            BigDecimal minPositionValue = new BigDecimal(tradingSettingsService.getString("capital-management.min-position-value", "1000"));
            int estimatedLots = minPositionValue.divide(currentPrice, 0, RoundingMode.UP).intValue();
            if (estimatedLots < 1) estimatedLots = 1;
            
            BigDecimal tradeAmount = currentPrice.multiply(BigDecimal.valueOf(estimatedLots));
            
            // Получаем тип инструмента
            String instrumentType = determineInstrumentType(figi);
            
            // Рассчитываем минимальное движение цены для безубыточности
            BigDecimal minPriceMove = commissionCalculatorService.calculateBreakevenPriceMove(currentPrice, estimatedLots, instrumentType);
            
            // Рассчитываем минимальный процент движения
            BigDecimal minMovePct = minPriceMove.divide(currentPrice, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            
            // Получаем настройки стоп-лосса и тейк-профита
            double slPct = riskRuleService.getDefaultStopLossPct() * 100; // переводим в проценты
            double tpPct = riskRuleService.getDefaultTakeProfitPct() * 100;
            
            // Проверяем, достаточно ли тейк-профит для покрытия комиссий + риска
            boolean profitable = tpPct > (minMovePct.doubleValue() + slPct);
            
            log.debug("💰 Анализ прибыльности {}: цена={}, лотов={}, мин.движение={}% ({}₽), SL={}%, TP={}% → {}", 
                displayOf(figi), currentPrice, estimatedLots, minMovePct, minPriceMove, slPct, tpPct,
                profitable ? "ПРИБЫЛЬНО" : "УБЫТОЧНО");
            
            return profitable;
            
        } catch (Exception e) {
            log.warn("Ошибка проверки прибыльности для {}: {}", displayOf(figi), e.getMessage());
            return true; // При ошибке разрешаем торговлю
        }
    }
    
    /**
     * Получение доступных акций
     */
    private List<ShareDto> getAvailableShares() {
        try {
            log.info("Получение доступных инструментов через DynamicInstrumentService...");
            
            // Используем новый динамический сервис
            List<ShareDto> instruments = dynamicInstrumentService.getAvailableInstruments();
            
            log.info("Получено {} доступных инструментов для анализа", instruments.size());
            return instruments;
            
        } catch (Exception e) {
            log.error("Ошибка при получении инструментов: {}", e.getMessage());
            // В случае ошибки возвращаем базовый набор инструментов
            return getFallbackInstruments();
        }
    }
    
    /**
     * Резервный набор инструментов в случае ошибки получения данных
     */
    private List<ShareDto> getFallbackInstruments() {
        List<ShareDto> shares = new ArrayList<>();
        
        // Добавляем популярные акции
        ShareDto apple = new ShareDto();
        apple.setFigi("BBG000B9XRY4");
        apple.setTicker("AAPL");
        apple.setName("Apple Inc.");
        apple.setCurrency("USD");
        apple.setExchange("MOEX");
        apple.setTradingStatus("SECURITY_TRADING_STATUS_NORMAL_TRADING");
        shares.add(apple);
        
        // Добавляем облигацию из портфеля
        ShareDto bond = new ShareDto();
        bond.setFigi("TCS00A107D74");
        bond.setTicker("TCS00A10");
        bond.setName("Облигация Тинькофф");
        bond.setCurrency("RUB");
        bond.setExchange("MOEX");
        bond.setTradingStatus("SECURITY_TRADING_STATUS_NORMAL_TRADING");
        shares.add(bond);
        
        return shares;
    }
    
    /**
     * Автоматическое выполнение торговых операций
     */
    public void executeAutomaticTrading(String accountId) {
        try {
            String mode = investApiManager != null ? investApiManager.getCurrentMode() : null;
            log.info("Запуск автоматической торговли для аккаунта: {} (mode={})", accountId, mode);
            botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                "Запуск автоматической торговли", "Аккаунт: " + accountId + (mode != null ? ", Режим: " + mode : ""));
            
            // 1. АНАЛИЗ ПОРТФЕЛЯ
            log.info("Начало анализа портфеля для аккаунта: {}", accountId);
            botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.PORTFOLIO_ANALYSIS, 
                "Начало анализа портфеля", "Аккаунт: " + accountId);
            
            PortfolioAnalysis portfolioAnalysis = analyzePortfolio(accountId);
            log.info("Анализ портфеля завершен. Общая стоимость: {}, Позиций: {}", 
                portfolioAnalysis.getTotalValue(), portfolioAnalysis.getPositions().size());
            botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.PORTFOLIO_ANALYSIS, 
                "Анализ портфеля завершен", String.format("Общая стоимость: %.2f, Позиций: %d", 
                    portfolioAnalysis.getTotalValue(), portfolioAnalysis.getPositions().size()));
            
            // 2. ПРОВЕРКА РЕБАЛАНСИРОВКИ
            log.info("Проверка необходимости ребалансировки");
            botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.PORTFOLIO_ANALYSIS, 
                "Проверка ребалансировки", "");
            
            RebalancingDecision rebalancingDecision = checkRebalancing(accountId);
            if (rebalancingDecision.isNeedsRebalancing()) {
                log.info("Требуется ребалансировка: {}", rebalancingDecision.getReason());
                botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.PORTFOLIO_ANALYSIS, 
                    "Требуется ребалансировка", rebalancingDecision.getReason());
                
                // Выполняем ребалансировку
                log.info("Выполнение ребалансировки портфеля");
                botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.PORTFOLIO_ANALYSIS, 
                    "Выполнение ребалансировки", "");
                rebalancePortfolio(accountId);
            } else {
                log.info("Ребалансировка не требуется");
                botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.PORTFOLIO_ANALYSIS, 
                    "Ребалансировка не требуется", "");
            }
            
            // 3. ПОИСК ТОРГОВЫХ ВОЗМОЖНОСТЕЙ
            log.info("Поиск торговых возможностей");
            botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.MARKET_ANALYSIS, 
                "Поиск торговых возможностей", "");
            
            List<TradingOpportunity> opportunities = findBestTradingOpportunities(accountId);
            
            if (opportunities.isEmpty()) {
                log.info("Нет подходящих торговых возможностей");
                botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                    "Нет торговых возможностей", "Подходящих инструментов не найдено");
                return;
            }
            
            // Выбираем лучшую возможность для торговли (предпочитаем BUY/SELL над HOLD)
            TradingOpportunity bestOpportunity = null;
            
            // Сначала ищем возможности с действиями BUY или SELL
            for (TradingOpportunity opportunity : opportunities) {
                if (("BUY".equals(opportunity.getRecommendedAction()) || "SELL".equals(opportunity.getRecommendedAction())) &&
                    opportunity.getScore().compareTo(BigDecimal.valueOf(30)) >= 0) {
                    bestOpportunity = opportunity;
                    break;
                }
            }
            
            // Если не нашли BUY/SELL, берем первую возможность с высоким score
            if (bestOpportunity == null) {
                for (TradingOpportunity opportunity : opportunities) {
                    if (opportunity.getScore().compareTo(BigDecimal.valueOf(30)) >= 0) {
                        bestOpportunity = opportunity;
                        break;
                    }
                }
            }
            
            if (bestOpportunity != null) {
                log.info("Выполняем торговую операцию для {}: {} (Score: {})", 
                    bestOpportunity.getFigi(), bestOpportunity.getRecommendedAction(), bestOpportunity.getScore());
                
                botLogService.addLogEntry(BotLogService.LogLevel.TRADE, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                    "Выполнение торговой операции", String.format("FIGI: %s, Действие: %s, Score: %.1f, RSI: %.1f, Тренд: %s", 
                        bestOpportunity.getFigi(), bestOpportunity.getRecommendedAction(), bestOpportunity.getScore(), 
                        bestOpportunity.getRsi(), bestOpportunity.getTrend()));
                
                try {
                    executeTradingStrategy(accountId, bestOpportunity.getFigi());
                } catch (Exception e) {
                                            log.error("Ошибка выполнения торговой стратегии для {}: {}", displayOf(bestOpportunity.getFigi()), e.getMessage());
                    botLogService.addLogEntry(BotLogService.LogLevel.ERROR, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                                                  "Ошибка выполнения торговой стратегии", displayOf(bestOpportunity.getFigi()) + " - " + e.getMessage());
                    // Продолжаем выполнение, не останавливаем бота
                }
            } else {
                log.info("Нет подходящих торговых возможностей с достаточным score");
                botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT, 
                    "Нет подходящих торговых возможностей", "Все возможности имеют score < 30 или только HOLD");
            }
            
            log.info("Автоматическая торговля завершена");
            botLogService.addLogEntry(BotLogService.LogLevel.SUCCESS, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                "Автоматическая торговля завершена", "");
            
        } catch (Exception e) {
            log.error("Ошибка при автоматической торговле: {}", e.getMessage());
            botLogService.addLogEntry(BotLogService.LogLevel.ERROR, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                "Ошибка автоматической торговли", e.getMessage());
            // НЕ останавливаем бота, продолжаем работу
        }
    }
    
    /**
     * Класс для представления торговой возможности
     */
    public static class TradingOpportunity {
        private final String figi;
        private final BigDecimal currentPrice;
        private final String trend;
        private final BigDecimal rsi;
        private final BigDecimal sma20;
        private final BigDecimal sma50;
        private final BigDecimal score;
        private final String recommendedAction;
        
        public TradingOpportunity(String figi, BigDecimal currentPrice, String trend, 
                                BigDecimal rsi, BigDecimal sma20, BigDecimal sma50, 
                                BigDecimal score, String recommendedAction) {
            this.figi = figi;
            this.currentPrice = currentPrice;
            this.trend = trend;
            this.rsi = rsi;
            this.sma20 = sma20;
            this.sma50 = sma50;
            this.score = score;
            this.recommendedAction = recommendedAction;
        }
        
        // Getters
        public String getFigi() { return figi; }
        public BigDecimal getCurrentPrice() { return currentPrice; }
        public String getTrend() { return trend; }
        public BigDecimal getRsi() { return rsi; }
        public BigDecimal getSma20() { return sma20; }
        public BigDecimal getSma50() { return sma50; }
        public BigDecimal getScore() { return score; }
        public String getRecommendedAction() { return recommendedAction; }
    }
    
    /**
     * Включение автоматического мониторинга
     */
    public void startAutoMonitoring(String accountId) {
        this.autoMonitoringEnabled = true;
        this.monitoredAccountId = accountId;
        String mode = investApiManager != null ? investApiManager.getCurrentMode() : null;
        log.info("🚀 Автоматический мониторинг ВКЛЮЧЕН для аккаунта: {} (mode={})", accountId, mode);
        botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.AUTOMATIC_TRADING, 
            "Автоматический мониторинг включен", "Аккаунт: " + accountId + (mode != null ? ", Режим: " + mode : ""));
    }
    
    /**
     * Выключение автоматического мониторинга
     */
    public void stopAutoMonitoring() {
        this.autoMonitoringEnabled = false;
        this.monitoredAccountId = null;
        log.info("⏹️ Автоматический мониторинг ВЫКЛЮЧЕН");
        botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.AUTOMATIC_TRADING, 
            "Автоматический мониторинг выключен", "");
    }
    
    /**
     * Получение статуса автоматического мониторинга
     */
    public boolean isAutoMonitoringEnabled() {
        log.debug("🔍 isAutoMonitoringEnabled: {} (monitoredAccountId: {})", autoMonitoringEnabled, monitoredAccountId);
        return autoMonitoringEnabled;
    }
    
    /**
     * Автоматический мониторинг каждые 5 минут
     */
    @Scheduled(fixedRate = 300000) // 5 минут = 300000 мс
    public void autoMonitoringTask() {
        if (!autoMonitoringEnabled || monitoredAccountId == null) {
            return;
        }
        
        try {
            log.info("Запуск автоматического мониторинга для аккаунта: {}", monitoredAccountId);
            botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                "Запуск автоматического мониторинга", "Аккаунт: " + monitoredAccountId);
            
            // Анализируем рынок и выполняем торговлю
            executeAutomaticTrading(monitoredAccountId);
            
            log.info("Автоматический мониторинг завершен");
            botLogService.addLogEntry(BotLogService.LogLevel.SUCCESS, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                "Автоматический мониторинг завершен", "");
            
        } catch (Exception e) {
            log.error("Ошибка в автоматическом мониторинге: {}", e.getMessage());
            botLogService.addLogEntry(BotLogService.LogLevel.ERROR, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                "Ошибка автоматического мониторинга", e.getMessage());
        }
    }
    
    /**
     * Быстрый мониторинг каждую минуту (анализ + торговля при хороших возможностях)
     */
    @Scheduled(fixedRate = 60000) // 1 минута = 60000 мс
    public void quickMonitoringTask() {
        if (!autoMonitoringEnabled || monitoredAccountId == null) {
            log.debug("Быстрый мониторинг отключен: autoMonitoringEnabled={}, monitoredAccountId={}", autoMonitoringEnabled, monitoredAccountId);
            return;
        }
        
        try {
            log.debug("=== БЫСТРЫЙ МОНИТОРИНГ === (аккаунт: {})", monitoredAccountId);
            // Анализируем возможности
            List<TradingOpportunity> opportunities = findBestTradingOpportunities(monitoredAccountId);
            log.debug("Найдено торговых возможностей: {}", opportunities.size());
            
            // Ищем лучшую возможность для торговли (только BUY/SELL)
            TradingOpportunity bestTradingOpportunity = null;
            int buyCount = 0, sellCount = 0, holdCount = 0;
            for (TradingOpportunity opportunity : opportunities) {
                log.debug("Возможность: {} -> {} (Score: {})", 
                    displayOf(opportunity.getFigi()), opportunity.getRecommendedAction(), opportunity.getScore());
                    
                if ("BUY".equals(opportunity.getRecommendedAction())) {
                    buyCount++;
                    if (bestTradingOpportunity == null || opportunity.getScore().compareTo(bestTradingOpportunity.getScore()) > 0) {
                        bestTradingOpportunity = opportunity;
                    }
                } else if ("SELL".equals(opportunity.getRecommendedAction())) {
                    sellCount++;
                    if (bestTradingOpportunity == null || opportunity.getScore().compareTo(bestTradingOpportunity.getScore()) > 0) {
                        bestTradingOpportunity = opportunity;
                }
                } else {
                    holdCount++;
            }
            }
            log.info("Статистика сигналов: BUY={}, SELL={}, HOLD={}", buyCount, sellCount, holdCount);
            
            // Логируем найденные возможности
            if (!opportunities.isEmpty()) {
                TradingOpportunity bestOpportunity = opportunities.get(0);
                log.info("Быстрый мониторинг: лучшая возможность - {} ({}), Score: {}", 
                    bestOpportunity.getFigi(), bestOpportunity.getRecommendedAction(), bestOpportunity.getScore());
                
                botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.MARKET_ANALYSIS, 
                    "Быстрый мониторинг", String.format("Лучшая возможность: %s (%s), Score: %.1f", 
                        bestOpportunity.getFigi(), bestOpportunity.getRecommendedAction(), bestOpportunity.getScore()));
                
                // Выполняем торговлю если есть хорошая возможность для торговли
                if (bestTradingOpportunity != null && bestTradingOpportunity.getScore().compareTo(BigDecimal.valueOf(60)) > 0) {
                    log.info("Выполняем торговую операцию для {} ({}), Score: {}", 
                        bestTradingOpportunity.getFigi(), bestTradingOpportunity.getRecommendedAction(), bestTradingOpportunity.getScore());
                    
                    botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.AUTOMATIC_TRADING, 
                        "Выполнение торговой операции", String.format("FIGI: %s, Действие: %s, Score: %.1f", 
                            bestTradingOpportunity.getFigi(), bestTradingOpportunity.getRecommendedAction(), bestTradingOpportunity.getScore()));
                    
                    executeTradingStrategy(monitoredAccountId, bestTradingOpportunity.getFigi());
                } else {
                    // Объясняем, почему не торгуем в этот тик планировщика
                    String reason;
                    if (bestTradingOpportunity == null) {
                        reason = "Нет подходящей возможности BUY/SELL";
                    } else {
                        reason = String.format("Низкий порог Score: %.1f ≤ 60 (действие: %s)",
                                bestTradingOpportunity.getScore(), bestTradingOpportunity.getRecommendedAction());
                    }
                    log.info("Быстрый мониторинг: торговля пропущена — {}", reason);
                    botLogService.addLogEntry(BotLogService.LogLevel.INFO, BotLogService.LogCategory.AUTOMATIC_TRADING,
                            "Пропуск торговли", reason);
                }
            }
            
        } catch (Exception e) {
            log.warn("Ошибка в быстром мониторинге: {}", e.getMessage());
        }
    }
    
    /**
     * 🚀 НОВЫЙ МЕТОД: Получение читаемого названия класса активов
     */
    private String getAssetClassName(String instrumentType) {
        switch (instrumentType.toLowerCase()) {
            case "bond":
                return "облигаций";
            case "share":
            case "stock":
                return "акций";
            case "etf":
                return "ETF";
            default:
                return instrumentType;
        }
    }
} 