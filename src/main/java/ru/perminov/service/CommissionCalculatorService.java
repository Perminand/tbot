package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommissionCalculatorService {
    
    private final TradingSettingsService tradingSettingsService;
    
    // Стандартные комиссии T-Bank (можно вынести в настройки)
    private static final BigDecimal STOCK_COMMISSION_PCT = new BigDecimal("0.06"); // 0.06%
    private static final BigDecimal MIN_COMMISSION_RUB = new BigDecimal("0.30"); // минимум 30 копеек
    
    /**
     * Расчет комиссии для сделки
     */
    public BigDecimal calculateCommission(BigDecimal tradeAmount, String instrumentType) {
        try {
            BigDecimal commissionPct = getCommissionRate(instrumentType);
            BigDecimal commission = tradeAmount.multiply(commissionPct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            
            // Применяем минимальную комиссию
            commission = commission.max(MIN_COMMISSION_RUB);
            
            log.debug("Комиссия для сделки на {} ({}): {}% = {} руб", tradeAmount, instrumentType, commissionPct, commission);
            return commission;
            
        } catch (Exception e) {
            log.warn("Ошибка расчета комиссии: {}", e.getMessage());
            return MIN_COMMISSION_RUB; // Возвращаем минимальную комиссию при ошибке
        }
    }
    
    /**
     * Расчет полной комиссии для цикла торговли (открытие + закрытие)
     */
    public BigDecimal calculateFullCycleCommission(BigDecimal tradeAmount, String instrumentType) {
        BigDecimal singleCommission = calculateCommission(tradeAmount, instrumentType);
        BigDecimal fullCommission = singleCommission.multiply(BigDecimal.valueOf(2)); // открытие + закрытие
        
        log.debug("Полная комиссия цикла для {} ({}): {} руб (открытие + закрытие)", 
            tradeAmount, instrumentType, fullCommission);
        return fullCommission;
    }
    
    /**
     * Проверка прибыльности сделки с учетом комиссий
     */
    public boolean isProfitableAfterCommissions(BigDecimal expectedProfit, BigDecimal tradeAmount, String instrumentType) {
        BigDecimal fullCommission = calculateFullCycleCommission(tradeAmount, instrumentType);
        boolean profitable = expectedProfit.compareTo(fullCommission) > 0;
        
        log.debug("Проверка прибыльности: ожидаемая прибыль {} vs комиссии {} = {}", 
            expectedProfit, fullCommission, profitable ? "ПРИБЫЛЬНО" : "УБЫТОЧНО");
        
        return profitable;
    }
    
    /**
     * Расчет минимального движения цены для безубыточности
     */
    public BigDecimal calculateBreakevenPriceMove(BigDecimal currentPrice, int lots, String instrumentType) {
        BigDecimal tradeAmount = currentPrice.multiply(BigDecimal.valueOf(lots));
        BigDecimal fullCommission = calculateFullCycleCommission(tradeAmount, instrumentType);
        
        // Минимальное движение цены = комиссии / количество лотов
        BigDecimal minPriceMove = fullCommission.divide(BigDecimal.valueOf(lots), 4, RoundingMode.HALF_UP);
        
        log.info("💰 Для безубыточности {} лотов по {} нужно движение цены минимум на {} руб", 
            lots, currentPrice, minPriceMove);
        
        return minPriceMove;
    }
    
    /**
     * Получение ставки комиссии для типа инструмента
     */
    private BigDecimal getCommissionRate(String instrumentType) {
        switch (instrumentType.toLowerCase()) {
            case "share":
            case "stock":
                return new BigDecimal(tradingSettingsService.getString("commission.stock.pct", STOCK_COMMISSION_PCT.toString()));
            case "bond":
                return new BigDecimal(tradingSettingsService.getString("commission.bond.pct", "0.04"));
            case "etf":
                return new BigDecimal(tradingSettingsService.getString("commission.etf.pct", "0.04"));
            default:
                return STOCK_COMMISSION_PCT;
        }
    }
    
    /**
     * Расчет комиссии для шорта (может отличаться)
     */
    public BigDecimal calculateShortCommission(BigDecimal tradeAmount, String instrumentType) {
        // Для шортов может быть дополнительная комиссия за заем акций
        BigDecimal baseCommission = calculateCommission(tradeAmount, instrumentType);
        BigDecimal shortMultiplier = new BigDecimal(tradingSettingsService.getString("commission.short.multiplier", "1.0"));
        
        return baseCommission.multiply(shortMultiplier);
    }
}
