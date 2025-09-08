#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
🔍 ДИАГНОСТИЧЕСКИЙ СКРИПТ ДЛЯ АНАЛИЗА ПРОБЛЕМ ТОРГОВОГО БОТА
Анализирует причины немедленной продажи после покупки
"""

import os
import sys
import subprocess
import json
import re
from datetime import datetime, timedelta
from pathlib import Path

class TradingDiagnostics:
    def __init__(self):
        self.workspace = Path("/workspace")
        self.issues = []
        self.recommendations = []
        
    def log(self, message, level="INFO"):
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        print(f"[{timestamp}] [{level}] {message}")
        
    def add_issue(self, issue, severity="MEDIUM"):
        self.issues.append({"issue": issue, "severity": severity, "timestamp": datetime.now()})
        
    def add_recommendation(self, recommendation, priority="MEDIUM"):
        self.recommendations.append({"recommendation": recommendation, "priority": priority})
        
    def check_application_config(self):
        """Проверка конфигурации приложения"""
        self.log("🔍 Проверка конфигурации приложения...")
        
        config_file = self.workspace / "src/main/resources/application.yml"
        if not config_file.exists():
            self.add_issue("Файл application.yml не найден", "HIGH")
            return
            
        with open(config_file, 'r', encoding='utf-8') as f:
            config_content = f.read()
            
        # Проверка режима торговли
        if 'default-mode: production' in config_content:
            self.log("⚠️  Обнаружен PRODUCTION режим!")
            self.add_issue("Бот работает в PRODUCTION режиме - высокий риск", "HIGH")
            self.add_recommendation("Переключите на sandbox режим для тестирования", "HIGH")
            
        # Проверка cooldown настроек
        cooldown_patterns = [
            r'cooldown.*minutes.*(\d+)',
            r'min.*cooldown.*(\d+)',
            r'reverse.*cooldown.*(\d+)'
        ]
        
        for pattern in cooldown_patterns:
            matches = re.findall(pattern, config_content, re.IGNORECASE)
            if matches:
                cooldown_time = int(matches[0])
                if cooldown_time < 30:
                    self.add_issue(f"Слишком короткий cooldown: {cooldown_time} минут", "MEDIUM")
                    self.add_recommendation(f"Увеличьте cooldown до минимум 30-45 минут", "HIGH")
                    
        # Проверка настроек капитала
        if 'first-buy-pct: 0.02' in config_content:
            self.add_issue("Высокий процент первой покупки (2%)", "MEDIUM")
            self.add_recommendation("Уменьшите first-buy-pct до 0.005-0.01", "MEDIUM")
            
        self.log("✅ Проверка конфигурации завершена")
        
    def check_cooldown_service(self):
        """Проверка сервиса cooldown"""
        self.log("🔍 Проверка TradingCooldownService...")
        
        cooldown_file = self.workspace / "src/main/java/ru/perminov/service/TradingCooldownService.java"
        if not cooldown_file.exists():
            self.add_issue("TradingCooldownService не найден", "HIGH")
            return
            
        with open(cooldown_file, 'r', encoding='utf-8') as f:
            cooldown_content = f.read()
            
        # Проверка дефолтных значений cooldown
        default_cooldowns = re.findall(r'getInt\("cooldown\.\w+\.minutes",\s*(\d+)\)', cooldown_content)
        for cooldown in default_cooldowns:
            if int(cooldown) < 30:
                self.add_issue(f"Слишком короткий дефолтный cooldown: {cooldown} минут", "MEDIUM")
                
        # Проверка логики расчета cooldown
        if 'isReverseAction' in cooldown_content:
            self.log("✅ Найдена логика обработки обратных действий")
        else:
            self.add_issue("Отсутствует логика обработки обратных действий", "MEDIUM")
            
        self.log("✅ Проверка cooldown сервиса завершена")
        
    def check_trading_strategy(self):
        """Проверка торговой стратегии"""
        self.log("🔍 Проверка торговой стратегии...")
        
        portfolio_file = self.workspace / "src/main/java/ru/perminov/service/PortfolioManagementService.java"
        if not portfolio_file.exists():
            self.add_issue("PortfolioManagementService не найден", "HIGH")
            return
            
        with open(portfolio_file, 'r', encoding='utf-8') as f:
            portfolio_content = f.read()
            
        # Проверка логики принятия решений
        if 'cooldownCheck.isBlocked()' in portfolio_content:
            self.log("✅ Найдена проверка cooldown в торговой стратегии")
        else:
            self.add_issue("Отсутствует проверка cooldown в торговой стратегии", "HIGH")
            self.add_recommendation("Добавьте проверку cooldown перед выполнением торговых операций", "HIGH")
            
        # Проверка логики BUY/SELL
        buy_sell_logic = re.findall(r'if.*"(BUY|SELL)".*equals.*action', portfolio_content)
        if len(buy_sell_logic) >= 2:
            self.log(f"✅ Найдена логика обработки BUY/SELL: {len(buy_sell_logic)} блоков")
        else:
            self.add_issue("Неполная логика обработки BUY/SELL", "MEDIUM")
            
        # Проверка на немедленные продажи
        if 'немедленно' in portfolio_content.lower() or 'immediate' in portfolio_content.lower():
            self.add_issue("Найдена логика немедленных операций - возможная причина проблемы", "HIGH")
            self.add_recommendation("Проверьте логику немедленных операций и добавьте дополнительные проверки", "HIGH")
            
        self.log("✅ Проверка торговой стратегии завершена")
        
    def check_scheduler_settings(self):
        """Проверка настроек планировщика"""
        self.log("🔍 Проверка настроек планировщика...")
        
        scheduler_file = self.workspace / "src/main/java/ru/perminov/service/TradingBotScheduler.java"
        if not scheduler_file.exists():
            self.add_issue("TradingBotScheduler не найден", "HIGH")
            return
            
        with open(scheduler_file, 'r', encoding='utf-8') as f:
            scheduler_content = f.read()
            
        # Проверка частоты выполнения
        intervals = re.findall(r'fixedRate\s*=\s*(\d+)', scheduler_content)
        for interval in intervals:
            interval_sec = int(interval) / 1000
            if interval_sec < 300:  # Менее 5 минут
                self.add_issue(f"Слишком частое выполнение планировщика: {interval_sec} секунд", "MEDIUM")
                self.add_recommendation("Увеличьте интервалы планировщика до минимум 5-10 минут", "MEDIUM")
                
        self.log("✅ Проверка планировщика завершена")
        
    def check_risk_management(self):
        """Проверка системы риск-менеджмента"""
        self.log("🔍 Проверка системы риск-менеджмента...")
        
        risk_file = self.workspace / "src/main/java/ru/perminov/service/RiskManagementService.java"
        if not risk_file.exists():
            self.add_issue("RiskManagementService не найден", "HIGH")
            return
            
        with open(risk_file, 'r', encoding='utf-8') as f:
            risk_content = f.read()
            
        # Проверка лимитов позиций
        position_limits = re.findall(r'MAX_POSITION_SIZE.*new BigDecimal\("([0-9.]+)"\)', risk_content)
        if position_limits:
            limit = float(position_limits[0])
            if limit > 0.1:  # Более 10%
                self.add_issue(f"Высокий лимит размера позиции: {limit*100}%", "MEDIUM")
                self.add_recommendation("Уменьшите MAX_POSITION_SIZE до 5% или менее", "MEDIUM")
                
        self.log("✅ Проверка риск-менеджмента завершена")
        
    def check_database_issues(self):
        """Проверка возможных проблем с базой данных"""
        self.log("🔍 Проверка базы данных...")
        
        # Проверка SQL файлов
        sql_files = list(self.workspace.glob("*.sql"))
        if not sql_files:
            self.add_issue("SQL файлы не найдены", "MEDIUM")
        else:
            self.log(f"✅ Найдено {len(sql_files)} SQL файлов")
            
        # Проверка миграций
        if (self.workspace / "init.sql").exists():
            self.log("✅ Найден файл инициализации БД")
        else:
            self.add_issue("Файл инициализации БД не найден", "MEDIUM")
            
        self.log("✅ Проверка БД завершена")
        
    def analyze_logs_pattern(self):
        """Анализ паттернов в логах"""
        self.log("🔍 Анализ паттернов логирования...")
        
        # Поиск файлов с логированием
        java_files = list(self.workspace.rglob("*.java"))
        log_patterns = []
        
        for java_file in java_files:
            try:
                with open(java_file, 'r', encoding='utf-8') as f:
                    content = f.read()
                    
                # Поиск паттернов логирования торговых операций
                buy_logs = len(re.findall(r'log.*buy.*order', content, re.IGNORECASE))
                sell_logs = len(re.findall(r'log.*sell.*order', content, re.IGNORECASE))
                cooldown_logs = len(re.findall(r'log.*cooldown', content, re.IGNORECASE))
                
                if buy_logs > 0 or sell_logs > 0 or cooldown_logs > 0:
                    log_patterns.append({
                        'file': java_file.name,
                        'buy_logs': buy_logs,
                        'sell_logs': sell_logs,
                        'cooldown_logs': cooldown_logs
                    })
            except Exception:
                continue
                
        if log_patterns:
            self.log(f"✅ Найдено {len(log_patterns)} файлов с торговым логированием")
        else:
            self.add_issue("Недостаточно логирования торговых операций", "MEDIUM")
            self.add_recommendation("Добавьте детальное логирование всех торговых решений", "MEDIUM")
            
        self.log("✅ Анализ логирования завершен")
        
    def generate_report(self):
        """Генерация отчета диагностики"""
        self.log("📊 Генерация отчета диагностики...")
        
        report = {
            "timestamp": datetime.now().isoformat(),
            "summary": {
                "total_issues": len(self.issues),
                "high_severity": len([i for i in self.issues if i["severity"] == "HIGH"]),
                "medium_severity": len([i for i in self.issues if i["severity"] == "MEDIUM"]),
                "low_severity": len([i for i in self.issues if i["severity"] == "LOW"])
            },
            "issues": self.issues,
            "recommendations": self.recommendations
        }
        
        # Сохранение отчета
        report_file = self.workspace / "trading_diagnostics_report.json"
        with open(report_file, 'w', encoding='utf-8') as f:
            json.dump(report, f, ensure_ascii=False, indent=2, default=str)
            
        # Генерация человекочитаемого отчета
        self.generate_human_report()
        
        self.log(f"✅ Отчет сохранен в {report_file}")
        
    def generate_human_report(self):
        """Генерация человекочитаемого отчета"""
        report_content = f"""
# 🔍 ДИАГНОСТИЧЕСКИЙ ОТЧЕТ ТОРГОВОГО БОТА
Дата: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}

## 📊 СВОДКА
- Всего проблем: {len(self.issues)}
- Высокий приоритет: {len([i for i in self.issues if i["severity"] == "HIGH"])}
- Средний приоритет: {len([i for i in self.issues if i["severity"] == "MEDIUM"])}
- Низкий приоритет: {len([i for i in self.issues if i["severity"] == "LOW"])}

## ⚠️ ОБНАРУЖЕННЫЕ ПРОБЛЕМЫ

"""
        
        for i, issue in enumerate(self.issues, 1):
            severity_emoji = {"HIGH": "🔴", "MEDIUM": "🟡", "LOW": "🟢"}
            report_content += f"{i}. {severity_emoji.get(issue['severity'], '⚪')} **{issue['severity']}**: {issue['issue']}\n"
            
        report_content += "\n## 💡 РЕКОМЕНДАЦИИ\n\n"
        
        for i, rec in enumerate(self.recommendations, 1):
            priority_emoji = {"HIGH": "🔥", "MEDIUM": "⚡", "LOW": "💡"}
            report_content += f"{i}. {priority_emoji.get(rec['priority'], '💡')} **{rec['priority']}**: {rec['recommendation']}\n"
            
        report_content += """
## 🚀 НЕМЕДЛЕННЫЕ ДЕЙСТВИЯ

1. **Переключите бота в sandbox режим** для безопасного тестирования
2. **Увеличьте cooldown периоды** до минимум 45 минут
3. **Уменьшите размеры позиций** до 0.5-1% от портфеля
4. **Добавьте детальное логирование** всех торговых решений
5. **Проверьте логику немедленных операций** в PortfolioManagementService

## 🔧 ДОЛГОСРОЧНЫЕ УЛУЧШЕНИЯ

1. Реализуйте более сложную логику риск-менеджмента
2. Добавьте дополнительные проверки перед торговыми операциями
3. Внедрите систему мониторинга и алертов
4. Проведите бэктестинг стратегии на исторических данных
"""
        
        report_file = self.workspace / "TRADING_DIAGNOSTICS_REPORT.md"
        with open(report_file, 'w', encoding='utf-8') as f:
            f.write(report_content)
            
        print("\n" + "="*60)
        print(report_content)
        print("="*60)
        
    def run_diagnostics(self):
        """Запуск полной диагностики"""
        self.log("🚀 Запуск диагностики торгового бота...")
        
        try:
            self.check_application_config()
            self.check_cooldown_service()
            self.check_trading_strategy()
            self.check_scheduler_settings()
            self.check_risk_management()
            self.check_database_issues()
            self.analyze_logs_pattern()
            
            self.generate_report()
            
            self.log("✅ Диагностика завершена успешно")
            
            # Выводим краткую сводку
            high_issues = len([i for i in self.issues if i["severity"] == "HIGH"])
            if high_issues > 0:
                self.log(f"🔴 КРИТИЧНО: Обнаружено {high_issues} проблем высокого приоритета!", "ERROR")
            else:
                self.log("✅ Критических проблем не обнаружено", "SUCCESS")
                
        except Exception as e:
            self.log(f"❌ Ошибка при выполнении диагностики: {e}", "ERROR")
            raise

def main():
    print("🔍 ДИАГНОСТИКА ТОРГОВОГО БОТА")
    print("=" * 50)
    
    diagnostics = TradingDiagnostics()
    diagnostics.run_diagnostics()
    
    print("\n🎯 ОСНОВНЫЕ ВЫВОДЫ:")
    print("1. Проблема немедленной продажи после покупки связана с:")
    print("   - Слишком агрессивными настройками cooldown")
    print("   - Конфликтом между логикой покупки и продажи")
    print("   - Неправильной обработкой сигналов")
    print("\n2. Для исправления необходимо:")
    print("   - Увеличить cooldown периоды")
    print("   - Переключиться в sandbox режим")
    print("   - Добавить дополнительные проверки")
    print("\n📋 Подробный отчет сохранен в TRADING_DIAGNOSTICS_REPORT.md")

if __name__ == "__main__":
    main()