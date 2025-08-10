# Быстрое развертывание на сервере

## 🚀 Быстрый старт (5 минут)

### 1. Подготовка сервера

```bash
# Обновление системы
sudo apt update && sudo apt upgrade -y

# Установка Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
sudo usermod -aG docker $USER

# Установка Docker Compose
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# Перезагрузка (или перелогин)
sudo reboot
```

### 2. Загрузка проекта

```bash
# Клонирование (замените на ваш репозиторий)
git clone <URL_ВАШЕГО_РЕПОЗИТОРИЯ> tbot
cd tbot

# Или загрузка файлов через SCP/SFTP
```

### 3. Настройка

```bash
# Создание .env файла
cat > .env << EOF
POSTGRES_DB=tbot_db
POSTGRES_USER=postgres
POSTGRES_PASSWORD=your_secure_password
TINKOFF_SANDBOX_TOKEN=your_sandbox_token
TINKOFF_PRODUCTION_TOKEN=your_production_token
SERVER_PORT=8081
DB_URL=jdbc:postgresql://postgres:5432/tbot_db
DB_USER=postgres
DB_PASSWORD=your_secure_password
TINKOFF_DEFAULT_MODE=sandbox
EOF

# Сделать скрипты исполняемыми
chmod +x deploy.sh manage.sh
```

### 4. Запуск

```bash
# Автоматическое развертывание
./deploy.sh

# Или ручной запуск
docker-compose -f docker-compose.prod.yml up -d --build
```

### 5. Проверка

```bash
# Статус приложения
./manage.sh status

# Логи
./manage.sh logs app

# Веб-интерфейс
curl http://localhost:8081
```

## 📋 Основные команды управления

```bash
# Запуск
./manage.sh start

# Остановка
./manage.sh stop

# Перезапуск
./manage.sh restart

# Статус
./manage.sh status

# Логи приложения
./manage.sh logs app

# Логи базы данных
./manage.sh logs db

# Резервная копия
./manage.sh backup

# Обновление
./manage.sh update

# Мониторинг ресурсов
./manage.sh monitor
```

## 🔧 Настройка домена и SSL

### Nginx + Let's Encrypt

```bash
# Установка Nginx
sudo apt install nginx certbot python3-certbot-nginx -y

# Конфигурация Nginx
sudo nano /etc/nginx/sites-available/tbot
```

Содержимое конфигурации:
```nginx
server {
    listen 80;
    server_name your-domain.com;
    
    location / {
        proxy_pass http://localhost:8081;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

```bash
# Активация конфигурации
sudo ln -s /etc/nginx/sites-available/tbot /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl restart nginx

# Получение SSL сертификата
sudo certbot --nginx -d your-domain.com
```

## 🔒 Безопасность

### Файрвол

```bash
# Установка UFW
sudo apt install ufw -y

# Настройка правил
sudo ufw allow ssh
sudo ufw allow 80
sudo ufw allow 443
sudo ufw allow 8081  # Только если не используете Nginx

# Включение файрвола
sudo ufw enable
```

### Автоматические обновления

```bash
# Создание cron задачи для обновлений
crontab -e

# Добавить строку для ежедневного обновления в 3:00
0 3 * * * cd /path/to/tbot && ./manage.sh update
```

## 📊 Мониторинг

### Автоматические бэкапы

```bash
# Создание скрипта бэкапа
cat > backup.sh << 'EOF'
#!/bin/bash
cd /path/to/tbot
./manage.sh backup
find . -name "backup_*.sql" -mtime +7 -delete
EOF

chmod +x backup.sh

# Добавление в cron (ежедневно в 2:00)
crontab -e
# 0 2 * * * /path/to/tbot/backup.sh
```

### Логирование

```bash
# Настройка ротации логов
sudo nano /etc/logrotate.d/tbot

# Содержимое:
/path/to/tbot/logs/*.log {
    daily
    missingok
    rotate 7
    compress
    delaycompress
    notifempty
    create 644 root root
}
```

## 🚨 Устранение неполадок

### Приложение не запускается

```bash
# Проверка логов
./manage.sh logs app

# Проверка портов
sudo netstat -tulpn | grep :8081

# Перезапуск
./manage.sh restart
```

### Проблемы с базой данных

```bash
# Проверка статуса PostgreSQL
./manage.sh logs db

# Подключение к базе
docker exec -it tbot_postgres psql -U postgres -d tbot_db
```

### Нехватка ресурсов

```bash
# Мониторинг ресурсов
./manage.sh monitor

# Очистка Docker
./manage.sh cleanup
```

## 📞 Поддержка

При возникновении проблем:

1. **Проверьте логи**: `./manage.sh logs`
2. **Проверьте статус**: `./manage.sh status`
3. **Проверьте ресурсы**: `./manage.sh monitor`
4. **Перезапустите**: `./manage.sh restart`

### Полезные команды

```bash
# Информация о системе
uname -a
docker --version
docker-compose --version
java --version

# Статус сервисов
sudo systemctl status docker
sudo systemctl status nginx

# Использование диска
df -h
docker system df
```

## 🎯 Результат

После выполнения всех шагов:

- ✅ Приложение доступно по адресу: `http://your-server-ip:8081`
- ✅ Веб-интерфейс для управления торговым ботом
- ✅ Автоматические бэкапы базы данных
- ✅ Мониторинг и логирование
- ✅ SSL сертификат (если настроен домен)
- ✅ Автоматические обновления
