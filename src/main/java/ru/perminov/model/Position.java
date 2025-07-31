package ru.perminov.model;

import lombok.Data;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Data
@Entity
@Table(name = "positions")
public class Position {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String figi;
    private String ticker;
    private String isin;
    private String instrumentType;
    private BigDecimal balance;
    private BigDecimal blocked;
    private BigDecimal lots;
    private BigDecimal averagePositionPrice;
    private BigDecimal averagePositionPriceNoNkd;
    private String name;
    private String currency;
    private BigDecimal currentPrice;
    private BigDecimal averagePositionPriceFifo;
    private BigDecimal quantityLots;
} 