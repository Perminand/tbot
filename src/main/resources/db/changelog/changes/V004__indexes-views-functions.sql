--liquibase formatted sql

--changeset system:004-01-indexes-instruments
--comment: Создание индексов для таблицы instruments

CREATE INDEX IF NOT EXISTS idx_instruments_ticker ON instruments(ticker);
CREATE INDEX IF NOT EXISTS idx_instruments_currency ON instruments(currency);
CREATE INDEX IF NOT EXISTS idx_instruments_exchange ON instruments(exchange);

--changeset system:004-02-indexes-orders
--comment: Создание индексов для таблицы orders

CREATE INDEX IF NOT EXISTS idx_orders_figi ON orders(figi);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_account_id ON orders(account_id);
CREATE INDEX IF NOT EXISTS idx_orders_order_date ON orders(order_date);

--changeset system:004-03-indexes-positions
--comment: Создание индексов для таблицы positions

CREATE INDEX IF NOT EXISTS idx_positions_figi ON positions(figi);
CREATE INDEX IF NOT EXISTS idx_positions_ticker ON positions(ticker);
CREATE INDEX IF NOT EXISTS idx_positions_currency ON positions(currency);

--changeset system:004-04-indexes-position-risk-state
--comment: Создание индексов для таблицы position_risk_state

CREATE INDEX IF NOT EXISTS idx_position_risk_state_account_figi ON position_risk_state(account_id, figi);
CREATE INDEX IF NOT EXISTS idx_position_risk_state_side ON position_risk_state(side);
CREATE INDEX IF NOT EXISTS idx_position_risk_state_active_sl ON position_risk_state(sl_level) WHERE sl_level IS NOT NULL AND sl_level > 0;
CREATE INDEX IF NOT EXISTS idx_position_risk_state_active_tp ON position_risk_state(tp_level) WHERE tp_level IS NOT NULL AND tp_level > 0;

--changeset system:004-05-indexes-risk-events
--comment: Создание индексов для таблицы risk_events

CREATE INDEX IF NOT EXISTS idx_risk_events_account_figi ON risk_events(account_id, figi);
CREATE INDEX IF NOT EXISTS idx_risk_events_type ON risk_events(event_type);
CREATE INDEX IF NOT EXISTS idx_risk_events_created_at ON risk_events(created_at);

--changeset system:004-06-indexes-order-log
--comment: Создание индексов для таблицы order_log

CREATE INDEX IF NOT EXISTS idx_order_log_order_id ON order_log(order_id);
CREATE INDEX IF NOT EXISTS idx_order_log_changed_at ON order_log(changed_at);

--changeset system:004-07-view-portfolio-summary
--comment: Создание представления portfolio_summary

CREATE OR REPLACE VIEW portfolio_summary AS
SELECT 
    p.currency,
    COUNT(*) as instruments_count,
    SUM(p.balance) as total_balance,
    SUM(p.lots) as total_lots
FROM positions p
GROUP BY p.currency;

--changeset system:004-08-view-active-orders
--comment: Создание представления active_orders

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

--changeset system:004-09-function-get-instrument-stats splitStatements:false stripComments:false
--comment: Создание функции get_instrument_stats

CREATE OR REPLACE FUNCTION get_instrument_stats()
RETURNS TABLE(
    instrument_type VARCHAR,
    count BIGINT,
    currencies TEXT[]
) AS $function$
BEGIN
    RETURN QUERY
    SELECT 
        i.instrument_type,
        COUNT(*)::BIGINT,
        ARRAY_AGG(DISTINCT i.currency)::TEXT[]
    FROM instruments i
    GROUP BY i.instrument_type;
END;
$function$ LANGUAGE plpgsql;

--changeset system:004-10-function-get-order-history splitStatements:false stripComments:false
--comment: Создание функции get_order_history

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
) AS $function$
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
$function$ LANGUAGE plpgsql;

--changeset system:004-11-trigger-order-log splitStatements:false stripComments:false
--comment: Создание функции и триггера для логирования изменений ордеров

CREATE OR REPLACE FUNCTION log_order_changes()
RETURNS TRIGGER AS $function$
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
$function$ LANGUAGE plpgsql;

--changeset system:004-11b-trigger-create splitStatements:false stripComments:false
--comment: Создание триггера для логирования изменений ордеров

DROP TRIGGER IF EXISTS trigger_order_log ON orders;
CREATE TRIGGER trigger_order_log
    AFTER INSERT OR UPDATE OR DELETE ON orders
    FOR EACH ROW
    EXECUTE FUNCTION log_order_changes();

--changeset system:004-12-table-comments
--comment: Добавление комментариев к таблицам

COMMENT ON TABLE trading_settings IS 'Настройки торгового бота';
COMMENT ON TABLE instruments IS 'Финансовые инструменты';
COMMENT ON TABLE orders IS 'Торговые ордера';
COMMENT ON TABLE positions IS 'Позиции в портфеле';
COMMENT ON TABLE order_log IS 'Лог изменений ордеров';
COMMENT ON TABLE position_risk_state IS 'Состояние рисков по позициям (SL/TP/трейлинг)';
COMMENT ON TABLE risk_events IS 'Аудиторские события по рискам';
COMMENT ON VIEW portfolio_summary IS 'Сводка по портфелю';
COMMENT ON VIEW active_orders IS 'Активные ордера';

