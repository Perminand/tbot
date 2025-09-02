package ru.perminov.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "position_risk_state", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"account_id", "figi", "side"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PositionRiskState {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "account_id", nullable = false)
    private String accountId;
    
    @Column(name = "figi", nullable = false)
    private String figi;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false)
    private PositionSide side;
    
    // Процентные правила
    @Column(name = "sl_pct", precision = 10, scale = 4)
    private BigDecimal stopLossPct;
    
    @Column(name = "tp_pct", precision = 10, scale = 4)
    private BigDecimal takeProfitPct;
    
    @Column(name = "trailing_pct", precision = 10, scale = 4)
    private BigDecimal trailingPct;
    
    // Рассчитанные ценовые уровни
    @Column(name = "sl_level", precision = 20, scale = 4)
    private BigDecimal stopLossLevel;
    
    @Column(name = "tp_level", precision = 20, scale = 4)
    private BigDecimal takeProfitLevel;
    
    // Watermark для trailing stop
    @Column(name = "high_watermark", precision = 20, scale = 4)
    private BigDecimal highWatermark;
    
    @Column(name = "low_watermark", precision = 20, scale = 4)
    private BigDecimal lowWatermark;
    
    // Снимки позиции
    @Column(name = "entry_price", precision = 20, scale = 4)
    private BigDecimal entryPrice;
    
    @Column(name = "avg_price_snapshot", precision = 20, scale = 4)
    private BigDecimal averagePriceSnapshot;
    
    @Column(name = "qty_snapshot", precision = 20, scale = 4)
    private BigDecimal quantitySnapshot;
    
    // Настройки трейлинга
    @Enumerated(EnumType.STRING)
    @Column(name = "trailing_type")
    private TrailingType trailingType;
    
    @Column(name = "min_step_ticks", precision = 10, scale = 4)
    private BigDecimal minStepTicks;
    
    // Метаданные
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "source")
    private String source;
    
    public enum PositionSide {
        LONG, SHORT
    }
    
    public enum TrailingType {
        PERCENT, ATR, FIXED, CHANNEL
    }
}
