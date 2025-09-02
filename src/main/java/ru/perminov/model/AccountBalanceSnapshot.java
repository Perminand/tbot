package ru.perminov.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "account_balance_snapshots")
public class AccountBalanceSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String accountId;
    private LocalDateTime capturedAt;

    private BigDecimal cashTotal;         // Денежные средства (валюта счёта)
    private BigDecimal portfolioValue;    // Стоимость позиций
    private BigDecimal totalValue;        // Итого (cash + portfolio)

    private String currency;              // Валюта отчёта
}


