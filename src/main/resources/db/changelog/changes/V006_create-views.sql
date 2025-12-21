-- Создание представлений (views) для удобных запросов
-- Таблицы уже созданы через Liquibase, поэтому проверки не нужны

-- Представление для портфеля
CREATE OR REPLACE VIEW portfolio_summary AS
SELECT 
    p.currency,
    COUNT(*) as instruments_count,
    SUM(p.balance) as total_balance,
    SUM(p.lots) as total_lots
FROM positions p
GROUP BY p.currency;

COMMENT ON VIEW portfolio_summary IS 'Сводка по портфелю';

-- Представление для активных ордеров
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

COMMENT ON VIEW active_orders IS 'Активные ордера';
