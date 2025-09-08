#!/bin/bash
# 📊 МОНИТОРИНГ ТОРГОВОГО БОТА

echo "📊 МОНИТОРИНГ ТОРГОВОГО БОТА"
echo "=============================="

# Проверка статуса процесса
if pgrep -f "java.*Main" > /dev/null; then
    echo "✅ Торговый бот запущен"
    ps aux | grep java | grep Main | grep -v grep | awk '{print "PID: "$2", CPU: "$3"%, RAM: "$4"%"}'
else
    echo "❌ Торговый бот не запущен"
fi

# Проверка файла блокировки
if [ -f "/workspace/TRADING_STOPPED.lock" ]; then
    echo "🚨 Торговля заблокирована файлом TRADING_STOPPED.lock"
fi

# Проверка режима торговли
echo ""
echo "🔍 НАСТРОЙКИ ТОРГОВЛИ:"
grep -E "(default-mode|first-buy-pct|cooldown)" /workspace/src/main/resources/application.yml | head -10

# Проверка последних логов
echo ""
echo "📋 ПОСЛЕДНИЕ СОБЫТИЯ (поиск торговых операций):"
if [ -f "/workspace/logs/application.log" ]; then
    tail -50 /workspace/logs/application.log | grep -E "(BUY|SELL|COOLDOWN|БЛОК)" | tail -10
else
    echo "Файл логов не найден в /workspace/logs/"
    # Попробуем найти логи в других местах
    find /workspace -name "*.log" -type f 2>/dev/null | head -3
fi

echo ""
echo "⏰ Последняя проверка: $(date)"
echo ""
echo "🚨 Для экстренной остановки используйте: ./emergency_stop.sh"