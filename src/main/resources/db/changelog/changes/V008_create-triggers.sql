-- Создание триггеров для автоматического логирования
-- Таблицы и функции уже созданы через Liquibase

DROP TRIGGER IF EXISTS trigger_order_log ON orders;
CREATE TRIGGER trigger_order_log
    AFTER INSERT OR UPDATE OR DELETE ON orders
    FOR EACH ROW
    EXECUTE FUNCTION log_order_changes();
