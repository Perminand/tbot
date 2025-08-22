-- Включение маржинальной торговли и шортов для продакшена
-- Выполните этот скрипт в базе данных для активации шортов

-- Включаем маржинальную торговлю
UPDATE trading_settings 
SET setting_value = 'true', 
    updated_at = CURRENT_TIMESTAMP 
WHERE setting_key = 'margin_enabled';

-- Включаем шорты
UPDATE trading_settings 
SET setting_value = 'true', 
    updated_at = CURRENT_TIMESTAMP 
WHERE setting_key = 'margin_allow_short';

-- Настраиваем параметры маржинальной торговли для продакшена
UPDATE trading_settings 
SET setting_value = '0.40', 
    updated_at = CURRENT_TIMESTAMP 
WHERE setting_key = 'margin_max_utilization_pct';

UPDATE trading_settings 
SET setting_value = '0.15', 
    updated_at = CURRENT_TIMESTAMP 
WHERE setting_key = 'margin_max_short_pct';

UPDATE trading_settings 
SET setting_value = '0.70', 
    updated_at = CURRENT_TIMESTAMP 
WHERE setting_key = 'margin_safety_pct';

-- Проверяем обновленные настройки
SELECT setting_key, setting_value, description 
FROM trading_settings 
WHERE setting_key IN ('margin_enabled', 'margin_allow_short', 'margin_max_utilization_pct', 'margin_max_short_pct', 'margin_safety_pct')
ORDER BY setting_key;
