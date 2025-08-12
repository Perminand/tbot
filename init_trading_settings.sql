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

-- Настройки маржинальной торговли (значения по умолчанию)
INSERT INTO trading_settings (setting_key, setting_value, description)
VALUES ('margin_enabled', 'false', 'Включить маржинальную торговлю')
ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO trading_settings (setting_key, setting_value, description)
VALUES ('margin_allow_short', 'false', 'Разрешить открытие коротких позиций')
ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO trading_settings (setting_key, setting_value, description)
VALUES ('margin_max_utilization_pct', '0.30', 'Доля портфеля на маржинальные покупки (0..1)')
ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO trading_settings (setting_key, setting_value, description)
VALUES ('margin_max_short_pct', '0.10', 'Доля портфеля на шорт (0..1)')
ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO trading_settings (setting_key, setting_value, description)
VALUES ('margin_max_leverage', '1.30', 'Максимальное кредитное плечо (инфо)')
ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO trading_settings (setting_key, setting_value, description)
VALUES ('margin_safety_pct', '0.50', 'Доля использования доступной маржи (0..1) для безопасного расчета лимитов')
ON CONFLICT (setting_key) DO NOTHING;

-- Риск-лимиты и стоп-правила по умолчанию
INSERT INTO trading_settings (setting_key, setting_value, description)
VALUES ('risk_default_sl_pct', '0.05', 'Стоп-лосс по умолчанию (доля, 0..1)')
ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO trading_settings (setting_key, setting_value, description)
VALUES ('risk_default_tp_pct', '0.10', 'Тейк-профит по умолчанию (доля, 0..1)')
ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO trading_settings (setting_key, setting_value, description)
VALUES ('risk_per_trade_pct', '0.01', 'Риск на сделку (доля портфеля, 0..1)')
ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO trading_settings (setting_key, setting_value, description)
VALUES ('risk_daily_loss_limit_pct', '0.02', 'Дневной лимит убытка (0..1)')
ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO trading_settings (setting_key, setting_value, description)
VALUES ('risk_max_position_share_pct', '0.05', 'Максимальная доля одного инструмента в портфеле (0..1)')
ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO trading_settings (setting_key, setting_value, description)
VALUES ('risk_max_sector_share_pct', '0.20', 'Максимальная доля сектора в портфеле (0..1)')
ON CONFLICT (setting_key) DO NOTHING;

-- Создание индекса для быстрого поиска
CREATE INDEX IF NOT EXISTS idx_trading_settings_key ON trading_settings(setting_key);

-- Проверка созданных настроек
SELECT * FROM trading_settings WHERE setting_key = 'trading_mode';

