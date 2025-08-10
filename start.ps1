# Простой скрипт для запуска Tinkoff Trading Bot на Windows
# Использование: .\start.ps1

Write-Host "🚀 Запуск Tinkoff Trading Bot..." -ForegroundColor Green

# Проверка наличия .env файла
if (-not (Test-Path ".env")) {
    Write-Host "❌ Файл .env не найден!" -ForegroundColor Red
    Write-Host "Создайте файл .env с настройками:" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Скопируйте файл env-template.txt в .env и заполните своими значениями:"
    Write-Host "copy env-template.txt .env"
    Write-Host ""
    Write-Host "Или создайте .env файл вручную с настройками:"
    Write-Host "POSTGRES_DB=tbot_db"
    Write-Host "POSTGRES_USER=postgres"
    Write-Host "POSTGRES_PASSWORD=your_secure_password"
    Write-Host "TINKOFF_SANDBOX_TOKEN=your_sandbox_token"
    Write-Host "TINKOFF_PRODUCTION_TOKEN=your_production_token"
    Write-Host "SERVER_PORT=8081"
    Write-Host "DB_URL=jdbc:postgresql://postgres:5432/tbot_db"
    Write-Host "DB_USER=postgres"
    Write-Host "DB_PASSWORD=your_secure_password"
    Write-Host "TINKOFF_DEFAULT_MODE=sandbox"
    exit 1
}

# Проверка Docker
try {
    docker --version | Out-Null
} catch {
    Write-Host "❌ Docker не установлен или не запущен" -ForegroundColor Red
    exit 1
}

try {
    docker-compose --version | Out-Null
} catch {
    Write-Host "❌ Docker Compose не установлен" -ForegroundColor Red
    exit 1
}

Write-Host "✅ Зависимости проверены" -ForegroundColor Green

# Остановка существующих контейнеров
Write-Host "🛑 Остановка существующих контейнеров..." -ForegroundColor Yellow
docker-compose down 2>$null

# Запуск приложения
Write-Host "🏗️  Сборка и запуск приложения..." -ForegroundColor Yellow
docker-compose up -d --build

Write-Host "⏳ Ожидание запуска приложения..." -ForegroundColor Yellow
Start-Sleep -Seconds 30

# Проверка статуса
Write-Host "📊 Статус контейнеров:" -ForegroundColor Cyan
docker-compose ps

Write-Host ""
Write-Host "🎉 Приложение запущено!" -ForegroundColor Green
Write-Host "🌐 Веб-интерфейс: http://localhost:8081" -ForegroundColor Cyan
Write-Host ""
Write-Host "📋 Полезные команды:" -ForegroundColor Yellow
Write-Host "  Логи: docker-compose logs -f"
Write-Host "  Остановка: docker-compose down"
Write-Host "  Перезапуск: docker-compose restart"
