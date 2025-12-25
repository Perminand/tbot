--liquibase formatted sql

--changeset system:003-01-position-risk-state
--comment: Создание таблицы position_risk_state

CREATE TABLE IF NOT EXISTS position_risk_state (
    id BIGSERIAL PRIMARY KEY,
    account_id VARCHAR(255) NOT NULL,
    figi VARCHAR(255) NOT NULL,
    side VARCHAR(10) NOT NULL CHECK (side IN ('LONG', 'SHORT')),
    sl_pct NUMERIC(10,4),
    tp_pct NUMERIC(10,4),
    trailing_pct NUMERIC(10,4),
    sl_level NUMERIC(20,4),
    tp_level NUMERIC(20,4),
    high_watermark NUMERIC(20,4),
    low_watermark NUMERIC(20,4),
    entry_price NUMERIC(20,4),
    avg_price_snapshot NUMERIC(20,4),
    qty_snapshot NUMERIC(20,4),
    trailing_type VARCHAR(20) CHECK (trailing_type IS NULL OR trailing_type IN ('PERCENT', 'ATR', 'FIXED', 'CHANNEL')),
    min_step_ticks NUMERIC(10,4),
    updated_at TIMESTAMP,
    source VARCHAR(50),
    UNIQUE(account_id, figi, side)
);

--changeset system:003-02-risk-events
--comment: Создание таблицы risk_events

CREATE TABLE IF NOT EXISTS risk_events (
    id BIGSERIAL PRIMARY KEY,
    account_id VARCHAR(255) NOT NULL,
    figi VARCHAR(255) NOT NULL,
    event_type VARCHAR(50) NOT NULL CHECK (event_type IN ('SL_UPDATED', 'TP_UPDATED', 'TRAILING_UPDATED', 'WATERMARK_UPDATED', 'POSITION_ENTERED', 'POSITION_CLOSED', 'RISK_RULE_APPLIED')),
    side VARCHAR(10),
    old_value NUMERIC(20,4),
    new_value NUMERIC(20,4),
    current_price NUMERIC(20,4),
    watermark NUMERIC(20,4),
    reason TEXT,
    details TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

