-- ЭКСТРЕННЫЙ СКРИПТ: Остановка флиппинга торговых сигналов
-- Выполнить на продакшене: docker exec tbot_postgres psql -U postgres -d tbot_db -f /tmp/emergency-stop-flipping.sql

-- 1. Временно отключаем автоматические стопы
INSERT INTO trading_settings (setting_key, setting_value, description) 
VALUES ('position_watcher_disabled', 'true', 'ЭКСТРЕННОЕ отключение PositionWatcher для остановки флиппинга')
ON CONFLICT (setting_key) DO UPDATE SET 
    setting_value = 'true',
    description = 'ЭКСТРЕННОЕ отключение PositionWatcher для остановки флиппинга',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO trading_settings (setting_key, setting_value, description) 
VALUES ('virtual_stop_disabled', 'true', 'ЭКСТРЕННОЕ отключение VirtualStopMonitor для остановки флиппинга')
ON CONFLICT (setting_key) DO UPDATE SET 
    setting_value = 'true',
    description = 'ЭКСТРЕННОЕ отключение VirtualStopMonitor для остановки флиппинга',
    updated_at = CURRENT_TIMESTAMP;

-- 2. Увеличиваем кулдауны для предотвращения частых сделок
INSERT INTO trading_settings (setting_key, setting_value, description) 
VALUES ('cooldown.min.minutes', '30', 'ЭКСТРЕННОЕ увеличение минимального кулдауна с 15 до 30 мин')
ON CONFLICT (setting_key) DO UPDATE SET 
    setting_value = '30',
    description = 'ЭКСТРЕННОЕ увеличение минимального кулдауна с 15 до 30 мин',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO trading_settings (setting_key, setting_value, description) 
VALUES ('cooldown.same.minutes', '60', 'ЭКСТРЕННОЕ увеличение кулдауна для того же направления с 30 до 60 мин')
ON CONFLICT (setting_key) DO UPDATE SET 
    setting_value = '60',
    description = 'ЭКСТРЕННОЕ увеличение кулдауна для того же направления с 30 до 60 мин',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO trading_settings (setting_key, setting_value, description) 
VALUES ('cooldown.reverse.minutes', '120', 'ЭКСТРЕННОЕ увеличение кулдауна для смены направления с 45 до 120 мин')
ON CONFLICT (setting_key) DO UPDATE SET 
    setting_value = '120',
    description = 'ЭКСТРЕННОЕ увеличение кулдауна для смены направления с 45 до 120 мин',
    updated_at = CURRENT_TIMESTAMP;

-- 3. Проверяем результат
SELECT setting_key, setting_value, description, updated_at 
FROM trading_settings 
WHERE setting_key IN (
    'position_watcher_disabled',
    'virtual_stop_disabled', 
    'cooldown.min.minutes',
    'cooldown.same.minutes',
    'cooldown.reverse.minutes'
)
ORDER BY setting_key;

-- Сообщение об успешном выполнении
\echo 'ЭКСТРЕННЫЕ НАСТРОЙКИ ПРИМЕНЕНЫ! Флиппинг должен остановиться.'
\echo 'Перезапустите приложение для применения изменений: docker-compose restart tbot-app'
