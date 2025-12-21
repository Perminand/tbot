-- Создание таблицы для логирования изменений ордеров
CREATE TABLE IF NOT EXISTS order_log (
    id SERIAL PRIMARY KEY,
    order_id VARCHAR NOT NULL,
    action VARCHAR(10) NOT NULL,
    old_status VARCHAR(20),
    new_status VARCHAR(20),
    changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Индексы
CREATE INDEX IF NOT EXISTS idx_order_log_order_id ON order_log(order_id);
CREATE INDEX IF NOT EXISTS idx_order_log_changed_at ON order_log(changed_at);

-- Комментарии
COMMENT ON TABLE order_log IS 'Лог изменений ордеров';

