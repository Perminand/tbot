-- Миграция для добавления поддержки HARD OCO ордеров
-- Добавляет колонки message и проверяет наличие order_type в таблице orders

-- Проверяем и добавляем колонку message, если её нет
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 
        FROM information_schema.columns 
        WHERE table_name = 'orders' 
        AND column_name = 'message'
    ) THEN
        ALTER TABLE orders ADD COLUMN message TEXT;
        RAISE NOTICE 'Колонка message добавлена в таблицу orders';
    ELSE
        RAISE NOTICE 'Колонка message уже существует в таблице orders';
    END IF;
END $$;

-- Проверяем и добавляем колонку order_type, если её нет
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 
        FROM information_schema.columns 
        WHERE table_name = 'orders' 
        AND column_name = 'order_type'
    ) THEN
        ALTER TABLE orders ADD COLUMN order_type VARCHAR(255);
        RAISE NOTICE 'Колонка order_type добавлена в таблицу orders';
    ELSE
        RAISE NOTICE 'Колонка order_type уже существует в таблице orders';
    END IF;
END $$;

-- Создаем индекс для быстрого поиска OCO групп по message
CREATE INDEX IF NOT EXISTS idx_orders_message_oco ON orders(message) 
WHERE message LIKE 'OCO_GROUP:%';

-- Создаем индекс для поиска HARD OCO ордеров по типу
CREATE INDEX IF NOT EXISTS idx_orders_order_type_hard_oco ON orders(order_type) 
WHERE order_type LIKE 'HARD_OCO_%';

-- Комментарии к колонкам
COMMENT ON COLUMN orders.message IS 'Сообщение/метаданные ордера, используется для OCO групп (формат: OCO_GROUP:{groupId} | ...)';
COMMENT ON COLUMN orders.order_type IS 'Тип ордера: HARD_OCO_TAKE_PROFIT, HARD_OCO_STOP_LOSS, VIRTUAL_STOP_LOSS, VIRTUAL_TAKE_PROFIT и т.д.';

