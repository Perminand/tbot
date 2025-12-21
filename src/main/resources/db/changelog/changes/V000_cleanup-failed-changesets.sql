-- Очистка неудачных changesets из databasechangelog
-- Этот скрипт удаляет записи о changesets, которые были выполнены, но не создали необходимые таблицы
-- Выполняется только если таблица databasechangelog существует

DO $cleanup$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'databasechangelog') THEN
        -- Удаляем запись о changeset с id=5, если таблицы instruments не существует
        -- Это позволяет выполнить changeset с id=5.1 заново
        IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'instruments') THEN
            DELETE FROM databasechangelog 
            WHERE id = '5' 
              AND author = 'system' 
              AND filename LIKE '%db.changelog-master.yaml';
            
            -- Также удаляем запись о changeset с id=5.1, если она существует, чтобы он выполнился заново
            DELETE FROM databasechangelog 
            WHERE id = '5.1' 
              AND author = 'system' 
              AND filename LIKE '%db.changelog-master.yaml';
        END IF;
    END IF;
END $cleanup$;

