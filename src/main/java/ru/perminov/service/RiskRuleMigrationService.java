package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.perminov.model.Order;
import ru.perminov.model.PositionRiskState;
import ru.perminov.model.RiskRule;
import ru.perminov.repository.OrderRepository;
import ru.perminov.repository.PositionRiskStateRepository;
import ru.perminov.repository.RiskRuleRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 🚀 СЕРВИС МИГРАЦИИ: Обновление существующих SL/TP правил на новые оптимизированные значения
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RiskRuleMigrationService {
    
    private final RiskRuleRepository riskRuleRepository;
    private final PositionRiskStateRepository positionRiskStateRepository;
    private final OrderRepository orderRepository;
    private final RiskRuleService riskRuleService;
    private final BotLogService botLogService;
    
    // Новые оптимизированные значения
    private static final double NEW_SL_PCT = 0.02;  // 2%
    private static final double NEW_TP_PCT = 0.06;  // 6%
    private static final double NEW_TRAILING_PCT = 0.03;  // 3%
    
    /**
     * 🎯 ОСНОВНОЙ МЕТОД: Миграция всех существующих правил на новые значения
     */
    @Transactional
    public MigrationResult migrateAllRulesToNewValues() {
        log.info("🚀 НАЧАЛО МИГРАЦИИ: Обновление всех SL/TP правил на новые оптимизированные значения");
        
        MigrationResult result = new MigrationResult();
        
        try {
            // 1. Обновляем RiskRule записи
            result.riskRulesUpdated = updateRiskRules();
            
            // 2. Обновляем PositionRiskState записи  
            result.positionStatesUpdated = updatePositionRiskStates();
            
            // 3. Отменяем старые виртуальные ордера и создаем новые
            result.virtualOrdersUpdated = updateVirtualOrders();
            
            // 4. Логируем результаты
            logMigrationResults(result);
            
            log.info("✅ МИГРАЦИЯ ЗАВЕРШЕНА УСПЕШНО: {} правил, {} позиций, {} ордеров обновлено", 
                result.riskRulesUpdated, result.positionStatesUpdated, result.virtualOrdersUpdated);
            
        } catch (Exception e) {
            log.error("❌ ОШИБКА МИГРАЦИИ: {}", e.getMessage(), e);
            result.error = e.getMessage();
        }
        
        return result;
    }
    
    /**
     * Обновление RiskRule записей
     */
    private int updateRiskRules() {
        List<RiskRule> allRules = riskRuleRepository.findAll();
        int updated = 0;
        
        for (RiskRule rule : allRules) {
            boolean changed = false;
            
            // Обновляем только если текущие значения = старым дефолтам (5% и 10%)
            if (rule.getStopLossPct() != null && Math.abs(rule.getStopLossPct() - 0.05) < 0.001) {
                rule.setStopLossPct(NEW_SL_PCT);
                changed = true;
                log.debug("📊 Обновляем SL для {}: 5% → 2%", rule.getFigi());
            }
            
            if (rule.getTakeProfitPct() != null && Math.abs(rule.getTakeProfitPct() - 0.10) < 0.001) {
                rule.setTakeProfitPct(NEW_TP_PCT);
                changed = true;
                log.debug("📊 Обновляем TP для {}: 10% → 6%", rule.getFigi());
            }
            
            if (changed) {
                riskRuleRepository.save(rule);
                updated++;
            }
        }
        
        log.info("✅ Обновлено {} RiskRule записей", updated);
        return updated;
    }
    
    /**
     * Обновление PositionRiskState записей
     */
    private int updatePositionRiskStates() {
        List<PositionRiskState> allStates = positionRiskStateRepository.findAll();
        int updated = 0;
        
        for (PositionRiskState state : allStates) {
            boolean changed = false;
            
            // Обновляем только если текущие значения = старым дефолтам
            if (state.getStopLossPct() != null && 
                Math.abs(state.getStopLossPct().doubleValue() - 0.05) < 0.001) {
                state.setStopLossPct(BigDecimal.valueOf(NEW_SL_PCT));
                changed = true;
            }
            
            if (state.getTakeProfitPct() != null && 
                Math.abs(state.getTakeProfitPct().doubleValue() - 0.10) < 0.001) {
                state.setTakeProfitPct(BigDecimal.valueOf(NEW_TP_PCT));
                changed = true;
            }
            
            if (state.getTrailingPct() != null && 
                Math.abs(state.getTrailingPct().doubleValue() - 0.05) < 0.001) {
                state.setTrailingPct(BigDecimal.valueOf(NEW_TRAILING_PCT));
                changed = true;
            }
            
            if (changed) {
                state.setUpdatedAt(LocalDateTime.now());
                positionRiskStateRepository.save(state);
                updated++;
                
                log.debug("📊 Обновляем позицию {}: SL/TP/Trailing → 2%/6%/3%", 
                    state.getFigi());
            }
        }
        
        log.info("✅ Обновлено {} PositionRiskState записей", updated);
        return updated;
    }
    
    /**
     * Обновление виртуальных ордеров
     */
    private int updateVirtualOrders() {
        // Находим активные виртуальные ордера
        List<Order> virtualOrders = orderRepository.findByStatus("MONITORING").stream()
            .filter(order -> "VIRTUAL_STOP_LOSS".equals(order.getOrderType()) || 
                           "VIRTUAL_TAKE_PROFIT".equals(order.getOrderType()))
            .toList();
        
        int updated = 0;
        
        for (Order order : virtualOrders) {
            try {
                // Отменяем старый ордер
                order.setStatus("CANCELLED_BY_MIGRATION");
                order.setMessage("Отменен при миграции на новые SL/TP значения");
                orderRepository.save(order);
                
                log.debug("🔄 Отменен виртуальный ордер {} для миграции", order.getOrderId());
                updated++;
                
            } catch (Exception e) {
                log.warn("⚠️ Ошибка обновления виртуального ордера {}: {}", 
                    order.getOrderId(), e.getMessage());
            }
        }
        
        log.info("✅ Отменено {} виртуальных ордеров для пересоздания с новыми значениями", updated);
        return updated;
    }
    
    /**
     * Логирование результатов миграции
     */
    private void logMigrationResults(MigrationResult result) {
        String message = String.format(
            "Миграция SL/TP завершена: %d правил, %d позиций, %d ордеров обновлено. Новые значения: SL=2%%, TP=6%%, Trailing=3%%",
            result.riskRulesUpdated, result.positionStatesUpdated, result.virtualOrdersUpdated
        );
        
        botLogService.addLogEntry(
            BotLogService.LogLevel.INFO,
            BotLogService.LogCategory.RISK_MANAGEMENT,
            "Миграция SL/TP правил",
            message
        );
    }
    
    /**
     * 🎯 ВЫБОРОЧНАЯ МИГРАЦИЯ: Обновление правил только для конкретного инструмента
     */
    @Transactional
    public boolean migrateRulesForInstrument(String figi) {
        log.info("🎯 Миграция SL/TP для конкретного инструмента: {}", figi);
        
        try {
            // Обновляем RiskRule
            riskRuleService.upsert(figi, NEW_SL_PCT, NEW_TP_PCT, true);
            
            // Обновляем PositionRiskState если есть (для всех аккаунтов с этим FIGI)
            List<PositionRiskState> states = positionRiskStateRepository.findAll().stream()
                .filter(state -> figi.equals(state.getFigi()))
                .toList();
            for (PositionRiskState state : states) {
                state.setStopLossPct(BigDecimal.valueOf(NEW_SL_PCT));
                state.setTakeProfitPct(BigDecimal.valueOf(NEW_TP_PCT));
                state.setTrailingPct(BigDecimal.valueOf(NEW_TRAILING_PCT));
                state.setUpdatedAt(LocalDateTime.now());
                positionRiskStateRepository.save(state);
            }
            
            log.info("✅ Миграция для {} завершена успешно", figi);
            return true;
            
        } catch (Exception e) {
            log.error("❌ Ошибка миграции для {}: {}", figi, e.getMessage());
            return false;
        }
    }
    
    /**
     * Результат миграции
     */
    public static class MigrationResult {
        public int riskRulesUpdated = 0;
        public int positionStatesUpdated = 0;
        public int virtualOrdersUpdated = 0;
        public String error = null;
        
        public boolean isSuccess() {
            return error == null;
        }
    }
}
