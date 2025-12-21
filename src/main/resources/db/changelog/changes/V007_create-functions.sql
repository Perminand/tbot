-- Создание функций PostgreSQL
-- Таблицы уже созданы через Liquibase, поэтому проверки не нужны

-- Функция для получения статистики по инструментам
CREATE OR REPLACE FUNCTION get_instrument_stats()
RETURNS TABLE(
    instrument_type VARCHAR,
    count BIGINT,
    currencies TEXT[]
) AS $func$
BEGIN
    RETURN QUERY
    SELECT 
        i.instrument_type,
        COUNT(*)::BIGINT,
        ARRAY_AGG(DISTINCT i.currency)::TEXT[]
    FROM instruments i
    GROUP BY i.instrument_type;
END;
$func$ LANGUAGE plpgsql;

-- Функция для получения истории ордеров
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
) AS $func$
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
$func$ LANGUAGE plpgsql;

-- Функция для логирования изменений ордеров
CREATE OR REPLACE FUNCTION log_order_changes()
RETURNS TRIGGER AS $func$
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
$func$ LANGUAGE plpgsql;
