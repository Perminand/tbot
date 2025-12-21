package ru.perminov.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Модель для хранения метрик производительности торговли
 */
@Entity
@Table(name = "performance_metrics",
       indexes = {
           @Index(name = "idx_performance_metrics_account_date", columnList = "account_id,calculated_at"),
           @Index(name = "idx_performance_metrics_date", columnList = "calculated_at")
       })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PerformanceMetrics {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "account_id", nullable = false)
    private String accountId;
    
    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;
    
    // Основные метрики
    @Column(name = "total_trades", nullable = false)
    private Integer totalTrades;
    
    @Column(name = "profitable_trades", nullable = false)
    private Integer profitableTrades;
    
    @Column(name = "losing_trades", nullable = false)
    private Integer losingTrades;
    
    @Column(name = "win_rate", precision = 10, scale = 4)
    private BigDecimal winRate; // Процент прибыльных сделок
    
    @Column(name = "total_pnl", precision = 20, scale = 4)
    private BigDecimal totalPnL; // Общий P&L
    
    @Column(name = "average_win", precision = 20, scale = 4)
    private BigDecimal averageWin; // Средняя прибыль
    
    @Column(name = "average_loss", precision = 20, scale = 4)
    private BigDecimal averageLoss; // Средний убыток
    
    @Column(name = "average_win_loss_ratio", precision = 10, scale = 4)
    private BigDecimal averageWinLossRatio; // Отношение среднего выигрыша к среднему проигрышу
    
    @Column(name = "profit_factor", precision = 10, scale = 4)
    private BigDecimal profitFactor; // Profit Factor = сумма прибылей / сумма убытков
    
    // Метрики риска
    @Column(name = "sharpe_ratio", precision = 10, scale = 4)
    private BigDecimal sharpeRatio; // Коэффициент Шарпа
    
    @Column(name = "sortino_ratio", precision = 10, scale = 4)
    private BigDecimal sortinoRatio; // Коэффициент Сортино
    
    @Column(name = "max_drawdown", precision = 10, scale = 4)
    private BigDecimal maxDrawdown; // Максимальная просадка в процентах
    
    @Column(name = "max_drawdown_value", precision = 20, scale = 4)
    private BigDecimal maxDrawdownValue; // Максимальная просадка в абсолютных значениях
    
    // Дополнительные метрики
    @Column(name = "total_return", precision = 20, scale = 4)
    private BigDecimal totalReturn; // Общая доходность
    
    @Column(name = "total_return_pct", precision = 10, scale = 4)
    private BigDecimal totalReturnPct; // Общая доходность в процентах
    
    @Column(name = "volatility", precision = 10, scale = 4)
    private BigDecimal volatility; // Волатильность
    
    @Column(name = "period_start")
    private LocalDateTime periodStart; // Начало периода расчета
    
    @Column(name = "period_end")
    private LocalDateTime periodEnd; // Конец периода расчета
    
    @Column(name = "period_days")
    private Integer periodDays; // Количество дней в периоде
}

