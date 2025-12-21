package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.perminov.model.AccountBalanceSnapshot;
import ru.perminov.model.Order;
import ru.perminov.model.PerformanceMetrics;
import ru.perminov.repository.AccountBalanceSnapshotRepository;
import ru.perminov.repository.OrderRepository;
import ru.perminov.repository.PerformanceMetricsRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис для расчета и хранения метрик производительности торговли
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PerformanceMetricsService {
    
    private final PerformanceMetricsRepository metricsRepository;
    private final OrderRepository orderRepository;
    private final AccountBalanceSnapshotRepository balanceSnapshotRepository;
    private final AccountService accountService;
    
    // Безрисковая ставка для расчета Sharpe/Sortino (годовая, в долях)
    private static final BigDecimal RISK_FREE_RATE = BigDecimal.valueOf(0.05); // 5% годовых
    
    /**
     * Расчет и сохранение метрик для аккаунта
     * @param accountId ID аккаунта
     * @param periodDays Количество дней для расчета (null = все доступные данные)
     */
    @Transactional
    public PerformanceMetrics calculateAndSaveMetrics(String accountId, Integer periodDays) {
        log.info("Расчет метрик производительности для аккаунта: {}, период: {} дней", accountId, periodDays);
        
        LocalDateTime periodStart = periodDays != null 
            ? LocalDateTime.now().minusDays(periodDays)
            : null;
        
        // Получаем историю ордеров
        List<Order> orders = getOrderHistory(accountId, periodStart);
        
        // Получаем историю балансов для расчета drawdown
        List<AccountBalanceSnapshot> balanceHistory = getBalanceHistory(accountId, periodStart);
        
        // Рассчитываем метрики
        PerformanceMetrics metrics = calculateMetrics(accountId, orders, balanceHistory, periodStart);
        
        // Сохраняем метрики
        metricsRepository.save(metrics);
        
        log.info("Метрики сохранены: totalTrades={}, winRate={}%, sharpe={}, maxDrawdown={}%",
            metrics.getTotalTrades(), 
            metrics.getWinRate() != null ? metrics.getWinRate().setScale(2, RoundingMode.HALF_UP) : 0,
            metrics.getSharpeRatio() != null ? metrics.getSharpeRatio().setScale(2, RoundingMode.HALF_UP) : 0,
            metrics.getMaxDrawdown() != null ? metrics.getMaxDrawdown().setScale(2, RoundingMode.HALF_UP) : 0);
        
        return metrics;
    }
    
    /**
     * Получить последние метрики для аккаунта
     */
    public Optional<PerformanceMetrics> getLatestMetrics(String accountId) {
        return metricsRepository.findFirstByAccountIdOrderByCalculatedAtDesc(accountId);
    }
    
    /**
     * Получить все метрики для аккаунта
     */
    public List<PerformanceMetrics> getAllMetrics(String accountId) {
        return metricsRepository.findByAccountIdOrderByCalculatedAtDesc(accountId);
    }
    
    /**
     * Периодический пересчет метрик для всех аккаунтов (каждый час)
     */
    @Scheduled(fixedRate = 3600_000) // Каждый час
    public void recalculateMetricsForAllAccounts() {
        try {
            log.info("Начало периодического пересчета метрик производительности");
            
            List<ru.tinkoff.piapi.contract.v1.Account> accounts = accountService.getAccounts();
            for (ru.tinkoff.piapi.contract.v1.Account account : accounts) {
                String accountId = account.getId();
                try {
                    // Рассчитываем метрики за последние 30 дней
                    calculateAndSaveMetrics(accountId, 30);
                    log.debug("Метрики пересчитаны для аккаунта: {}", accountId);
                } catch (Exception e) {
                    log.warn("Ошибка при пересчете метрик для аккаунта {}: {}", accountId, e.getMessage());
                }
            }
            
            log.info("Периодический пересчет метрик завершен");
        } catch (Exception e) {
            log.error("Ошибка при периодическом пересчете метрик: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Получить историю ордеров
     */
    private List<Order> getOrderHistory(String accountId, LocalDateTime periodStart) {
        if (periodStart != null) {
            return orderRepository.findByAccountId(accountId).stream()
                .filter(order -> order.getOrderDate() != null && order.getOrderDate().isAfter(periodStart))
                .filter(order -> "FILLED".equals(order.getStatus()) || "EXECUTED".equals(order.getStatus()))
                .sorted(Comparator.comparing(Order::getOrderDate))
                .collect(Collectors.toList());
        } else {
            return orderRepository.findByAccountId(accountId).stream()
                .filter(order -> "FILLED".equals(order.getStatus()) || "EXECUTED".equals(order.getStatus()))
                .sorted(Comparator.comparing(Order::getOrderDate))
                .collect(Collectors.toList());
        }
    }
    
    /**
     * Получить историю балансов
     */
    private List<AccountBalanceSnapshot> getBalanceHistory(String accountId, LocalDateTime periodStart) {
        if (periodStart != null) {
            return balanceSnapshotRepository.findByAccountIdAndCapturedAtBetweenOrderByCapturedAtAsc(
                accountId, periodStart, LocalDateTime.now());
        } else {
            // Получаем последние 48 снимков (12 часов при снимке каждые 15 минут)
            List<AccountBalanceSnapshot> snapshots = balanceSnapshotRepository.findTop48ByAccountIdOrderByCapturedAtDesc(accountId);
            return snapshots.stream()
                .sorted(Comparator.comparing(AccountBalanceSnapshot::getCapturedAt))
                .collect(Collectors.toList());
        }
    }
    
    /**
     * Расчет метрик на основе истории ордеров и балансов
     */
    private PerformanceMetrics calculateMetrics(String accountId, List<Order> orders, 
                                                List<AccountBalanceSnapshot> balanceHistory,
                                                LocalDateTime periodStart) {
        
        PerformanceMetrics metrics = PerformanceMetrics.builder()
            .accountId(accountId)
            .calculatedAt(LocalDateTime.now())
            .periodStart(periodStart)
            .periodEnd(LocalDateTime.now())
            .periodDays(periodStart != null ? (int) ChronoUnit.DAYS.between(periodStart, LocalDateTime.now()) : null)
            .build();
        
        if (orders.isEmpty()) {
            // Нет данных для расчета
            metrics.setTotalTrades(0);
            metrics.setProfitableTrades(0);
            metrics.setLosingTrades(0);
            return metrics;
        }
        
        // Группируем ордера по FIGI для расчета PnL по позициям
        Map<String, List<Order>> ordersByFigi = orders.stream()
            .collect(Collectors.groupingBy(Order::getFigi));
        
        // Рассчитываем PnL для каждой позиции
        List<TradePnL> tradePnLs = new ArrayList<>();
        for (Map.Entry<String, List<Order>> entry : ordersByFigi.entrySet()) {
            String figi = entry.getKey();
            List<Order> figiOrders = entry.getValue();
            
            // Упрощенный расчет: считаем, что BUY открывает позицию, SELL закрывает
            // В реальности нужно учитывать частичные исполнения и среднюю цену входа
            BigDecimal totalBuyCost = BigDecimal.ZERO;
            BigDecimal totalBuyLots = BigDecimal.ZERO;
            BigDecimal totalSellRevenue = BigDecimal.ZERO;
            BigDecimal totalSellLots = BigDecimal.ZERO;
            
            for (Order order : figiOrders) {
                if ("BUY".equalsIgnoreCase(order.getOperation()) && order.getPrice() != null && order.getExecutedLots() != null) {
                    totalBuyCost = totalBuyCost.add(order.getPrice().multiply(order.getExecutedLots()));
                    totalBuyLots = totalBuyLots.add(order.getExecutedLots());
                } else if ("SELL".equalsIgnoreCase(order.getOperation()) && order.getPrice() != null && order.getExecutedLots() != null) {
                    totalSellRevenue = totalSellRevenue.add(order.getPrice().multiply(order.getExecutedLots()));
                    totalSellLots = totalSellLots.add(order.getExecutedLots());
                }
            }
            
            // Если есть и покупки, и продажи, считаем PnL
            if (totalBuyLots.compareTo(BigDecimal.ZERO) > 0 && totalSellLots.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal avgBuyPrice = totalBuyCost.divide(totalBuyLots, 4, RoundingMode.HALF_UP);
                BigDecimal avgSellPrice = totalSellRevenue.divide(totalSellLots, 4, RoundingMode.HALF_UP);
                BigDecimal closedLots = totalBuyLots.min(totalSellLots);
                BigDecimal pnl = avgSellPrice.subtract(avgBuyPrice).multiply(closedLots);
                
                // Вычитаем комиссии (примерно 0.05% за сделку)
                BigDecimal commission = totalBuyCost.add(totalSellRevenue).multiply(BigDecimal.valueOf(0.0005));
                pnl = pnl.subtract(commission);
                
                tradePnLs.add(new TradePnL(figi, pnl, avgBuyPrice, avgSellPrice));
            }
        }
        
        // Основные метрики
        metrics.setTotalTrades(tradePnLs.size());
        
        long profitableTrades = tradePnLs.stream()
            .filter(t -> t.pnl.compareTo(BigDecimal.ZERO) > 0)
            .count();
        metrics.setProfitableTrades((int) profitableTrades);
        
        long losingTrades = tradePnLs.stream()
            .filter(t -> t.pnl.compareTo(BigDecimal.ZERO) < 0)
            .count();
        metrics.setLosingTrades((int) losingTrades);
        
        // Win Rate
        if (tradePnLs.size() > 0) {
            BigDecimal winRate = BigDecimal.valueOf(profitableTrades)
                .divide(BigDecimal.valueOf(tradePnLs.size()), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
            metrics.setWinRate(winRate);
        }
        
        // Total PnL
        BigDecimal totalPnL = tradePnLs.stream()
            .map(t -> t.pnl)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        metrics.setTotalPnL(totalPnL);
        
        // Average Win
        if (profitableTrades > 0) {
            BigDecimal avgWin = tradePnLs.stream()
                .filter(t -> t.pnl.compareTo(BigDecimal.ZERO) > 0)
                .map(t -> t.pnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(profitableTrades), 4, RoundingMode.HALF_UP);
            metrics.setAverageWin(avgWin);
        }
        
        // Average Loss
        if (losingTrades > 0) {
            BigDecimal avgLoss = tradePnLs.stream()
                .filter(t -> t.pnl.compareTo(BigDecimal.ZERO) < 0)
                .map(t -> t.pnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(losingTrades), 4, RoundingMode.HALF_UP);
            metrics.setAverageLoss(avgLoss);
        }
        
        // Average Win/Loss Ratio
        if (metrics.getAverageLoss() != null && metrics.getAverageLoss().compareTo(BigDecimal.ZERO) != 0) {
            BigDecimal winLossRatio = metrics.getAverageWin() != null 
                ? metrics.getAverageWin().divide(metrics.getAverageLoss().abs(), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
            metrics.setAverageWinLossRatio(winLossRatio);
        }
        
        // Profit Factor
        BigDecimal totalProfit = tradePnLs.stream()
            .filter(t -> t.pnl.compareTo(BigDecimal.ZERO) > 0)
            .map(t -> t.pnl)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalLoss = tradePnLs.stream()
            .filter(t -> t.pnl.compareTo(BigDecimal.ZERO) < 0)
            .map(t -> t.pnl.abs())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        if (totalLoss.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal profitFactor = totalProfit.divide(totalLoss, 4, RoundingMode.HALF_UP);
            metrics.setProfitFactor(profitFactor);
        }
        
        // Метрики на основе балансов
        if (!balanceHistory.isEmpty()) {
            // Total Return
            AccountBalanceSnapshot first = balanceHistory.get(0);
            AccountBalanceSnapshot last = balanceHistory.get(balanceHistory.size() - 1);
            
            if (first.getTotalValue() != null && last.getTotalValue() != null 
                && first.getTotalValue().compareTo(BigDecimal.ZERO) > 0) {
                
                BigDecimal totalReturn = last.getTotalValue().subtract(first.getTotalValue());
                metrics.setTotalReturn(totalReturn);
                
                BigDecimal totalReturnPct = totalReturn.divide(first.getTotalValue(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
                metrics.setTotalReturnPct(totalReturnPct);
            }
            
            // Max Drawdown
            BigDecimal maxDrawdown = calculateMaxDrawdown(balanceHistory);
            metrics.setMaxDrawdown(maxDrawdown);
            
            // Volatility
            BigDecimal volatility = calculateVolatility(balanceHistory);
            metrics.setVolatility(volatility);
            
            // Sharpe Ratio
            BigDecimal sharpeRatio = calculateSharpeRatio(balanceHistory);
            metrics.setSharpeRatio(sharpeRatio);
            
            // Sortino Ratio
            BigDecimal sortinoRatio = calculateSortinoRatio(balanceHistory);
            metrics.setSortinoRatio(sortinoRatio);
        }
        
        return metrics;
    }
    
    /**
     * Расчет максимальной просадки
     */
    private BigDecimal calculateMaxDrawdown(List<AccountBalanceSnapshot> balanceHistory) {
        if (balanceHistory.size() < 2) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        BigDecimal peak = balanceHistory.get(0).getTotalValue();
        
        for (AccountBalanceSnapshot snapshot : balanceHistory) {
            BigDecimal value = snapshot.getTotalValue();
            if (value == null) continue;
            
            if (value.compareTo(peak) > 0) {
                peak = value;
            }
            
            if (peak.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal drawdown = peak.subtract(value).divide(peak, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
                if (drawdown.compareTo(maxDrawdown) > 0) {
                    maxDrawdown = drawdown;
                }
            }
        }
        
        return maxDrawdown;
    }
    
    /**
     * Расчет волатильности (стандартное отклонение доходности)
     */
    private BigDecimal calculateVolatility(List<AccountBalanceSnapshot> balanceHistory) {
        if (balanceHistory.size() < 2) {
            return BigDecimal.ZERO;
        }
        
        // Рассчитываем дневные доходности
        List<BigDecimal> returns = new ArrayList<>();
        for (int i = 1; i < balanceHistory.size(); i++) {
            BigDecimal prevValue = balanceHistory.get(i - 1).getTotalValue();
            BigDecimal currValue = balanceHistory.get(i).getTotalValue();
            
            if (prevValue != null && currValue != null && prevValue.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal returnPct = currValue.subtract(prevValue).divide(prevValue, 6, RoundingMode.HALF_UP);
                returns.add(returnPct);
            }
        }
        
        if (returns.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        // Средняя доходность
        BigDecimal meanReturn = returns.stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(returns.size()), 6, RoundingMode.HALF_UP);
        
        // Дисперсия
        BigDecimal variance = returns.stream()
            .map(r -> r.subtract(meanReturn).pow(2))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(returns.size()), 6, RoundingMode.HALF_UP);
        
        // Стандартное отклонение (волатильность)
        double volatility = Math.sqrt(variance.doubleValue());
        return BigDecimal.valueOf(volatility).multiply(BigDecimal.valueOf(100)); // В процентах
    }
    
    /**
     * Расчет коэффициента Шарпа
     */
    private BigDecimal calculateSharpeRatio(List<AccountBalanceSnapshot> balanceHistory) {
        if (balanceHistory.size() < 2) {
            return BigDecimal.ZERO;
        }
        
        // Рассчитываем дневные доходности
        List<BigDecimal> returns = new ArrayList<>();
        for (int i = 1; i < balanceHistory.size(); i++) {
            BigDecimal prevValue = balanceHistory.get(i - 1).getTotalValue();
            BigDecimal currValue = balanceHistory.get(i).getTotalValue();
            
            if (prevValue != null && currValue != null && prevValue.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal returnPct = currValue.subtract(prevValue).divide(prevValue, 6, RoundingMode.HALF_UP);
                returns.add(returnPct);
            }
        }
        
        if (returns.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        // Средняя дневная доходность
        BigDecimal meanReturn = returns.stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(returns.size()), 6, RoundingMode.HALF_UP);
        
        // Годовая доходность (предполагаем 252 торговых дня)
        BigDecimal annualReturn = meanReturn.multiply(BigDecimal.valueOf(252));
        
        // Волатильность
        BigDecimal volatility = calculateVolatility(balanceHistory);
        BigDecimal annualVolatility = volatility.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(Math.sqrt(252)));
        
        if (annualVolatility.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        // Sharpe Ratio = (доходность - безрисковая ставка) / волатильность
        BigDecimal excessReturn = annualReturn.subtract(RISK_FREE_RATE);
        return excessReturn.divide(annualVolatility, 4, RoundingMode.HALF_UP);
    }
    
    /**
     * Расчет коэффициента Сортино (учитывает только волатильность убытков)
     */
    private BigDecimal calculateSortinoRatio(List<AccountBalanceSnapshot> balanceHistory) {
        if (balanceHistory.size() < 2) {
            return BigDecimal.ZERO;
        }
        
        // Рассчитываем дневные доходности
        List<BigDecimal> returns = new ArrayList<>();
        for (int i = 1; i < balanceHistory.size(); i++) {
            BigDecimal prevValue = balanceHistory.get(i - 1).getTotalValue();
            BigDecimal currValue = balanceHistory.get(i).getTotalValue();
            
            if (prevValue != null && currValue != null && prevValue.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal returnPct = currValue.subtract(prevValue).divide(prevValue, 6, RoundingMode.HALF_UP);
                returns.add(returnPct);
            }
        }
        
        if (returns.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        // Средняя дневная доходность
        BigDecimal meanReturn = returns.stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(returns.size()), 6, RoundingMode.HALF_UP);
        
        // Годовая доходность
        BigDecimal annualReturn = meanReturn.multiply(BigDecimal.valueOf(252));
        
        // Downside deviation (только отрицательные доходности)
        List<BigDecimal> negativeReturns = returns.stream()
            .filter(r -> r.compareTo(BigDecimal.ZERO) < 0)
            .collect(Collectors.toList());
        
        if (negativeReturns.isEmpty()) {
            // Нет убытков - Sortino = очень высокий
            return BigDecimal.valueOf(999);
        }
        
        BigDecimal downsideVariance = negativeReturns.stream()
            .map(r -> r.pow(2))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(negativeReturns.size()), 6, RoundingMode.HALF_UP);
        
        BigDecimal downsideDeviation = BigDecimal.valueOf(Math.sqrt(downsideVariance.doubleValue()))
            .multiply(BigDecimal.valueOf(Math.sqrt(252)));
        
        if (downsideDeviation.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        // Sortino Ratio = (доходность - безрисковая ставка) / downside deviation
        BigDecimal excessReturn = annualReturn.subtract(RISK_FREE_RATE);
        return excessReturn.divide(downsideDeviation, 4, RoundingMode.HALF_UP);
    }
    
    /**
     * Вспомогательный класс для хранения PnL сделки
     */
    @SuppressWarnings("unused")
    private static class TradePnL {
        final String figi;
        final BigDecimal pnl;
        final BigDecimal entryPrice;
        final BigDecimal exitPrice;
        
        TradePnL(String figi, BigDecimal pnl, BigDecimal entryPrice, BigDecimal exitPrice) {
            this.figi = figi;
            this.pnl = pnl;
            this.entryPrice = entryPrice;
            this.exitPrice = exitPrice;
        }
    }
}

