package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Сервис для принятия торговых решений с учётом комиссий
 * Предотвращает закрытие позиций, если ожидаемая прибыль меньше двойной комиссии
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommissionAwareTradingService {
    
    private final TradingSettingsService tradingSettingsService;
    
    /**
     * Проверяет, целесообразно ли закрывать позицию с учётом комиссий
     * @param entryPrice цена входа в позицию
     * @param currentPrice текущая цена
     * @param positionValue стоимость позиции
     * @param figi FIGI инструмента
     * @return результат проверки
     */
    public CommissionResult shouldClosePosition(BigDecimal entryPrice, BigDecimal currentPrice, 
                                              BigDecimal positionValue, String figi) {
        try {
            // Настройки комиссий (по умолчанию 0.05% за сделку)
            String commissionRateStr = tradingSettingsService.getString("trading.commission_rate_pct", "0.0005");
            BigDecimal commissionRate = new BigDecimal(commissionRateStr);
            
            // Расчёт комиссий (вход + выход)
            BigDecimal totalCommission = positionValue.multiply(commissionRate).multiply(new BigDecimal("2"));
            
            // Расчёт текущей прибыли/убытка
            BigDecimal priceChange = currentPrice.subtract(entryPrice);
            BigDecimal pnl = priceChange.divide(entryPrice, 6, RoundingMode.HALF_UP).multiply(positionValue);
            
            // Минимальный порог прибыльности = 2 * комиссия + минимальный спред (по умолчанию 0.25%)
            String minSpreadStr = tradingSettingsService.getString("trading.min_profitable_spread_pct", "0.0025");
            BigDecimal minSpread = new BigDecimal(minSpreadStr);
            BigDecimal minProfitThreshold = totalCommission.add(positionValue.multiply(minSpread));
            
            log.debug("Анализ прибыльности для {}: PnL={}, комиссии={}, мин.порог={}", 
                figi, pnl, totalCommission, minProfitThreshold);
            
            if (pnl.compareTo(minProfitThreshold) < 0) {
                String reason = String.format("Недостаточная прибыль: PnL=%.2f₽, требуется >%.2f₽ (комиссии=%.2f₽)", 
                    pnl, minProfitThreshold, totalCommission);
                log.warn("💰 Блокировка закрытия по комиссиям для {}: {}", figi, reason);
                return CommissionResult.blocked(reason);
            }
            
            String reason = String.format("Прибыльность достаточна: PnL=%.2f₽ > %.2f₽", pnl, minProfitThreshold);
            log.info("✅ Закрытие позиции выгодно для {}: {}", figi, reason);
            return CommissionResult.allowed(reason);
            
        } catch (Exception e) {
            log.error("Ошибка анализа комиссий для {}: {}", figi, e.getMessage());
            // В случае ошибки разрешаем закрытие, чтобы не заблокировать торговлю
            return CommissionResult.allowed("Ошибка расчёта, разрешено по умолчанию");
        }
    }
    
    /**
     * Результат проверки прибыльности с учётом комиссий
     */
    public static class CommissionResult {
        private final boolean allowed;
        private final String reason;
        
        private CommissionResult(boolean allowed, String reason) {
            this.allowed = allowed;
            this.reason = reason;
        }
        
        public static CommissionResult allowed(String reason) {
            return new CommissionResult(true, reason);
        }
        
        public static CommissionResult blocked(String reason) {
            return new CommissionResult(false, reason);
        }
        
        public boolean isAllowed() { return allowed; }
        public boolean isBlocked() { return !allowed; }
        public String getReason() { return reason; }
    }
}
