-- Добавление колонок для поддержки HARD OCO ордеров
-- Таблица orders уже создана через Liquibase

DO $add_oco_columns$
BEGIN
    -- Добавление колонки message, если её нет
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'orders' AND column_name = 'message'
    ) THEN
        ALTER TABLE orders ADD COLUMN message TEXT;
    END IF;
    
    -- Добавление колонки order_type, если её нет
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'orders' AND column_name = 'order_type'
    ) THEN
        ALTER TABLE orders ADD COLUMN order_type VARCHAR(255);
    END IF;
END $add_oco_columns$;

-- Индексы для OCO (создаются после добавления колонок)
CREATE INDEX IF NOT EXISTS idx_orders_message_oco ON orders(message) 
    WHERE message LIKE 'OCO_GROUP:%';
CREATE INDEX IF NOT EXISTS idx_orders_order_type_hard_oco ON orders(order_type) 
    WHERE order_type LIKE 'HARD_OCO_%';

-- Комментарии к колонкам
COMMENT ON COLUMN orders.message IS 'Сообщение/метаданные ордера, используется для OCO групп (формат: OCO_GROUP:{groupId} | ...)';
COMMENT ON COLUMN orders.order_type IS 'Тип ордера: HARD_OCO_TAKE_PROFIT, HARD_OCO_STOP_LOSS, VIRTUAL_STOP_LOSS, VIRTUAL_TAKE_PROFIT и т.д.';
