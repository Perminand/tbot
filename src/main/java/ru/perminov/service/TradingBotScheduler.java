package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.contract.v1.CandleInterval;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradingBotScheduler {
    
    private final PortfolioManagementService portfolioManagementService;
    private final MarketAnalysisService marketAnalysisService;
    private final AccountService accountService;
    private final InstrumentService instrumentService;
    
    // Список инструментов для мониторинга (можно вынести в конфигурацию)
    private final List<String> monitoredInstruments = List.of(
        "BBG000B9XRY4", // Apple
        "BBG000B9XRY4", // Microsoft
        "BBG000B9XRY4"  // Google
    );
    
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
     * Почасовая проверка торговых сигналов
     * Выполняется каждый час в рабочее время (9:00-18:00)
     */
    @Scheduled(cron = "0 0 9-18 * * MON-FRI")
    public void hourlyTradingSignals() {
        log.info("Запуск почасовой проверки торговых сигналов");
        
        try {
            List<String> accountIds = getAccountIds();
            
            for (String accountId : accountIds) {
                for (String figi : monitoredInstruments) {
                    // Анализ тренда для каждого инструмента
                    MarketAnalysisService.TrendAnalysis trend = 
                        marketAnalysisService.analyzeTrend(figi, CandleInterval.CANDLE_INTERVAL_HOUR);
                    
                    log.info("Анализ {}: тренд = {}, сигнал = {}, цена = {}", 
                        figi, trend.getTrend(), trend.getSignal(), trend.getCurrentPrice());
                    
                    // Выполнение торговой стратегии
                    portfolioManagementService.executeTradingStrategy(accountId, figi);
                }
            }
        } catch (Exception e) {
            log.error("Ошибка при почасовой проверке торговых сигналов: {}", e.getMessage());
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