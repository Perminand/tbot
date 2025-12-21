-- Создание индексов для оптимизации запросов
-- Таблицы уже созданы через Liquibase (V011), поэтому проверки не нужны

-- Индексы для instruments
CREATE INDEX IF NOT EXISTS idx_instruments_ticker ON instruments(ticker);
CREATE INDEX IF NOT EXISTS idx_instruments_currency ON instruments(currency);
CREATE INDEX IF NOT EXISTS idx_instruments_exchange ON instruments(exchange);

-- Индексы для orders
CREATE INDEX IF NOT EXISTS idx_orders_figi ON orders(figi);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_account_id ON orders(account_id);
CREATE INDEX IF NOT EXISTS idx_orders_order_date ON orders(order_date);

-- Индексы для positions
CREATE INDEX IF NOT EXISTS idx_positions_figi ON positions(figi);
CREATE INDEX IF NOT EXISTS idx_positions_ticker ON positions(ticker);
CREATE INDEX IF NOT EXISTS idx_positions_currency ON positions(currency);

-- Индекс для trading_settings
CREATE INDEX IF NOT EXISTS idx_trading_settings_key ON trading_settings(setting_key);
