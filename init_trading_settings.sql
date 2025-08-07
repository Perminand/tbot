-- Инициализация настроек торговли
-- Выполните этот скрипт в базе данных для создания начальных настроек

-- Создание таблицы настроек (если не существует)
CREATE TABLE IF NOT EXISTS trading_settings (
    id BIGSERIAL PRIMARY KEY,
    setting_key VARCHAR(255) UNIQUE NOT NULL,
    setting_value VARCHAR(255) NOT NULL,
    description TEXT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Вставка начальных настроек режима торговли
INSERT INTO trading_settings (setting_key, setting_value, description) 
VALUES ('trading_mode', 'sandbox', 'Режим торговли (sandbox/production)')
ON CONFLICT (setting_key) DO UPDATE SET 
    setting_value = EXCLUDED.setting_value,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

-- Создание индекса для быстрого поиска
CREATE INDEX IF NOT EXISTS idx_trading_settings_key ON trading_settings(setting_key);

-- Проверка созданных настроек
SELECT * FROM trading_settings WHERE setting_key = 'trading_mode';

