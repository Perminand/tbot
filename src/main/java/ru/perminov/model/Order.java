package ru.perminov.model;

import lombok.Data;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "orders")
public class Order {
    
    @Id
    private String orderId;
    
    private String figi;
    private String operation;
    private String status;
    private BigDecimal requestedLots;
    private BigDecimal executedLots;
    private BigDecimal price;
    private String currency;
    private LocalDateTime orderDate;
    private String orderType;
    private String message;
    private BigDecimal commission;
    private String accountId;
} 