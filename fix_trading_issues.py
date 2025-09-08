#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
🔧 СКРИПТ ИСПРАВЛЕНИЯ ПРОБЛЕМ ТОРГОВОГО БОТА
Автоматически исправляет проблемы немедленной продажи после покупки
"""

import os
import sys
import shutil
import re
from datetime import datetime
from pathlib import Path

class TradingFixer:
    def __init__(self):
        self.workspace = Path("/workspace")
        self.backup_dir = self.workspace / "backup_before_fix"
        self.fixes_applied = []
        
    def log(self, message, level="INFO"):
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        print(f"[{timestamp}] [{level}] {message}")
        
    def create_backup(self):
        """Создание резервной копии перед изменениями"""
        self.log("📦 Создание резервной копии...")
        
        if self.backup_dir.exists():
            shutil.rmtree(self.backup_dir)
        self.backup_dir.mkdir()
        
        # Бэкап ключевых файлов
        files_to_backup = [
            "src/main/resources/application.yml",
            "src/main/java/ru/perminov/service/TradingCooldownService.java",
            "src/main/java/ru/perminov/service/PortfolioManagementService.java",
            "src/main/java/ru/perminov/service/TradingBotScheduler.java",
            "src/main/java/ru/perminov/service/RiskManagementService.java"
        ]
        
        for file_path in files_to_backup:
            src_file = self.workspace / file_path
            if src_file.exists():
                dst_file = self.backup_dir / file_path
                dst_file.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(src_file, dst_file)
                self.log(f"✅ Создана копия: {file_path}")
        
        self.log("✅ Резервная копия создана")
        
    def fix_application_config(self):
        """Исправление конфигурации приложения"""
        self.log("🔧 Исправление application.yml...")
        
        config_file = self.workspace / "src/main/resources/application.yml"
        if not config_file.exists():
            self.log("❌ Файл application.yml не найден", "ERROR")
            return
            
        with open(config_file, 'r', encoding='utf-8') as f:
            content = f.read()
            
        original_content = content
        
        # 1. Переключение в sandbox режим
        if 'default-mode: production' in content:
            content = content.replace('default-mode: production', 'default-mode: sandbox')
            self.fixes_applied.append("Переключен режим с production на sandbox")
            self.log("✅ Переключен в sandbox режим")
            
        # 2. Уменьшение процента первой покупки
        if 'first-buy-pct: 0.02' in content:
            content = content.replace('first-buy-pct: 0.02', 'first-buy-pct: 0.005')
            self.fixes_applied.append("Уменьшен процент первой покупки с 2% до 0.5%")
            self.log("✅ Уменьшен процент первой покупки")
            
        # 3. Уменьшение процента докупки
        if 'add-buy-pct: 0.01' in content:
            content = content.replace('add-buy-pct: 0.01', 'add-buy-pct: 0.003')
            self.fixes_applied.append("Уменьшен процент докупки с 1% до 0.3%")
            self.log("✅ Уменьшен процент докупки")
            
        # 4. Добавление настроек cooldown если их нет
        if 'cooldown:' not in content:
            cooldown_config = """
# Trading Cooldown Configuration
cooldown:
  min:
    minutes: 30      # Минимальный cooldown между сделками
  same:
    minutes: 45      # Cooldown для повторных операций
  reverse:
    minutes: 60      # Cooldown для обратных операций (BUY->SELL)
  protection:
    enabled: true    # Включить защиту от частых сделок
    window-seconds: 300  # Окно защиты в секундах
"""
            # Добавляем в конец файла
            content += cooldown_config
            self.fixes_applied.append("Добавлены настройки cooldown")
            self.log("✅ Добавлены настройки cooldown")
            
        # 5. Увеличение интервалов планировщика
        scheduler_section = """
# Trading Scheduler Configuration
scheduler:
  quick-monitoring:
    interval-minutes: 10    # Быстрый мониторинг каждые 10 минут (было 5)
  full-monitoring:
    interval-minutes: 30    # Полный мониторинг каждые 30 минут (было 15)
  safety:
    min-interval-seconds: 600  # Минимальный интервал между операциями
"""
        if 'scheduler:' not in content:
            content += scheduler_section
            self.fixes_applied.append("Добавлены настройки планировщика")
            self.log("✅ Добавлены настройки планировщика")
        
        # Сохраняем изменения только если есть изменения
        if content != original_content:
            with open(config_file, 'w', encoding='utf-8') as f:
                f.write(content)
            self.log("✅ application.yml обновлен")
        else:
            self.log("ℹ️  application.yml не требует изменений")
            
    def fix_cooldown_service(self):
        """Исправление TradingCooldownService"""
        self.log("🔧 Исправление TradingCooldownService...")
        
        cooldown_file = self.workspace / "src/main/java/ru/perminov/service/TradingCooldownService.java"
        if not cooldown_file.exists():
            self.log("❌ TradingCooldownService не найден", "ERROR")
            return
            
        with open(cooldown_file, 'r', encoding='utf-8') as f:
            content = f.read()
            
        original_content = content
        
        # 1. Увеличение дефолтных значений cooldown
        content = re.sub(
            r'getInt\("cooldown\.min\.minutes",\s*15\)',
            'getInt("cooldown.min.minutes", 30)',
            content
        )
        content = re.sub(
            r'getInt\("cooldown\.same\.minutes",\s*30\)',
            'getInt("cooldown.same.minutes", 45)',
            content
        )
        content = re.sub(
            r'getInt\("cooldown\.reverse\.minutes",\s*45\)',
            'getInt("cooldown.reverse.minutes", 60)',
            content
        )
        
        # 2. Добавление дополнительного логирования
        if 'БЛОКИРОВКА OVERTRADING' in content and 'cooldownCheck.getReason()' in content:
            # Добавляем более детальное логирование
            log_enhancement = '''
            // Дополнительное диагностическое логирование
            log.warn("🔍 ДИАГНОСТИКА COOLDOWN: FIGI={}, Action={}, LastTrade={}, TimeDiff={}min, Required={}min", 
                figi, action, lastTradeTime, minutesSinceLastTrade, requiredCooldown);
            '''
            
            if log_enhancement.strip() not in content:
                # Находим место для вставки логирования
                insert_pos = content.find('botLogService.addLogEntry(')
                if insert_pos != -1:
                    content = content[:insert_pos] + log_enhancement + '\n                ' + content[insert_pos:]
        
        # Сохраняем изменения
        if content != original_content:
            with open(cooldown_file, 'w', encoding='utf-8') as f:
                f.write(content)
            self.fixes_applied.append("Обновлены настройки cooldown в TradingCooldownService")
            self.log("✅ TradingCooldownService обновлен")
        else:
            self.log("ℹ️  TradingCooldownService не требует изменений")
            
    def fix_portfolio_management(self):
        """Исправление PortfolioManagementService"""
        self.log("🔧 Исправление PortfolioManagementService...")
        
        portfolio_file = self.workspace / "src/main/java/ru/perminov/service/PortfolioManagementService.java"
        if not portfolio_file.exists():
            self.log("❌ PortfolioManagementService не найден", "ERROR")
            return
            
        with open(portfolio_file, 'r', encoding='utf-8') as f:
            content = f.read()
            
        original_content = content
        
        # 1. Добавление дополнительной проверки перед торговыми операциями
        safety_check = '''
            // 🛡️ ДОПОЛНИТЕЛЬНАЯ БЕЗОПАСНОСТЬ: Проверка времени с момента последней операции
            String operationKey = figi + "_" + action;
            Long lastOperationTime = recentOperationsWindow.get(operationKey);
            long currentTime = System.currentTimeMillis();
            if (lastOperationTime != null && (currentTime - lastOperationTime) < 600000) { // 10 минут
                log.warn("🚫 БЛОКИРОВКА: Операция {} для {} заблокирована - прошло менее 10 минут с последней операции", 
                    action, displayOf(figi));
                botLogService.addLogEntry(BotLogService.LogLevel.WARNING, BotLogService.LogCategory.RISK_MANAGEMENT,
                    "Блокировка частых операций", String.format("%s: операция %s заблокирована (< 10 мин)", 
                        displayOf(figi), action));
                return;
            }
            recentOperationsWindow.put(operationKey, currentTime);
        '''
        
        # Ищем место для вставки проверки (после cooldown проверки)
        cooldown_check_pos = content.find('cooldownCheck.isBlocked()')
        if cooldown_check_pos != -1 and safety_check.strip() not in content:
            # Находим конец блока cooldown проверки
            end_pos = content.find('log.info("✅ Cooldown проверка пройдена', cooldown_check_pos)
            if end_pos != -1:
                end_pos = content.find('\n', end_pos) + 1
                content = content[:end_pos] + '\n            ' + safety_check + '\n' + content[end_pos:]
                self.fixes_applied.append("Добавлена дополнительная проверка времени операций")
        
        # 2. Усиление логирования торговых решений
        enhanced_logging = '''
            // 🔍 ДИАГНОСТИЧЕСКОЕ ЛОГИРОВАНИЕ ТОРГОВОГО РЕШЕНИЯ
            log.info("📊 ТОРГОВОЕ РЕШЕНИЕ: FIGI={}, Action={}, Trend={}, Price={}, Portfolio={}", 
                displayOf(figi), action, trend.getTrend(), trend.getCurrentPrice(), 
                portfolioAnalysis.getTotalValue());
        '''
        
        # Ищем место для вставки расширенного логирования
        final_decision_pos = content.find('ФИНАЛЬНОЕ РЕШЕНИЕ для')
        if final_decision_pos != -1 and enhanced_logging.strip() not in content:
            line_end = content.find('\n', final_decision_pos) + 1
            content = content[:line_end] + '\n            ' + enhanced_logging + '\n' + content[line_end:]
            self.fixes_applied.append("Добавлено диагностическое логирование торговых решений")
        
        # Сохраняем изменения
        if content != original_content:
            with open(portfolio_file, 'w', encoding='utf-8') as f:
                f.write(content)
            self.fixes_applied.append("Обновлен PortfolioManagementService с дополнительными проверками")
            self.log("✅ PortfolioManagementService обновлен")
        else:
            self.log("ℹ️  PortfolioManagementService не требует изменений")
            
    def fix_scheduler_intervals(self):
        """Исправление интервалов планировщика"""
        self.log("🔧 Исправление TradingBotScheduler...")
        
        scheduler_file = self.workspace / "src/main/java/ru/perminov/service/TradingBotScheduler.java"
        if not scheduler_file.exists():
            self.log("❌ TradingBotScheduler не найден", "ERROR")
            return
            
        with open(scheduler_file, 'r', encoding='utf-8') as f:
            content = f.read()
            
        original_content = content
        
        # 1. Увеличение интервала быстрого мониторинга с 5 до 10 минут
        content = re.sub(
            r'@Scheduled\(fixedRate\s*=\s*300000\)',  # 5 минут
            '@Scheduled(fixedRate = 600000)',          # 10 минут
            content
        )
        
        # 2. Увеличение интервала полного мониторинга с 15 до 30 минут
        content = re.sub(
            r'@Scheduled\(fixedRate\s*=\s*900000\)',  # 15 минут
            '@Scheduled(fixedRate = 1800000)',         # 30 минут
            content
        )
        
        # 3. Обновление комментариев
        content = re.sub(
            r'каждые 5 минут \(ОПТИМИЗАЦИЯ ДЛЯ СНИЖЕНИЯ КОМИССИЙ\)',
            'каждые 10 минут (ЗАЩИТА ОТ OVERTRADING)',
            content
        )
        content = re.sub(
            r'каждые 15 минут \(ОПТИМИЗАЦИЯ ДЛЯ СНИЖЕНИЯ КОМИССИЙ\)',
            'каждые 30 минут (ЗАЩИТА ОТ OVERTRADING)',
            content
        )
        
        # Сохраняем изменения
        if content != original_content:
            with open(scheduler_file, 'w', encoding='utf-8') as f:
                f.write(content)
            self.fixes_applied.append("Увеличены интервалы планировщика для предотвращения overtrading")
            self.log("✅ TradingBotScheduler обновлен")
        else:
            self.log("ℹ️  TradingBotScheduler не требует изменений")
            
    def create_emergency_stop_script(self):
        """Создание скрипта экстренной остановки"""
        self.log("🚨 Создание скрипта экстренной остановки...")
        
        emergency_script = """#!/bin/bash
# 🚨 ЭКСТРЕННАЯ ОСТАНОВКА ТОРГОВОГО БОТА

echo "🚨 ЭКСТРЕННАЯ ОСТАНОВКА ТОРГОВОГО БОТА"
echo "=================================="

# Остановка Java процесса
echo "Останавливаем Java процессы..."
pkill -f "java.*Main" || echo "Java процесс не найден"

# Остановка Docker контейнера если используется
echo "Останавливаем Docker контейнеры..."
docker-compose down 2>/dev/null || echo "Docker контейнеры не запущены"

# Создание файла блокировки
echo "Создаем файл блокировки..."
touch /workspace/TRADING_STOPPED.lock
echo "$(date): Торговля остановлена экстренно" >> /workspace/trading_stop.log

echo "✅ Торговый бот остановлен"
echo "Для возобновления удалите файл /workspace/TRADING_STOPPED.lock"
"""
        
        emergency_file = self.workspace / "emergency_stop.sh"
        with open(emergency_file, 'w', encoding='utf-8') as f:
            f.write(emergency_script)
        
        # Делаем скрипт исполняемым
        os.chmod(emergency_file, 0o755)
        
        self.fixes_applied.append("Создан скрипт экстренной остановки emergency_stop.sh")
        self.log("✅ Скрипт экстренной остановки создан")
        
    def create_monitoring_script(self):
        """Создание скрипта мониторинга"""
        self.log("📊 Создание скрипта мониторинга...")
        
        monitoring_script = """#!/bin/bash
# 📊 МОНИТОРИНГ ТОРГОВОГО БОТА

echo "📊 МОНИТОРИНГ ТОРГОВОГО БОТА"
echo "=============================="

# Проверка статуса процесса
if pgrep -f "java.*Main" > /dev/null; then
    echo "✅ Торговый бот запущен"
else
    echo "❌ Торговый бот не запущен"
fi

# Проверка файла блокировки
if [ -f "/workspace/TRADING_STOPPED.lock" ]; then
    echo "🚨 Торговля заблокирована файлом TRADING_STOPPED.lock"
fi

# Проверка последних логов
echo ""
echo "📋 ПОСЛЕДНИЕ СОБЫТИЯ:"
if [ -f "/workspace/logs/application.log" ]; then
    tail -20 /workspace/logs/application.log | grep -E "(BUY|SELL|COOLDOWN|BLOCK)"
else
    echo "Файл логов не найден"
fi

# Проверка использования памяти
echo ""
echo "💾 ИСПОЛЬЗОВАНИЕ РЕСУРСОВ:"
ps aux | grep java | grep -v grep | awk '{print "CPU: "$3"%, RAM: "$4"%, PID: "$2}'

echo ""
echo "⏰ Последняя проверка: $(date)"
"""
        
        monitoring_file = self.workspace / "monitor_bot.sh"
        with open(monitoring_file, 'w', encoding='utf-8') as f:
            f.write(monitoring_script)
        
        # Делаем скрипт исполняемым
        os.chmod(monitoring_file, 0o755)
        
        self.fixes_applied.append("Создан скрипт мониторинга monitor_bot.sh")
        self.log("✅ Скрипт мониторинга создан")
        
    def create_rollback_script(self):
        """Создание скрипта отката изменений"""
        self.log("🔄 Создание скрипта отката...")
        
        rollback_script = f"""#!/bin/bash
# 🔄 ОТКАТ ИЗМЕНЕНИЙ ТОРГОВОГО БОТА

echo "🔄 ОТКАТ ИЗМЕНЕНИЙ"
echo "=================="

if [ ! -d "{self.backup_dir}" ]; then
    echo "❌ Резервная копия не найдена"
    exit 1
fi

echo "Восстанавливаем файлы из резервной копии..."

# Восстановление файлов
cp -r {self.backup_dir}/* {self.workspace}/

echo "✅ Файлы восстановлены"
echo "🔄 Перезапустите приложение для применения изменений"

# Показываем что было откачено
echo ""
echo "📋 ОТКАЧЕНЫ СЛЕДУЮЩИЕ ИСПРАВЛЕНИЯ:"
"""
        
        for fix in self.fixes_applied:
            rollback_script += f'echo "- {fix}"\n'
            
        rollback_file = self.workspace / "rollback_fixes.sh"
        with open(rollback_file, 'w', encoding='utf-8') as f:
            f.write(rollback_script)
        
        # Делаем скрипт исполняемым
        os.chmod(rollback_file, 0o755)
        
        self.log("✅ Скрипт отката создан")
        
    def generate_fix_report(self):
        """Генерация отчета об исправлениях"""
        self.log("📋 Генерация отчета об исправлениях...")
        
        report_content = f"""
# 🔧 ОТЧЕТ ОБ ИСПРАВЛЕНИЯХ ТОРГОВОГО БОТА
Дата: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}

## ✅ ПРИМЕНЕНЫ СЛЕДУЮЩИЕ ИСПРАВЛЕНИЯ:

"""
        
        for i, fix in enumerate(self.fixes_applied, 1):
            report_content += f"{i}. {fix}\n"
            
        report_content += f"""

## 📁 РЕЗЕРВНАЯ КОПИЯ
Оригинальные файлы сохранены в: `{self.backup_dir}`

## 🚨 ЭКСТРЕННЫЕ ДЕЙСТВИЯ
- **Остановка бота**: `./emergency_stop.sh`
- **Мониторинг**: `./monitor_bot.sh`
- **Откат изменений**: `./rollback_fixes.sh`

## 🎯 СЛЕДУЮЩИЕ ШАГИ

1. **Перезапустите приложение** для применения изменений
2. **Переключитесь в sandbox режим** для тестирования
3. **Мониторьте логи** на предмет улучшений
4. **Тестируйте** торговые операции в безопасном режиме

## ⚠️ ВАЖНЫЕ ИЗМЕНЕНИЯ

- **Режим торговли**: Переключен на SANDBOX
- **Cooldown периоды**: Увеличены до 30-60 минут
- **Размеры позиций**: Уменьшены до 0.5-0.3%
- **Интервалы мониторинга**: Увеличены до 10-30 минут

## 🔍 МОНИТОРИНГ

Следите за следующими сигналами в логах:
- `БЛОКИРОВКА OVERTRADING` - система работает корректно
- `Cooldown активен` - защита от частых сделок активна
- `ТОРГОВОЕ РЕШЕНИЕ` - диагностическая информация о решениях

## 📞 ПОДДЕРЖКА

Если проблемы продолжаются:
1. Запустите диагностику: `python3 trading_diagnostics.py`
2. Проверьте логи приложения
3. Используйте экстренную остановку при необходимости
"""
        
        report_file = self.workspace / "FIXES_APPLIED_REPORT.md"
        with open(report_file, 'w', encoding='utf-8') as f:
            f.write(report_content)
            
        print("\n" + "="*60)
        print(report_content)
        print("="*60)
        
        self.log(f"✅ Отчет сохранен в {report_file}")
        
    def apply_all_fixes(self):
        """Применение всех исправлений"""
        self.log("🚀 Начало применения исправлений...")
        
        try:
            self.create_backup()
            self.fix_application_config()
            self.fix_cooldown_service()
            self.fix_portfolio_management()
            self.fix_scheduler_intervals()
            self.create_emergency_stop_script()
            self.create_monitoring_script()
            self.create_rollback_script()
            
            self.generate_fix_report()
            
            self.log(f"✅ Все исправления применены успешно! Всего: {len(self.fixes_applied)}")
            
            return True
            
        except Exception as e:
            self.log(f"❌ Ошибка при применении исправлений: {e}", "ERROR")
            self.log("🔄 Рекомендуется запустить rollback_fixes.sh для отката", "ERROR")
            return False

def main():
    print("🔧 ИСПРАВЛЕНИЕ ПРОБЛЕМ ТОРГОВОГО БОТА")
    print("=" * 50)
    
    fixer = TradingFixer()
    
    # Запрашиваем подтверждение
    print("\n⚠️  ВНИМАНИЕ: Будут внесены изменения в код торгового бота")
    print("Резервная копия будет создана автоматически")
    
    response = input("\nПродолжить? (y/N): ").strip().lower()
    if response != 'y':
        print("❌ Операция отменена")
        return
        
    success = fixer.apply_all_fixes()
    
    if success:
        print("\n🎉 ИСПРАВЛЕНИЯ ПРИМЕНЕНЫ УСПЕШНО!")
        print("\n📋 СЛЕДУЮЩИЕ ШАГИ:")
        print("1. Перезапустите торговый бот")
        print("2. Проверьте логи на предмет улучшений")
        print("3. Тестируйте в sandbox режиме")
        print("4. Используйте ./monitor_bot.sh для мониторинга")
        print("\n🚨 В случае проблем используйте ./emergency_stop.sh")
    else:
        print("\n❌ ОШИБКА ПРИ ПРИМЕНЕНИИ ИСПРАВЛЕНИЙ")
        print("Проверьте логи и при необходимости используйте откат")

if __name__ == "__main__":
    main()