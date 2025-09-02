-- Создание таблиц для улучшенной системы управления рисками

-- Таблица состояния рисков позиций
CREATE TABLE IF NOT EXISTS position_risk_state (
    id BIGSERIAL PRIMARY KEY,
    account_id VARCHAR(255) NOT NULL,
    figi VARCHAR(255) NOT NULL,
    side VARCHAR(10) NOT NULL CHECK (side IN ('LONG', 'SHORT')),
    
    -- Процентные правила
    sl_pct DECIMAL(10,4),
    tp_pct DECIMAL(10,4),
    trailing_pct DECIMAL(10,4),
    
    -- Рассчитанные ценовые уровни
    sl_level DECIMAL(20,4),
    tp_level DECIMAL(20,4),
    
    -- Watermark для trailing stop
    high_watermark DECIMAL(20,4),
    low_watermark DECIMAL(20,4),
    
    -- Снимки позиции
    entry_price DECIMAL(20,4),
    avg_price_snapshot DECIMAL(20,4),
    qty_snapshot DECIMAL(20,4),
    
    -- Настройки трейлинга
    trailing_type VARCHAR(20) CHECK (trailing_type IN ('PERCENT', 'ATR', 'FIXED', 'CHANNEL')),
    min_step_ticks DECIMAL(10,4),
    
    -- Метаданные
    updated_at TIMESTAMP,
    source VARCHAR(50),
    
    -- Уникальный индекс по аккаунту, FIGI и стороне
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

-- Индексы для быстрого поиска
CREATE INDEX IF NOT EXISTS idx_position_risk_state_account_figi ON position_risk_state(account_id, figi);
CREATE INDEX IF NOT EXISTS idx_position_risk_state_side ON position_risk_state(side);
CREATE INDEX IF NOT EXISTS idx_position_risk_state_active_sl ON position_risk_state(sl_level) WHERE sl_level IS NOT NULL AND sl_level > 0;
CREATE INDEX IF NOT EXISTS idx_position_risk_state_active_tp ON position_risk_state(tp_level) WHERE tp_level IS NOT NULL AND tp_level > 0;

CREATE INDEX IF NOT EXISTS idx_risk_events_account_figi ON risk_events(account_id, figi);
CREATE INDEX IF NOT EXISTS idx_risk_events_type ON risk_events(event_type);
CREATE INDEX IF NOT EXISTS idx_risk_events_created_at ON risk_events(created_at);

-- Комментарии к таблицам
COMMENT ON TABLE position_risk_state IS 'Состояние рисков по позициям (SL/TP/трейлинг)';
COMMENT ON TABLE risk_events IS 'Аудиторские события по рискам';

COMMENT ON COLUMN position_risk_state.side IS 'Сторона позиции: LONG или SHORT';
COMMENT ON COLUMN position_risk_state.sl_pct IS 'Процент Stop Loss от средней цены';
COMMENT ON COLUMN position_risk_state.tp_pct IS 'Процент Take Profit от средней цены';
COMMENT ON COLUMN position_risk_state.trailing_pct IS 'Процент для trailing stop';
COMMENT ON COLUMN position_risk_state.sl_level IS 'Рассчитанный уровень Stop Loss в цене';
COMMENT ON COLUMN position_risk_state.tp_level IS 'Рассчитанный уровень Take Profit в цене';
COMMENT ON COLUMN position_risk_state.high_watermark IS 'Максимальная цена для лонга (trailing)';
COMMENT ON COLUMN position_risk_state.low_watermark IS 'Минимальная цена для шорта (trailing)';
COMMENT ON COLUMN position_risk_state.entry_price IS 'Цена входа в позицию';
COMMENT ON COLUMN position_risk_state.avg_price_snapshot IS 'Снимок средней цены позиции';
COMMENT ON COLUMN position_risk_state.qty_snapshot IS 'Снимок количества лотов';
COMMENT ON COLUMN position_risk_state.trailing_type IS 'Тип trailing stop';
COMMENT ON COLUMN position_risk_state.min_step_ticks IS 'Минимальный шаг для обновления trailing';

COMMENT ON COLUMN risk_events.event_type IS 'Тип события риска';
COMMENT ON COLUMN risk_events.old_value IS 'Предыдущее значение (для изменений)';
COMMENT ON COLUMN risk_events.new_value IS 'Новое значение (для изменений)';
COMMENT ON COLUMN risk_events.current_price IS 'Текущая цена на момент события';
COMMENT ON COLUMN risk_events.watermark IS 'Значение watermark на момент события';
COMMENT ON COLUMN risk_events.reason IS 'Причина события';
COMMENT ON COLUMN risk_events.details IS 'Детали события';
