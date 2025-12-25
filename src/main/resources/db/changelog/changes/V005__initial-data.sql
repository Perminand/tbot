--liquibase formatted sql

--changeset system:005-01-trading-settings
--comment: Вставка начальных настроек торговли

INSERT INTO trading_settings (setting_key, setting_value, description) 
VALUES ('trading_mode', 'sandbox', 'Режим торговли (sandbox/production)')
ON CONFLICT (setting_key) DO UPDATE SET 
    setting_value = EXCLUDED.setting_value,
    description = EXCLUDED.description,
    updated_at = NOW();

--changeset system:005-02-margin-settings
--comment: Вставка настроек маржинальной торговли

INSERT INTO trading_settings (setting_key, setting_value, description)
VALUES 
    ('margin_enabled', 'true', 'Включить маржинальную торговлю'),
    ('margin_allow_short', 'true', 'Разрешить открытие коротких позиций'),
    ('margin_max_utilization_pct', '0.30', 'Доля портфеля на маржинальные покупки (0..1)'),
    ('margin_max_short_pct', '0.10', 'Доля портфеля на шорт (0..1)'),
    ('margin_max_leverage', '1.30', 'Максимальное кредитное плечо (инфо)'),
    ('margin_safety_pct', '0.50', 'Доля использования доступной маржи (0..1) для безопасного расчета лимитов'),
    ('margin-trading.allow-negative-cash', 'true', 'Разрешить покупки при отрицательных средствах (используя плечо)'),
    ('margin-trading.min-buying-power-ratio', '0.1', 'Минимальное отношение покупательной способности к стоимости покупки')
ON CONFLICT (setting_key) DO NOTHING;

--changeset system:005-03-risk-settings
--comment: Вставка риск-лимитов и стоп-правил

INSERT INTO trading_settings (setting_key, setting_value, description)
VALUES 
    ('risk_default_sl_pct', '0.02', 'Стоп-лосс по умолчанию 2% (было 5%)'),
    ('risk_default_tp_pct', '0.06', 'Тейк-профит по умолчанию 6% (было 10%)'),
    ('risk_default_trailing_pct', '0.03', 'Трейлинг стоп по умолчанию 3%'),
    ('risk_per_trade_pct', '0.005', 'Риск на сделку 0.5% от портфеля (было 1%)'),
    ('risk_daily_loss_limit_pct', '0.02', 'Дневной лимит убытка (0..1)'),
    ('risk_max_position_share_pct', '0.05', 'Максимальная доля одного инструмента в портфеле (0..1)'),
    ('risk_max_sector_share_pct', '0.20', 'Максимальная доля сектора в портфеле (0..1)')
ON CONFLICT (setting_key) DO NOTHING;

