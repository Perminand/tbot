package ru.perminov.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "risk_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskEvent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "account_id", nullable = false)
    private String accountId;
    
    @Column(name = "figi", nullable = false)
    private String figi;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;
    
    @Column(name = "side")
    private String side;
    
    @Column(name = "old_value", precision = 20, scale = 4)
    private BigDecimal oldValue;
    
    @Column(name = "new_value", precision = 20, scale = 4)
    private BigDecimal newValue;
    
    @Column(name = "current_price", precision = 20, scale = 4)
    private BigDecimal currentPrice;
    
    @Column(name = "watermark", precision = 20, scale = 4)
    private BigDecimal watermark;
    
    @Column(name = "reason")
    private String reason;
    
    @Column(name = "details")
    private String details;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    public enum EventType {
        SL_UPDATED, TP_UPDATED, TRAILING_UPDATED, WATERMARK_UPDATED, 
        POSITION_ENTERED, POSITION_CLOSED, RISK_RULE_APPLIED
    }
}
