-- Вставка начальных настроек торговли
-- Таблица trading_settings уже создана через Liquibase

-- Режим торговли
INSERT INTO trading_settings (setting_key, setting_value, description) 
VALUES ('trading_mode', 'sandbox', 'Режим торговли (sandbox/production)')
ON CONFLICT (setting_key) DO UPDATE SET 
    setting_value = EXCLUDED.setting_value,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

-- Настройки маржинальной торговли
INSERT INTO trading_settings (setting_key, setting_value, description)
VALUES ('margin_enabled', 'true', 'Включить маржинальную торговлю')
ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO trading_settings (setting_key, setting_value, description)
VALUES ('margin_allow_short', 'true', 'Разрешить открытие коротких позиций')
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

-- Настройки для разрешения маржинальных покупок при отрицательных средствах
INSERT INTO trading_settings (setting_key, setting_value, description)
VALUES ('margin-trading.allow-negative-cash', 'true', 'Разрешить покупки при отрицательных средствах (используя плечо)')
ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO trading_settings (setting_key, setting_value, description)
VALUES ('margin-trading.min-buying-power-ratio', '0.1', 'Минимальное отношение покупательной способности к стоимости покупки')
ON CONFLICT (setting_key) DO NOTHING;

-- Риск-лимиты и стоп-правила по умолчанию (ОПТИМИЗИРОВАНЫ)
INSERT INTO trading_settings (setting_key, setting_value, description)
VALUES ('risk_default_sl_pct', '0.02', 'Стоп-лосс по умолчанию 2% (было 5%)')
ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO trading_settings (setting_key, setting_value, description)
VALUES ('risk_default_tp_pct', '0.06', 'Тейк-профит по умолчанию 6% (было 10%)')
ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO trading_settings (setting_key, setting_value, description)
VALUES ('risk_default_trailing_pct', '0.03', 'Трейлинг стоп по умолчанию 3%')
ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO trading_settings (setting_key, setting_value, description)
VALUES ('risk_per_trade_pct', '0.005', 'Риск на сделку 0.5% от портфеля (было 1%)')
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

-- Комментарий к таблице
COMMENT ON TABLE trading_settings IS 'Настройки торгового бота';
