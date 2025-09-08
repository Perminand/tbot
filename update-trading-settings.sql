-- Обновление настроек торговли для исправления быстрых закрытий позиций
-- Выполнить в PostgreSQL: docker exec tbot_postgres psql -U postgres -d tbot_db -f /path/to/update-trading-settings.sql

-- 1. Обновляем дефолтные SL/TP на более мягкие значения
INSERT INTO trading_settings (setting_key, setting_value, description) 
VALUES ('risk_default_sl_pct', '0.05', 'Дефолтный Stop Loss 5% (было 2%)')
ON CONFLICT (setting_key) DO UPDATE SET 
    setting_value = '0.05',
    description = 'Дефолтный Stop Loss 5% (было 2%)',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO trading_settings (setting_key, setting_value, description) 
VALUES ('risk_default_tp_pct', '0.12', 'Дефолтный Take Profit 12% (было 6%)')
ON CONFLICT (setting_key) DO UPDATE SET 
    setting_value = '0.12',
    description = 'Дефолтный Take Profit 12% (было 6%)',
    updated_at = CURRENT_TIMESTAMP;

-- 2. Настройки для минимального времени удержания позиции
INSERT INTO trading_settings (setting_key, setting_value, description) 
VALUES ('position.min_hold_time_minutes', '10', 'Минимальное время удержания позиции в минутах')
ON CONFLICT (setting_key) DO UPDATE SET 
    setting_value = '10',
    description = 'Минимальное время удержания позиции в минутах',
    updated_at = CURRENT_TIMESTAMP;

-- 3. Настройки комиссий и минимальной прибыльности
INSERT INTO trading_settings (setting_key, setting_value, description) 
VALUES ('trading.commission_rate_pct', '0.0005', 'Комиссия брокера 0.05% за сделку')
ON CONFLICT (setting_key) DO UPDATE SET 
    setting_value = '0.0005',
    description = 'Комиссия брокера 0.05% за сделку',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO trading_settings (setting_key, setting_value, description) 
VALUES ('trading.min_profitable_spread_pct', '0.0025', 'Минимальный спред для прибыльности 0.25%')
ON CONFLICT (setting_key) DO UPDATE SET 
    setting_value = '0.0025',
    description = 'Минимальный спред для прибыльности 0.25%',
    updated_at = CURRENT_TIMESTAMP;

-- 4. Обновляем существующие риск-правила на новые дефолты
UPDATE risk_rules 
SET 
    stop_loss_pct = 0.05,
    take_profit_pct = 0.12
WHERE stop_loss_pct = 0.02 AND take_profit_pct = 0.06;

-- Вывод результата
SELECT 'Настройки обновлены успешно!' as result;
SELECT setting_key, setting_value, description 
FROM trading_settings 
WHERE setting_key IN (
    'risk_default_sl_pct',
    'risk_default_tp_pct', 
    'position.min_hold_time_minutes',
    'trading.commission_rate_pct',
    'trading.min_profitable_spread_pct'
)
ORDER BY setting_key;
