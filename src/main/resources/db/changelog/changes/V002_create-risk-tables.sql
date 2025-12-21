-- Создание таблиц для системы управления рисками

-- Таблица состояния рисков позиций
CREATE TABLE IF NOT EXISTS position_risk_state (
    id BIGSERIAL PRIMARY KEY,
    account_id VARCHAR(255) NOT NULL,
    figi VARCHAR(255) NOT NULL,
    side VARCHAR(10) NOT NULL CHECK (side IN ('LONG', 'SHORT')),
    sl_pct DECIMAL(10,4),
    tp_pct DECIMAL(10,4),
    trailing_pct DECIMAL(10,4),
    sl_level DECIMAL(20,4),
    tp_level DECIMAL(20,4),
    high_watermark DECIMAL(20,4),
    low_watermark DECIMAL(20,4),
    entry_price DECIMAL(20,4),
    avg_price_snapshot DECIMAL(20,4),
    qty_snapshot DECIMAL(20,4),
    trailing_type VARCHAR(20) CHECK (trailing_type IN ('PERCENT', 'ATR', 'FIXED', 'CHANNEL') OR trailing_type IS NULL),
    min_step_ticks DECIMAL(10,4),
    updated_at TIMESTAMP,
    source VARCHAR(50),
    UNIQUE(account_id, figi, side)
);

-- Таблица аудиторских событий по рискам
CREATE TABLE IF NOT EXISTS risk_events (
    id BIGSERIAL PRIMARY KEY,
    account_id VARCHAR(255) NOT NULL,
    figi VARCHAR(255) NOT NULL,
    event_type VARCHAR(50) NOT NULL CHECK (event_type IN (
        'SL_UPDATED', 'TP_UPDATED', 'TRAILING_UPDATED', 'WATERMARK_UPDATED',
        'POSITION_ENTERED', 'POSITION_CLOSED', 'RISK_RULE_APPLIED'
    )),
    side VARCHAR(10),
    old_value DECIMAL(20,4),
    new_value DECIMAL(20,4),
    current_price DECIMAL(20,4),
    watermark DECIMAL(20,4),
    reason TEXT,
    details TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Индексы для position_risk_state
CREATE INDEX IF NOT EXISTS idx_position_risk_state_account_figi ON position_risk_state(account_id, figi);
CREATE INDEX IF NOT EXISTS idx_position_risk_state_side ON position_risk_state(side);
CREATE INDEX IF NOT EXISTS idx_position_risk_state_active_sl ON position_risk_state(sl_level) WHERE sl_level IS NOT NULL AND sl_level > 0;
CREATE INDEX IF NOT EXISTS idx_position_risk_state_active_tp ON position_risk_state(tp_level) WHERE tp_level IS NOT NULL AND tp_level > 0;

-- Индексы для risk_events
CREATE INDEX IF NOT EXISTS idx_risk_events_account_figi ON risk_events(account_id, figi);
CREATE INDEX IF NOT EXISTS idx_risk_events_type ON risk_events(event_type);
CREATE INDEX IF NOT EXISTS idx_risk_events_created_at ON risk_events(created_at);

-- Таблица правил рисков по инструментам
CREATE TABLE IF NOT EXISTS risk_rules (
    id BIGSERIAL PRIMARY KEY,
    figi VARCHAR(255) NOT NULL UNIQUE,
    stop_loss_pct DOUBLE PRECISION,
    take_profit_pct DOUBLE PRECISION,
    active BOOLEAN DEFAULT TRUE
);

-- Индекс для risk_rules
CREATE INDEX IF NOT EXISTS idx_risk_rules_figi ON risk_rules(figi);
CREATE INDEX IF NOT EXISTS idx_risk_rules_active ON risk_rules(active) WHERE active = TRUE;

-- Комментарии
COMMENT ON TABLE position_risk_state IS 'Состояние рисков по позициям (SL/TP/трейлинг)';
COMMENT ON TABLE risk_events IS 'Аудиторские события по рискам';
COMMENT ON TABLE risk_rules IS 'Правила рисков по инструментам (персональные SL/TP)';
COMMENT ON COLUMN position_risk_state.side IS 'Сторона позиции: LONG или SHORT';
COMMENT ON COLUMN position_risk_state.sl_pct IS 'Процент Stop Loss от средней цены';
COMMENT ON COLUMN position_risk_state.tp_pct IS 'Процент Take Profit от средней цены';
COMMENT ON COLUMN position_risk_state.trailing_pct IS 'Процент для trailing stop';
COMMENT ON COLUMN risk_events.event_type IS 'Тип события риска';
COMMENT ON COLUMN risk_rules.figi IS 'FIGI инструмента';
COMMENT ON COLUMN risk_rules.stop_loss_pct IS 'Процент стоп-лосса для инструмента';
COMMENT ON COLUMN risk_rules.take_profit_pct IS 'Процент тейк-профита для инструмента';
COMMENT ON COLUMN risk_rules.active IS 'Активно ли правило';

