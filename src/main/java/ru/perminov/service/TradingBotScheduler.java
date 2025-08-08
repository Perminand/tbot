package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.contract.v1.CandleInterval;

import java.util.List;
import java.util.Map;
import ru.perminov.dto.ShareDto;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradingBotScheduler {
    
    private final PortfolioManagementService portfolioManagementService;
    private final MarketAnalysisService marketAnalysisService;
    private final AccountService accountService;
    private final InstrumentService instrumentService;
    private final DynamicInstrumentService dynamicInstrumentService;
    private final SmartAnalysisService smartAnalysisService;
    
    // Список инструментов для мониторинга (можно вынести в конфигурацию)
    private final List<String> monitoredInstruments = List.of(
        "BBG000B9XRY4", // Apple
        "BBG000B9XRY4", // Microsoft
        "BBG000B9XRY4"  // Google
    );
    
    /**
     * Умный быстрый мониторинг каждые 30 секунд
     * Использует умную стратегию анализа для выбора инструментов
     */
    @Scheduled(fixedRate = 30000) // 30 секунд
    public void smartQuickMonitoring() {
        log.info("Запуск умного быстрого мониторинга (30 сек)");
        
        try {
            List<String> accountIds = getAccountIds();
            
            for (String accountId : accountIds) {
                // Получаем инструменты для быстрого анализа через умную стратегию
                List<ShareDto> instrumentsToAnalyze = smartAnalysisService.getInstrumentsForQuickAnalysis(accountId);
                
                log.info("Умный быстрый анализ {} инструментов", instrumentsToAnalyze.size());
                
                for (ShareDto instrument : instrumentsToAnalyze) {
                    try {
                        String figi = instrument.getFigi();
                        
                        // Быстрый анализ тренда (15-минутные свечи)
                        MarketAnalysisService.TrendAnalysis trend = 
                            marketAnalysisService.analyzeTrend(figi, CandleInterval.CANDLE_INTERVAL_15_MIN);
                        
                        log.info("Быстрый анализ {}: тренд = {}, сигнал = {}, цена = {}", 
                            figi, trend.getTrend(), trend.getSignal(), trend.getCurrentPrice());
                        
                        // Обновляем приоритет инструмента на основе анализа
                        updateInstrumentPriority(figi, trend);
                        
                        // Выполнение торговой стратегии
                        portfolioManagementService.executeTradingStrategy(accountId, figi);
                        
                        // Минимальная задержка между запросами
                        Thread.sleep(100); // 100ms задержка
                        
                    } catch (Exception e) {
                        log.error("Ошибка быстрого анализа инструмента {}: {}", instrument.getFigi(), e.getMessage());
                        // Продолжаем с следующим инструментом, не останавливаем выполнение
                    }
                }
            }
        } catch (Exception e) {
            log.error("Ошибка при умном быстром мониторинге: {}", e.getMessage());
            // НЕ останавливаем планировщик, продолжаем работу
        }
    }
    
    /**
     * Умный полный мониторинг каждые 2 минуты
     * Использует умную стратегию анализа для выбора инструментов
     */
    @Scheduled(fixedRate = 120000) // 2 минуты
    public void smartFullMonitoring() {
        log.info("Запуск умного полного мониторинга (2 мин)");
        
        try {
            List<String> accountIds = getAccountIds();
            
            for (String accountId : accountIds) {
                // Получаем инструменты для полного анализа через умную стратегию
                List<ShareDto> instrumentsToAnalyze = smartAnalysisService.getInstrumentsForFullAnalysis(accountId);
                
                log.info("Умный полный анализ {} инструментов", instrumentsToAnalyze.size());
                
                for (ShareDto instrument : instrumentsToAnalyze) {
                    try {
                        String figi = instrument.getFigi();
                        
                        // Полный анализ тренда (часовые свечи)
                        MarketAnalysisService.TrendAnalysis trend = 
                            marketAnalysisService.analyzeTrend(figi, CandleInterval.CANDLE_INTERVAL_HOUR);
                        
                        log.info("Полный анализ {}: тренд = {}, сигнал = {}, цена = {}", 
                            figi, trend.getTrend(), trend.getSignal(), trend.getCurrentPrice());
                        
                        // Обновляем приоритет инструмента на основе анализа
                        updateInstrumentPriority(figi, trend);
                        
                        // Выполнение торговой стратегии
                        portfolioManagementService.executeTradingStrategy(accountId, figi);
                        
                        // Задержка между запросами
                        Thread.sleep(200); // 200ms задержка
                        
                    } catch (Exception e) {
                        log.error("Ошибка полного анализа инструмента {}: {}", instrument.getFigi(), e.getMessage());
                        // Продолжаем с следующим инструментом, не останавливаем выполнение
                    }
                }
            }
        } catch (Exception e) {
            log.error("Ошибка при умном полном мониторинге: {}", e.getMessage());
            // НЕ останавливаем планировщик, продолжаем работу
        }
    }
    
    /**
     * Ежедневная проверка портфеля и ребалансировка
     * Выполняется каждый день в 9:00
     */
    @Scheduled(cron = "0 0 9 * * ?")
    public void dailyPortfolioCheck() {
        log.info("Запуск ежедневной проверки портфеля");
        
        try {
            List<String> accountIds = getAccountIds();
            
            for (String accountId : accountIds) {
                // Проверка необходимости ребалансировки
                PortfolioManagementService.RebalancingDecision decision = 
                    portfolioManagementService.checkRebalancing(accountId);
                
                if (decision.isNeedsRebalancing()) {
                    log.info("Требуется ребалансировка для аккаунта {}: {}", accountId, decision.getReason());
                    portfolioManagementService.rebalancePortfolio(accountId);
                } else {
                    log.info("Портфель сбалансирован для аккаунта {}: {}", accountId, decision.getReason());
                }
            }
        } catch (Exception e) {
            log.error("Ошибка при ежедневной проверке портфеля: {}", e.getMessage());
        }
    }
    
    /**
     * Еженедельная оптимизация стратегии
     * Выполняется каждое воскресенье в 20:00
     */
    @Scheduled(cron = "0 0 20 * * SUN")
    public void weeklyStrategyOptimization() {
        log.info("Запуск еженедельной оптимизации стратегии");
        
        try {
            // Получаем статистику умного анализа
            Map<String, Object> stats = smartAnalysisService.getAnalysisStats();
            log.info("Статистика умного анализа: {}", stats);
            
            // Проверяем состояние резервного режима
            Map<String, Object> fallbackInfo = smartAnalysisService.getFallbackModeInfo();
            log.info("Информация о резервном режиме: {}", fallbackInfo);
            
            // Здесь можно добавить логику для:
            // - Анализа эффективности стратегии
            // - Корректировки параметров
            // - Обновления списка мониторинга
            // - Генерации отчетов
            
            log.info("Еженедельная оптимизация завершена");
        } catch (Exception e) {
            log.error("Ошибка при еженедельной оптимизации: {}", e.getMessage());
        }
    }
    
    /**
     * Получение списка аккаунтов
     */
    private List<String> getAccountIds() {
        try {
            return accountService.getAccounts().stream()
                .map(account -> account.getId())
                .toList();
        } catch (Exception e) {
            log.error("Ошибка при получении списка аккаунтов: {}", e.getMessage());
            return List.of();
        }
    }
    
    /**
     * Обновление приоритета инструмента на основе анализа
     */
    private void updateInstrumentPriority(String figi, MarketAnalysisService.TrendAnalysis trend) {
        int priority = 0;
        
        // Базовый приоритет
        switch (trend.getTrend()) {
            case BULLISH:
                priority = 80;
                break;
            case BEARISH:
                priority = 60;
                break;
            case SIDEWAYS:
                priority = 40;
                break;
            case UNKNOWN:
                priority = 20;
                break;
        }
        
        // Дополнительные бонусы за сигналы
        if (trend.getSignal().contains("сильный") || trend.getSignal().contains("четкий")) {
            priority += 20;
        }
        
        smartAnalysisService.updateInstrumentPriority(figi, priority);
    }
    
    /**
     * Ручной запуск анализа портфеля
     */
    public void manualPortfolioAnalysis(String accountId) {
        log.info("Ручной запуск анализа портфеля для аккаунта: {}", accountId);
        
        try {
            PortfolioManagementService.PortfolioAnalysis analysis = 
                portfolioManagementService.analyzePortfolio(accountId);
            
            log.info("Анализ портфеля завершен. Общая стоимость: {}", analysis.getTotalValue());
            log.info("Распределение по типам активов: {}", analysis.getAllocationPercentages());
            
        } catch (Exception e) {
            log.error("Ошибка при ручном анализе портфеля: {}", e.getMessage());
        }
    }
    
    /**
     * Ручной запуск торговой стратегии для конкретного инструмента
     */
    public void manualTradingStrategy(String accountId, String figi) {
        log.info("Ручной запуск торговой стратегии для {} в аккаунте {}", figi, accountId);
        
        try {
            portfolioManagementService.executeTradingStrategy(accountId, figi);
        } catch (Exception e) {
            log.error("Ошибка при ручном запуске торговой стратегии: {}", e.getMessage());
        }
    }
} 