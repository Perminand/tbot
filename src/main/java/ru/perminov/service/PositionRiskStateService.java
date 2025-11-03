package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.perminov.model.PositionRiskState;
import ru.perminov.model.RiskEvent;
import ru.perminov.repository.PositionRiskStateRepository;
import ru.perminov.repository.RiskEventRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PositionRiskStateService {

    private final PositionRiskStateRepository positionRiskStateRepository;
    private final RiskEventRepository riskEventRepository;

    @Transactional
    public PositionRiskState createOrUpdateRiskState(String accountId, String figi,
                                                     PositionRiskState.PositionSide side,
                                                     BigDecimal stopLossPct, BigDecimal takeProfitPct,
                                                     BigDecimal trailingPct, BigDecimal currentPrice,
                                                     BigDecimal averagePrice, BigDecimal quantity) {

        Optional<PositionRiskState> existing = positionRiskStateRepository
                .findByAccountIdAndFigiAndSide(accountId, figi, side);

        PositionRiskState riskState;
        if (existing.isPresent()) {
            riskState = existing.get();
            log.debug("Обновление существующего состояния рисков для {} {} {}", accountId, figi, side);
        } else {
            riskState = PositionRiskState.builder()
                    .accountId(accountId)
                    .figi(figi)
                    .side(side)
                    .entryPrice(currentPrice)
                    .highWatermark(currentPrice)
                    .lowWatermark(currentPrice)
                    .trailingType(PositionRiskState.TrailingType.PERCENT)
                    .minStepTicks(BigDecimal.valueOf(0.01))
                    .source("MANUAL")
                    .build();
            log.debug("Создание нового состояния рисков для {} {} {}", accountId, figi, side);
        }

        // Обновляем правила
        boolean rulesChanged = false;
        if (stopLossPct != null && !stopLossPct.equals(riskState.getStopLossPct())) {
            riskState.setStopLossPct(stopLossPct);
            rulesChanged = true;
        }
        if (takeProfitPct != null && !takeProfitPct.equals(riskState.getTakeProfitPct())) {
            riskState.setTakeProfitPct(takeProfitPct);
            rulesChanged = true;
        }
        if (trailingPct != null && !trailingPct.equals(riskState.getTrailingPct())) {
            riskState.setTrailingPct(trailingPct);
            rulesChanged = true;
        }

        // Обновляем снимки позиции
        riskState.setAveragePriceSnapshot(averagePrice);
        riskState.setQuantitySnapshot(quantity);
        riskState.setUpdatedAt(LocalDateTime.now());

        // Пересчитываем уровни
        recalculateRiskLevels(riskState, currentPrice);

        PositionRiskState saved = positionRiskStateRepository.save(riskState);

        if (rulesChanged) {
            logRiskEvent(accountId, figi, side.toString(), null, null, currentPrice, null,
                    "Правила рисков обновлены", String.format("SL: %s%%, TP: %s%%, Trailing: %s%%",
                            stopLossPct, takeProfitPct, trailingPct));
        }

        return saved;
    }

    @Transactional
    public void updateWatermark(String accountId, String figi, PositionRiskState.PositionSide side,
                                BigDecimal currentPrice) {

        Optional<PositionRiskState> existing = positionRiskStateRepository
                .findByAccountIdAndFigiAndSide(accountId, figi, side);

        if (existing.isEmpty()) {
            log.warn("Попытка обновить watermark для несуществующего состояния рисков: {} {} {}",
                    accountId, figi, side);
            return;
        }

        PositionRiskState riskState = existing.get();
        boolean watermarkUpdated = false;
        BigDecimal oldWatermark = null;

        // Обновляем watermark в зависимости от стороны позиции
        if (side == PositionRiskState.PositionSide.LONG) {
            if (currentPrice.compareTo(riskState.getHighWatermark()) > 0) {
                oldWatermark = riskState.getHighWatermark();
                riskState.setHighWatermark(currentPrice);
                watermarkUpdated = true;
            }
        } else { // SHORT
            if (currentPrice.compareTo(riskState.getLowWatermark()) < 0) {
                oldWatermark = riskState.getLowWatermark();
                riskState.setLowWatermark(currentPrice);
                watermarkUpdated = true;
            }
        }

        if (watermarkUpdated) {
            // Пересчитываем trailing stop
            BigDecimal oldStopLoss = riskState.getStopLossLevel();
            recalculateRiskLevels(riskState, currentPrice);

            // Логируем событие
            logRiskEvent(accountId, figi, side.toString(), oldWatermark, currentPrice, currentPrice,
                    currentPrice, "Watermark обновлен",
                    String.format("Старый: %s, Новый: %s", oldWatermark, currentPrice));

            // Логируем изменение SL если произошло
            if (oldStopLoss != null && riskState.getStopLossLevel() != null &&
                    !oldStopLoss.equals(riskState.getStopLossLevel())) {
                logRiskEvent(accountId, figi, side.toString(), oldStopLoss, riskState.getStopLossLevel(),
                        currentPrice, currentPrice, "Trailing SL обновлен",
                        String.format("Старый SL: %s, Новый SL: %s", oldStopLoss, riskState.getStopLossLevel()));
            }

            positionRiskStateRepository.save(riskState);
        }
    }

    private void recalculateRiskLevels(PositionRiskState riskState, BigDecimal currentPrice) {
        BigDecimal avgPrice = riskState.getAveragePriceSnapshot();
        if (avgPrice == null || avgPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        // Рассчитываем Stop Loss
        if (riskState.getStopLossPct() != null && riskState.getStopLossPct().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal stopLossPct = riskState.getStopLossPct();
            BigDecimal trailingPct = riskState.getTrailingPct();

            if (riskState.getSide() == PositionRiskState.PositionSide.LONG) {
                // Для лонга: SL ниже цены входа
                // ВНИМАНИЕ: stopLossPct уже в виде доли (0.05 = 5%), не делим на 100!
                BigDecimal baseStopLoss = avgPrice.multiply(BigDecimal.ONE.subtract(stopLossPct));

                // Trailing stop: если цена выше watermark, подтягиваем SL
                if (trailingPct != null && riskState.getHighWatermark() != null &&
                        currentPrice.compareTo(riskState.getHighWatermark()) >= 0) {
                    BigDecimal trailingLevel = riskState.getHighWatermark()
                            .multiply(BigDecimal.ONE.subtract(trailingPct));

                    // Берем лучший из двух SL
                    if (trailingLevel.compareTo(baseStopLoss) > 0) {
                        riskState.setStopLossLevel(trailingLevel);
                    } else {
                        riskState.setStopLossLevel(baseStopLoss);
                    }
                } else {
                    riskState.setStopLossLevel(baseStopLoss);
                }
            } else { // SHORT
                // Для шорта: SL выше цены входа
                // ВНИМАНИЕ: stopLossPct уже в виде доли (0.05 = 5%), не делим на 100!
                BigDecimal baseStopLoss = avgPrice.multiply(BigDecimal.ONE.add(stopLossPct));

                // Trailing stop: если цена ниже watermark, подтягиваем SL
                if (trailingPct != null && riskState.getLowWatermark() != null &&
                        currentPrice.compareTo(riskState.getLowWatermark()) <= 0) {
                    BigDecimal trailingLevel = riskState.getLowWatermark()
                            .multiply(BigDecimal.ONE.add(trailingPct));

                    // Берем лучший из двух SL
                    if (trailingLevel.compareTo(baseStopLoss) < 0) {
                        riskState.setStopLossLevel(trailingLevel);
                    } else {
                        riskState.setStopLossLevel(baseStopLoss);
                    }
                } else {
                    riskState.setStopLossLevel(baseStopLoss);
                }
            }
        }

        // Рассчитываем Take Profit
        if (riskState.getTakeProfitPct() != null && riskState.getTakeProfitPct().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal takeProfitPct = riskState.getTakeProfitPct();

            if (riskState.getSide() == PositionRiskState.PositionSide.LONG) {
                // ВНИМАНИЕ: takeProfitPct уже в виде доли (0.05 = 5%), не делим на 100!
                riskState.setTakeProfitLevel(avgPrice.multiply(BigDecimal.ONE.add(takeProfitPct)));
            } else { // SHORT
                // ВНИМАНИЕ: takeProfitPct уже в виде доли (0.05 = 5%), не делим на 100!
                riskState.setTakeProfitLevel(avgPrice.multiply(BigDecimal.ONE.subtract(takeProfitPct)));
            }
        }
    }

    public List<PositionRiskState> getActiveRiskStates(String accountId) {
        return positionRiskStateRepository.findByAccountId(accountId);
    }

    public Optional<PositionRiskState> getRiskState(String accountId, String figi, PositionRiskState.PositionSide side) {
        if (side != null) {
            return positionRiskStateRepository.findByAccountIdAndFigiAndSide(accountId, figi, side);
        } else {
            List<PositionRiskState> states = positionRiskStateRepository.findByAccountIdAndFigi(accountId, figi);
            return states.isEmpty() ? Optional.empty() : Optional.of(states.get(0));
        }
    }

    public List<RiskEvent> getRiskEventsByPosition(String accountId, String figi, LocalDateTime since) {
        return riskEventRepository.findRecentEventsByPosition(accountId, figi, since);
    }

    public List<RiskEvent> getRecentRiskEvents(LocalDateTime since) {
        return riskEventRepository.findRecentEvents(since);
    }

    public List<PositionRiskState> getPositionsWithActiveStopLoss() {
        return positionRiskStateRepository.findActiveStopLosses();
    }

    public List<PositionRiskState> getPositionsWithActiveTakeProfit() {
        return positionRiskStateRepository.findActiveTakeProfits();
    }

    @Transactional
    public void closePosition(String accountId, String figi, PositionRiskState.PositionSide side) {
        Optional<PositionRiskState> existing = positionRiskStateRepository
                .findByAccountIdAndFigiAndSide(accountId, figi, side);

        if (existing.isPresent()) {
            PositionRiskState riskState = existing.get();

            logRiskEvent(accountId, figi, side.toString(), null, null, null, null,
                    "Позиция закрыта", "Состояние рисков удалено");

            positionRiskStateRepository.delete(riskState);
        }
    }

    private void logRiskEvent(String accountId, String figi, String side, BigDecimal oldValue,
                              BigDecimal newValue, BigDecimal currentPrice, BigDecimal watermark,
                              String reason, String details) {
        try {
            RiskEvent event = RiskEvent.builder()
                    .accountId(accountId)
                    .figi(figi)
                    .eventType(determineEventType(oldValue, newValue, reason))
                    .side(side)
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .currentPrice(currentPrice)
                    .watermark(watermark)
                    .reason(reason)
                    .details(details)
                    .createdAt(LocalDateTime.now())
                    .build();

            riskEventRepository.save(event);
        } catch (Exception e) {
            log.error("Ошибка при логировании события риска: {}", e.getMessage(), e);
        }
    }

    private RiskEvent.EventType determineEventType(BigDecimal oldValue, BigDecimal newValue, String reason) {
        if (reason.contains("SL")) return RiskEvent.EventType.SL_UPDATED;
        if (reason.contains("TP")) return RiskEvent.EventType.TP_UPDATED;
        if (reason.contains("Trailing")) return RiskEvent.EventType.TRAILING_UPDATED;
        if (reason.contains("Watermark")) return RiskEvent.EventType.WATERMARK_UPDATED;
        if (reason.contains("закрыта")) return RiskEvent.EventType.POSITION_CLOSED;
        if (reason.contains("Правила")) return RiskEvent.EventType.RISK_RULE_APPLIED;
        return RiskEvent.EventType.RISK_RULE_APPLIED;
    }
}
