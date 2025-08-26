-- Скрипт для создания индексов и дополнительных объектов после инициализации Hibernate
-- Этот скрипт должен выполняться вручную после первого запуска приложения

-- Создание индексов для оптимизации
CREATE INDEX IF NOT EXISTS idx_instruments_ticker ON instruments(ticker);
CREATE INDEX IF NOT EXISTS idx_instruments_currency ON instruments(currency);
CREATE INDEX IF NOT EXISTS idx_instruments_exchange ON instruments(exchange);

CREATE INDEX IF NOT EXISTS idx_orders_figi ON orders(figi);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_account_id ON orders(account_id);
CREATE INDEX IF NOT EXISTS idx_orders_order_date ON orders(order_date);

CREATE INDEX IF NOT EXISTS idx_positions_figi ON positions(figi);
CREATE INDEX IF NOT EXISTS idx_positions_ticker ON positions(ticker);
CREATE INDEX IF NOT EXISTS idx_positions_currency ON positions(currency);

-- Создание представления для портфеля
CREATE OR REPLACE VIEW portfolio_summary AS
SELECT 
    p.currency,
    COUNT(*) as instruments_count,
    SUM(p.balance) as total_balance,
    SUM(p.lots) as total_lots
FROM positions p
GROUP BY p.currency;

-- Создание представления для активных ордеров
CREATE OR REPLACE VIEW active_orders AS
SELECT 
    o.order_id,
    o.figi,
    o.operation,
    o.status,
    o.requested_lots,
    o.executed_lots,
    o.price,
    o.currency,
    o.order_date,
    o.order_type,
    o.account_id
FROM orders o
WHERE o.status IN ('New', 'PartiallyFill', 'PendingNew', 'PendingReplace');

-- Создание функции для получения статистики по инструментам
CREATE OR REPLACE FUNCTION get_instrument_stats()
RETURNS TABLE(
    instrument_type VARCHAR,
    count BIGINT,
    currencies TEXT[]
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        i.instrument_type,
        COUNT(*)::BIGINT,
        ARRAY_AGG(DISTINCT i.currency)::TEXT[]
    FROM instruments i
    GROUP BY i.instrument_type;
END;
$$ LANGUAGE plpgsql;

-- Создание функции для получения истории ордеров
CREATE OR REPLACE FUNCTION get_order_history(
    p_account_id VARCHAR,
    p_start_date TIMESTAMP,
    p_end_date TIMESTAMP
)
RETURNS TABLE(
    order_id VARCHAR,
    figi VARCHAR,
    operation VARCHAR,
    status VARCHAR,
    requested_lots NUMERIC,
    executed_lots NUMERIC,
    price NUMERIC,
    currency VARCHAR,
    order_date TIMESTAMP,
    order_type VARCHAR
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        o.order_id,
        o.figi,
        o.operation,
        o.status,
        o.requested_lots,
        o.executed_lots,
        o.price,
        o.currency,
        o.order_date,
        o.order_type
    FROM orders o
    WHERE o.account_id = p_account_id
      AND o.order_date BETWEEN p_start_date AND p_end_date
    ORDER BY o.order_date DESC;
END;
$$ LANGUAGE plpgsql;

-- Создание таблицы для логирования изменений ордеров
CREATE TABLE IF NOT EXISTS order_log (
    id SERIAL PRIMARY KEY,
    order_id VARCHAR NOT NULL,
    action VARCHAR(10) NOT NULL,
    old_status VARCHAR(20),
    new_status VARCHAR(20),
    changed_at TIMESTAMP DEFAULT NOW()
);

-- Создание триггера для логирования изменений ордеров
CREATE OR REPLACE FUNCTION log_order_changes()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        INSERT INTO order_log (order_id, action, old_status, new_status, changed_at)
        VALUES (NEW.order_id, 'INSERT', NULL, NEW.status, NOW());
        RETURN NEW;
    ELSIF TG_OP = 'UPDATE' THEN
        INSERT INTO order_log (order_id, action, old_status, new_status, changed_at)
        VALUES (NEW.order_id, 'UPDATE', OLD.status, NEW.status, NOW());
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        INSERT INTO order_log (order_id, action, old_status, new_status, changed_at)
        VALUES (OLD.order_id, 'DELETE', OLD.status, NULL, NOW());
        RETURN OLD;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- Создание триггера для таблицы orders
DROP TRIGGER IF EXISTS trigger_order_log ON orders;
CREATE TRIGGER trigger_order_log
    AFTER INSERT OR UPDATE OR DELETE ON orders
    FOR EACH ROW
    EXECUTE FUNCTION log_order_changes();

-- Создание индекса для таблицы логов
CREATE INDEX IF NOT EXISTS idx_order_log_order_id ON order_log(order_id);
CREATE INDEX IF NOT EXISTS idx_order_log_changed_at ON order_log(changed_at);

-- Комментарии к таблицам
COMMENT ON TABLE instruments IS 'Финансовые инструменты';
COMMENT ON TABLE orders IS 'Торговые ордера';
COMMENT ON TABLE positions IS 'Позиции в портфеле';
COMMENT ON TABLE order_log IS 'Лог изменений ордеров';

COMMENT ON VIEW portfolio_summary IS 'Сводка по портфелю';
COMMENT ON VIEW active_orders IS 'Активные ордера';



