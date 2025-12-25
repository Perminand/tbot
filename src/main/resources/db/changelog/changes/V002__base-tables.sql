--liquibase formatted sql

--changeset system:002-01-trading-settings
--comment: Создание таблицы trading_settings

CREATE TABLE IF NOT EXISTS trading_settings (
    id BIGSERIAL PRIMARY KEY,
    setting_key VARCHAR(255) UNIQUE NOT NULL,
    setting_value TEXT NOT NULL,
    description TEXT,
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_trading_settings_key ON trading_settings(setting_key);

--changeset system:002-02-instruments
--comment: Создание таблицы instruments

CREATE TABLE IF NOT EXISTS instruments (
    figi VARCHAR(255) PRIMARY KEY,
    ticker VARCHAR(255),
    isin VARCHAR(255),
    name VARCHAR(255),
    currency VARCHAR(10),
    exchange VARCHAR(50),
    sector VARCHAR(255),
    country_of_risk VARCHAR(10),
    country_of_risk_name VARCHAR(255),
    instrument_type VARCHAR(50),
    instrument_kind VARCHAR(50),
    share_type VARCHAR(50),
    nominal VARCHAR(50),
    nominal_currency VARCHAR(10),
    trading_status VARCHAR(50),
    otc_flag VARCHAR(10),
    buy_available_flag VARCHAR(10),
    sell_available_flag VARCHAR(10),
    min_price_increment VARCHAR(50),
    api_trade_available_flag VARCHAR(10),
    uid VARCHAR(255),
    real_exchange VARCHAR(50),
    position_uid VARCHAR(255),
    for_iis_flag VARCHAR(10),
    for_qual_investor_flag VARCHAR(10),
    weekend_flag VARCHAR(10),
    blocked_tca_flag VARCHAR(10),
    first1min_candle_date VARCHAR(50),
    first1day_candle_date VARCHAR(50),
    risk_level VARCHAR(50)
);

--changeset system:002-03-orders
--comment: Создание таблицы orders

CREATE TABLE IF NOT EXISTS orders (
    order_id VARCHAR(255) PRIMARY KEY,
    figi VARCHAR(255),
    operation VARCHAR(50),
    status VARCHAR(50),
    requested_lots NUMERIC(20,4),
    executed_lots NUMERIC(20,4),
    price NUMERIC(20,4),
    currency VARCHAR(10),
    order_date TIMESTAMP,
    order_type VARCHAR(50),
    message TEXT,
    commission NUMERIC(20,4),
    account_id VARCHAR(255)
);

--changeset system:002-04-positions
--comment: Создание таблицы positions

CREATE TABLE IF NOT EXISTS positions (
    id BIGSERIAL PRIMARY KEY,
    figi VARCHAR(255),
    ticker VARCHAR(255),
    isin VARCHAR(255),
    instrument_type VARCHAR(50),
    balance NUMERIC(20,4),
    blocked NUMERIC(20,4),
    lots NUMERIC(20,4),
    average_position_price NUMERIC(20,4),
    average_position_price_no_nkd NUMERIC(20,4),
    name VARCHAR(255),
    currency VARCHAR(10),
    current_price NUMERIC(20,4),
    average_position_price_fifo NUMERIC(20,4),
    quantity_lots NUMERIC(20,4)
);

--changeset system:002-05-account-balance-snapshots
--comment: Создание таблицы account_balance_snapshots

CREATE TABLE IF NOT EXISTS account_balance_snapshots (
    id BIGSERIAL PRIMARY KEY,
    account_id VARCHAR(255),
    captured_at TIMESTAMP,
    cash_total NUMERIC(20,4),
    portfolio_value NUMERIC(20,4),
    total_value NUMERIC(20,4),
    currency VARCHAR(10)
);

--changeset system:002-06-risk-rules
--comment: Создание таблицы risk_rules

CREATE TABLE IF NOT EXISTS risk_rules (
    id BIGSERIAL PRIMARY KEY,
    figi VARCHAR(255) UNIQUE NOT NULL,
    stop_loss_pct DOUBLE PRECISION,
    take_profit_pct DOUBLE PRECISION,
    active BOOLEAN DEFAULT true
);

--changeset system:002-07-order-log
--comment: Создание таблицы order_log

CREATE TABLE IF NOT EXISTS order_log (
    id SERIAL PRIMARY KEY,
    order_id VARCHAR(255) NOT NULL,
    action VARCHAR(10) NOT NULL,
    old_status VARCHAR(20),
    new_status VARCHAR(20),
    changed_at TIMESTAMP DEFAULT NOW()
);

