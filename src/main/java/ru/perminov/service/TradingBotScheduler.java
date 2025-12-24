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
    private final TradingModeProtectionService protectionService;
    
    // Список инструментов для мониторинга (можно вынести в конфигурацию)
    private final List<String> monitoredInstruments = List.of(
        "BBG000B9XRY4", // Apple
        "BBG000B9XRY4", // Microsoft
        "BBG000B9XRY4"  // Google
    );
    
    /**
     * Умный быстрый мониторинг каждые 5 минут (ОПТИМИЗАЦИЯ ДЛЯ СНИЖЕНИЯ КОМИССИЙ)
     * Использует умную стратегию анализа для выбора инструментов
     */
    @Scheduled(fixedRate = 300000) // 5 минут (было 30 сек)
    public void smartQuickMonitoring() {
        log.info("Запуск умного быстрого мониторинга (5 мин)");
        
        // Проверяем защиту режима торговли
        if (!protectionService.validateTradingMode()) {
            log.error("❌ ОСТАНОВКА МОНИТОРИНГА: Обнаружена рассинхронизация режимов торговли");
            return;
        }
        
        // Устанавливаем флаг активной торговли
        protectionService.setTradingActive(true);
        
        try {
            List<String> accountIds = getAccountIds();
            
            for (String accountId : accountIds) {
                // ПРИОРИТЕТ: Сначала проверяем все шорт позиции для закрытия
                portfolioManagementService.checkAndCloseShortPositions(accountId);
                
                // Получаем инструменты для быстрого анализа через умную стратегию
                List<ShareDto> instrumentsToAnalyze = smartAnalysisService.getInstrumentsForQuickAnalysis(accountId);
                
                log.info("Умный быстрый анализ {} инструментов", instrumentsToAnalyze.size());
                
                for (ShareDto instrument : instrumentsToAnalyze) {
                    try {
                        String figi = instrument.getFigi();
                        
                        // Пропускаем заблокированные по ликвидности инструменты
                        if (portfolioManagementService.isLiquidityBlocked(figi)) {
                            long minutesLeft = portfolioManagementService.getLiquidityBlockRemainingMinutes(figi);
                            log.debug("Пропускаем {} из быстрого анализа - заблокирован по ликвидности (осталось ~{} мин)", 
                                figi, minutesLeft);
                            continue;
                        }
                        
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
                        log.error("Ошибка быстрого анализа инструмента: {}", e.getMessage());
                        // Продолжаем с следующим инструментом, не останавливаем выполнение
                    }
                }
            }
        } catch (Exception e) {
            log.error("Ошибка при умном быстром мониторинге: {}", e.getMessage());
            // НЕ останавливаем планировщик, продолжаем работу
        } finally {
            // Снимаем флаг активной торговли
            protectionService.setTradingActive(false);
        }
    }
    
    /**
     * Умный полный мониторинг каждые 15 минут (ОПТИМИЗАЦИЯ ДЛЯ СНИЖЕНИЯ КОМИССИЙ)
     * Использует умную стратегию анализа для выбора инструментов
     */
    @Scheduled(fixedRate = 900000) // 15 минут (было 2 мин)
    public void smartFullMonitoring() {
        log.info("Запуск умного полного мониторинга (15 мин)");
        
        // Проверяем защиту режима торговли
        if (!protectionService.validateTradingMode()) {
            log.error("❌ ОСТАНОВКА МОНИТОРИНГА: Обнаружена рассинхронизация режимов торговли");
            return;
        }
        
        // Устанавливаем флаг активной торговли
        protectionService.setTradingActive(true);
        
        try {
            List<String> accountIds = getAccountIds();
            
            for (String accountId : accountIds) {
                // Получаем инструменты для полного анализа через умную стратегию
                List<ShareDto> instrumentsToAnalyze = smartAnalysisService.getInstrumentsForFullAnalysis(accountId);
                
                log.info("Умный полный анализ {} инструментов", instrumentsToAnalyze.size());
                
                for (ShareDto instrument : instrumentsToAnalyze) {
                    try {
                        String figi = instrument.getFigi();
                        
                        // Пропускаем заблокированные по ликвидности инструменты
                        if (portfolioManagementService.isLiquidityBlocked(figi)) {
                            long minutesLeft = portfolioManagementService.getLiquidityBlockRemainingMinutes(figi);
                            log.debug("Пропускаем {} из полного анализа - заблокирован по ликвидности (осталось ~{} мин)", 
                                figi, minutesLeft);
                            continue;
                        }
                        
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
                        log.error("Ошибка полного анализа инструмента: {}", e.getMessage());
                        // Продолжаем с следующим инструментом, не останавливаем выполнение
                    }
                }
            }
        } catch (Exception e) {
            log.error("Ошибка при умном полном мониторинге: {}", e.getMessage());
            // НЕ останавливаем планировщик, продолжаем работу
        } finally {
            // Снимаем флаг активной торговли
            protectionService.setTradingActive(false);
        }
    }
    
    /**
     * Ежедневная проверка портфеля и ребалансировка
     * Выполняется каждый день в 9:00
     */
    @Scheduled(cron = "0 0 9 * * ?")
    public void dailyPortfolioCheck() {
        log.info("Запуск ежедневной проверки портфеля");
        
        // Проверяем защиту режима торговли
        if (!protectionService.validateTradingMode()) {
            log.error("❌ ОСТАНОВКА ПРОВЕРКИ ПОРТФЕЛЯ: Обнаружена рассинхронизация режимов торговли");
            return;
        }
        
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
        
        // Проверяем защиту режима торговли
        if (!protectionService.validateTradingMode()) {
            log.error("❌ ОСТАНОВКА ОПТИМИЗАЦИИ: Обнаружена рассинхронизация режимов торговли");
            return;
        }
        
        try {
            // Получаем статистику умного анализа
            Map<String, Object> stats = smartAnalysisService.getAnalysisStats();
            log.info("Статистика умного анализа: {}", stats);
            
            // Проверяем состояние резервного режима
            Map<String, Object> fallbackInfo = smartAnalysisService.getFallbackModeInfo();
            log.info("Информация о резервном режиме: {}", fallbackInfo);
            
            // Проверяем статус защиты режима торговли
            String protectionStatus = protectionService.getProtectionStatus();
            log.info("Статус защиты режима торговли: {}", protectionStatus);
            
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
        try {
            // Логика обновления приоритета на основе тренда
            // Можно добавить в SmartAnalysisService
                                    log.debug("Обновление приоритета: тренд = {}", trend.getTrend());
        } catch (Exception e) {
                                    log.warn("Ошибка обновления приоритета: {}", e.getMessage());
        }
    }
    
    /**
     * Ручной запуск торговой стратегии для конкретного инструмента
     */
    public void manualTradingStrategy(String accountId, String figi) {
                                log.info("Ручной запуск торговой стратегии в аккаунте {}", accountId);
        
        // Проверяем защиту режима торговли
        if (!protectionService.validateTradingMode()) {
            log.error("❌ ОСТАНОВКА РУЧНОЙ ТОРГОВЛИ: Обнаружена рассинхронизация режимов торговли");
            return;
        }
        
        // Устанавливаем флаг активной торговли
        protectionService.setTradingActive(true);
        
        try {
            portfolioManagementService.executeTradingStrategy(accountId, figi);
        } catch (Exception e) {
            log.error("Ошибка при ручном запуске торговой стратегии: {}", e.getMessage());
        } finally {
            // Снимаем флаг активной торговли
            protectionService.setTradingActive(false);
        }
    }
} 