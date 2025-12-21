-- Создание таблицы для метрик производительности торговли
CREATE TABLE IF NOT EXISTS performance_metrics (
    id BIGSERIAL PRIMARY KEY,
    account_id VARCHAR(255) NOT NULL,
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Основные метрики
    total_trades INTEGER NOT NULL DEFAULT 0,
    profitable_trades INTEGER NOT NULL DEFAULT 0,
    losing_trades INTEGER NOT NULL DEFAULT 0,
    win_rate DECIMAL(10,4),
    total_pnl DECIMAL(20,4),
    average_win DECIMAL(20,4),
    average_loss DECIMAL(20,4),
    average_win_loss_ratio DECIMAL(10,4),
    profit_factor DECIMAL(10,4),
    
    -- Метрики риска
    sharpe_ratio DECIMAL(10,4),
    sortino_ratio DECIMAL(10,4),
    max_drawdown DECIMAL(10,4),
    max_drawdown_value DECIMAL(20,4),
    
    -- Дополнительные метрики
    total_return DECIMAL(20,4),
    total_return_pct DECIMAL(10,4),
    volatility DECIMAL(10,4),
    period_start TIMESTAMP,
    period_end TIMESTAMP,
    period_days INTEGER
);

-- Индексы для быстрого поиска
CREATE INDEX IF NOT EXISTS idx_performance_metrics_account_date ON performance_metrics(account_id, calculated_at);
CREATE INDEX IF NOT EXISTS idx_performance_metrics_date ON performance_metrics(calculated_at);

-- Комментарии к таблице и колонкам
COMMENT ON TABLE performance_metrics IS 'Метрики производительности торговли';
COMMENT ON COLUMN performance_metrics.win_rate IS 'Процент прибыльных сделок';
COMMENT ON COLUMN performance_metrics.profit_factor IS 'Profit Factor = сумма прибылей / сумма убытков';
COMMENT ON COLUMN performance_metrics.sharpe_ratio IS 'Коэффициент Шарпа (доходность - безрисковая ставка) / волатильность';
COMMENT ON COLUMN performance_metrics.sortino_ratio IS 'Коэффициент Сортино (доходность - безрисковая ставка) / волатильность убытков';
COMMENT ON COLUMN performance_metrics.max_drawdown IS 'Максимальная просадка в процентах';

