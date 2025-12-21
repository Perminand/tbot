# Миграция на Liquibase

Все изменения базы данных теперь управляются через Liquibase в формате YAML с SQL файлами.

## Структура changelog

```
src/main/resources/db/changelog/
├── db.changelog-master.yaml         # Главный файл в формате YAML
└── changes/
    ├── V001_create-extension.sql              # Расширения PostgreSQL
    ├── V002_create-risk-tables.sql            # Таблицы управления рисками
    ├── V003_create-order-log-table.sql        # Таблица логов ордеров
    ├── V004_create-performance-metrics-table.sql # Таблица метрик производительности
    ├── V005_create-indexes.sql                # Индексы для оптимизации
    ├── V006_create-views.sql                  # Представления (views)
    ├── V007_create-functions.sql              # Функции PostgreSQL
    ├── V008_create-triggers.sql               # Триггеры
    ├── V009_insert-initial-data.sql           # Начальные данные
    └── V010_add-hard-oco-columns.sql          # Колонки для HARD OCO
```

## Порядок выполнения

1. **V001_create-extension.sql** - Создание расширений PostgreSQL (uuid-ossp)
2. **V002_create-risk-tables.sql** - Таблицы `position_risk_state` и `risk_events`
3. **V003_create-order-log-table.sql** - Таблица `order_log`
4. **V004_create-performance-metrics-table.sql** - Таблица метрик производительности
5. **V005_create-indexes.sql** - Индексы для всех таблиц (с проверкой существования через DO блоки)
6. **V006_create-views.sql** - Представления `portfolio_summary` и `active_orders`
7. **V007_create-functions.sql** - Функции PostgreSQL
8. **V008_create-triggers.sql** - Триггер для логирования изменений ордеров
9. **V009_insert-initial-data.sql** - Начальные настройки торговли
10. **V010_add-hard-oco-columns.sql** - Колонки `message` и `order_type` для orders

## Важные замечания

- **JPA Entities**: Таблицы, создаваемые через Hibernate (instruments, orders, positions, trading_settings, etc.), создаются автоматически при старте приложения
- **Проверка существования**: Все SQL файлы используют DO блоки PostgreSQL для проверки существования таблиц перед выполнением операций
- **Порядок выполнения**: Liquibase выполнит изменения в порядке, указанном в master changelog (YAML)
- **Идемпотентность**: Все изменения идемпотентны - их можно выполнять многократно без ошибок (используются IF NOT EXISTS, CREATE OR REPLACE, ON CONFLICT)
- **Формат**: Используется YAML формат для master changelog и SQL файлы для всех изменений (как в примере grekhov_backend)

## Устаревшие файлы

Следующие SQL файлы больше не используются напрямую (перенесены в Liquibase):
- `init.sql` - теперь минимальный (только комментарий)
- `risk-tables.sql` - перенесено в `V002_create-risk-tables.sql`
- `post-init.sql` - перенесено в SQL changelog файлы
- `init_trading_settings.sql` - перенесено в `V009_insert-initial-data.sql`
- `add_hard_oco_columns.sql` - перенесено в `V010_add-hard-oco-columns.sql`

Все старые XML changelog файлы удалены и заменены на SQL файлы.

## Добавление новых миграций

Для добавления новой миграции:

1. Создайте новый SQL файл в `src/main/resources/db/changelog/changes/` с номером больше последнего (например, `V011_...`)
2. Добавьте новый `changeSet` в `db.changelog-master.yaml`:
   ```yaml
   - changeSet:
       id: 11
       author: system
       changes:
         - sqlFile:
             path: changes/V011_your-migration-name.sql
             relativeToChangelogFile: true
             comments: "описание миграции"
   ```
3. При следующем запуске приложения Liquibase автоматически применит изменения

## Проверка статуса миграций

Liquibase автоматически создает таблицу `databasechangelog` для отслеживания выполненных изменений.

Для проверки статуса можно выполнить SQL запрос:
```sql
SELECT * FROM databasechangelog ORDER BY dateexecuted DESC;
```

