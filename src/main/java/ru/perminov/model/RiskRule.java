package ru.perminov.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "risk_rules")
@Data
public class RiskRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String figi;

    // Доля стоп-лосса, например 0.05 = 5%
    @Column(name = "stop_loss_pct")
    private Double stopLossPct;

    // Доля тейк-профита, например 0.1 = 10%
    @Column(name = "take_profit_pct")
    private Double takeProfitPct;

    @Column(name = "active")
    private Boolean active = true;
}


