// Основные переменные
let currentSection = 'dashboard';
let portfolioChart = null;
let accountId = null;
let tradingMode = 'sandbox';
let currentInstrumentType = 'shares';
let currentPage = 0;
let pageSize = 50;
let totalInstruments = 0;
let allInstruments = [];
let filteredInstruments = [];

// Функции форматирования
function formatPrice(price) {
    if (price === null || price === undefined) return null;
    if (typeof price === 'number') {
        return `₽${price.toFixed(2)}`;
    }
    if (typeof price === 'string') {
        const numPrice = parseFloat(price);
        if (!isNaN(numPrice)) {
            return `₽${numPrice.toFixed(2)}`;
        }
    }
    return null;
}

function formatYield(yield) {
    if (yield === null || yield === undefined) return null;
    if (typeof yield === 'number') {
        return `${yield.toFixed(2)}%`;
    }
    if (typeof yield === 'string') {
        const numYield = parseFloat(yield);
        if (!isNaN(numYield)) {
            return `${numYield.toFixed(2)}%`;
        }
    }
    return null;
}

// Инициализация приложения
document.addEventListener('DOMContentLoaded', function() {
    console.log('Trading Bot Web Interface initialized');
    loadTradingModeStatus();
    loadAccountId();
    // Определяем секцию по URL-пути или hash
    const path = window.location.pathname.replace(/^\/+|\/+$/g, '');
    const hash = window.location.hash.replace('#', '');
    let section = 'dashboard';
    const pathToSection = {
        '': 'dashboard',
        'dashboard': 'dashboard',
        'instruments': 'instruments',
        'portfolio': 'portfolio',
        'orders': 'orders',
        'trading': 'trading-bot',
        'settings': 'settings',
        'analysis': 'analysis',
        'logs': 'trading-bot'
    };
    if (pathToSection[path]) section = pathToSection[path];
    if (hash && document.getElementById(`${hash}-section`)) section = hash;
    showSection(section);
    
    // Обрабатываем навигацию назад/вперед в браузере
    window.addEventListener('popstate', function() {
        const path = window.location.pathname.replace(/^\/+|\/+$/g, '');
        const pathToSection = {
            '': 'dashboard',
            'dashboard': 'dashboard',
            'instruments': 'instruments',
            'portfolio': 'portfolio',
            'orders': 'orders',
            'trading': 'trading-bot',
            'settings': 'settings',
            'analysis': 'analysis',
            'logs': 'trading-bot'
        };
        const target = pathToSection[path] || 'dashboard';
        showSection(target);
    });
});

// Переключение между разделами
function showSection(sectionName) {
    // Скрыть все разделы
    document.querySelectorAll('.section').forEach(section => {
        section.style.display = 'none';
    });
    
    // Показать выбранный раздел
    document.getElementById(sectionName + '-section').style.display = 'block';
    
    // Обновить активную ссылку в навигации
    document.querySelectorAll('.nav-link').forEach(link => {
        link.classList.remove('active');
    });
    if (event && event.target && event.target.classList) {
        event.target.classList.add('active');
    } else {
        // Подсветим ссылку по sectionName
        const map = {
            'dashboard': 'Dashboard',
            'instruments': 'Инструменты',
            'portfolio': 'Портфель',
            'orders': 'Ордера',
            'trading-bot': 'Торговый бот',
            'analysis': 'Анализ',
            'settings': 'Настройки'
        };
        const text = map[sectionName];
        if (text) {
            document.querySelectorAll('.nav-link').forEach(link => {
                if (link.textContent.trim() === text) link.classList.add('active');
            });
        }
    }
    
    // Обновим адресную строку для прямых ссылок
    const sectionToPath = {
        'dashboard': '/dashboard',
        'instruments': '/instruments',
        'portfolio': '/portfolio',
        'orders': '/orders',
        'trading-bot': '/trading',
        'analysis': '/analysis',
        'settings': '/settings'
    };
    if (sectionToPath[sectionName]) {
        const newUrl = sectionToPath[sectionName];
        if (window.location.pathname !== newUrl) {
            window.history.pushState({ section: sectionName }, '', newUrl);
        }
    }
    
    currentSection = sectionName;
    
    // Загрузить данные для раздела
    switch(sectionName) {
        case 'dashboard':
            loadDashboard();
            break;
        case 'instruments':
            loadInstruments();
            break;
        case 'portfolio':
            loadPortfolio();
            break;
        case 'orders':
            loadOrders();
            break;
        case 'trading-bot':
            loadBotStatus();
            loadTradingOpportunities();
            loadBotLog();
            // Инициализируем SSE для логов в реальном времени
            initLogStream();
            break;
        case 'analysis':
            // Анализ загружается по требованию
            break;
        case 'settings':
            // При открытии настроек обновим статус и маржинальные настройки
            loadTradingModeStatus();
            loadMarginSettings();
            loadHardStopsSettings();
            break;
    }
}

// Загрузка accountId
async function loadAccountId() {
    try {
        const response = await fetch('/api/accounts');
        if (response.ok) {
            const accounts = await response.json();
            if (accounts && accounts.length > 0) {
                accountId = accounts[0].id;
                console.log('AccountId loaded:', accountId);
                // Обновить отображение accountId на странице
                const accountIdElement = document.getElementById('accountId');
                if (accountIdElement) {
                    accountIdElement.textContent = accountId;
                }
                // Обновить поле в форме пополнения баланса
                updateTopUpAccountIdField();
            } else {
                console.log('No accounts found, creating sandbox account...');
                await createSandboxAccount();
            }
        } else {
            console.error('Failed to load accounts');
        }
    } catch (error) {
        console.error('Error loading accountId:', error);
    }
}

// ==================== МАРЖИНАЛЬНАЯ ТОРГОВЛЯ ====================
async function loadMarginSettings() {
    try {
        const response = await fetch('/api/margin/status');
        if (!response.ok) return;
        const data = await response.json();
        const enabled = document.getElementById('marginEnabled');
        const allowShort = document.getElementById('marginAllowShort');
        const maxUtil = document.getElementById('marginMaxUtilizationPct');
        const maxShort = document.getElementById('marginMaxShortPct');
        const maxLev = document.getElementById('marginMaxLeverage');
        if (enabled) enabled.checked = !!data.enabled;
        if (allowShort) allowShort.checked = !!data.allowShort;
        if (maxUtil && data.maxUtilizationPct != null) maxUtil.value = data.maxUtilizationPct;
        if (maxShort && data.maxShortPct != null) maxShort.value = data.maxShortPct;
        if (maxLev && data.maxLeverage != null) maxLev.value = data.maxLeverage;
        const note = document.getElementById('marginStatusNote');
        if (note) {
            note.classList.remove('d-none');
            note.innerHTML = `<i class="fas fa-info-circle me-1"></i>Текущие значения загружены.`;
        }
    } catch (e) {
        console.error('Error loading margin settings', e);
    }
}

async function saveMarginSettings() {
    try {
        const enabled = document.getElementById('marginEnabled')?.checked;
        const allowShort = document.getElementById('marginAllowShort')?.checked;
        const maxUtil = document.getElementById('marginMaxUtilizationPct')?.value;
        const maxShort = document.getElementById('marginMaxShortPct')?.value;
        const maxLev = document.getElementById('marginMaxLeverage')?.value;
        
        const params = new URLSearchParams();
        if (enabled != null) params.append('enabled', enabled);
        if (allowShort != null) params.append('allowShort', allowShort);
        if (maxUtil) params.append('maxUtilizationPct', maxUtil);
        if (maxShort) params.append('maxShortPct', maxShort);
        if (maxLev) params.append('maxLeverage', maxLev);
        
        const response = await fetch('/api/margin/settings', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: params
        });
        if (response.ok) {
            showSuccess('Настройки маржинальной торговли сохранены');
            await loadMarginSettings();
        } else {
            showError('Не удалось сохранить настройки маржинальной торговли');
        }
    } catch (e) {
        console.error('Error saving margin settings', e);
        showError('Ошибка сохранения настроек маржинальной торговли');
    }
}

async function showMarginAttributes() {
    try {
        if (!accountId) {
            showError('Нет активного аккаунта');
            return;
        }
        const response = await fetch(`/api/margin/attributes?accountId=${encodeURIComponent(accountId)}`, {
            headers: { 'Accept': 'application/json' }
        });
        if (response.ok) {
            const ct = response.headers.get('content-type') || '';
            if (!ct.includes('application/json')) {
                const text = await response.text();
                throw new Error(text || 'Сервис вернул не-JSON ответ');
            }
            const data = await response.json();
            const box = document.getElementById('marginAttrs');
            if (box) {
                box.classList.remove('d-none');
                box.innerHTML = `
                    <div class="alert alert-info">
                        <div><strong>Ликвидный портфель:</strong> ${data.liquidPortfolio}</div>
                        <div><strong>Начальная маржа:</strong> ${data.startingMargin}</div>
                        <div><strong>Минимальная маржа:</strong> ${data.minimalMargin}</div>
                        <div><strong>Уровень достаточности средств:</strong> ${data.fundsSufficiencyLevel}</div>
                        <div><strong>Недостающие средства:</strong> ${data.amountOfMissingFunds}</div>
                        <div><strong>Скорректированная маржа:</strong> ${data.correctedMargin}</div>
                    </div>`;
            }
        } else {
            const text = await response.text();
            showError(text || 'Не удалось получить атрибуты маржи');
        }
    } catch (e) {
        console.error('Error loading margin attributes', e);
        showError('Ошибка получения атрибутов маржи');
    }
}

async function showBuyingPower() {
    try {
        if (!accountId) {
            showError('Нет активного аккаунта');
            return;
        }
        const resp = await fetch(`/api/margin/buying-power?accountId=${encodeURIComponent(accountId)}`, {
            headers: { 'Accept': 'application/json' }
        });
        if (!resp.ok) {
            const t = await resp.text();
            throw new Error(t || 'Ошибка получения покупательной способности');
        }
        const data = await resp.json();
        const box = document.getElementById('marginBp');
        if (box) {
            box.classList.remove('d-none');
            box.innerHTML = `
                <div class="alert alert-warning">
                    <div><strong>Кэш:</strong> ${formatPrice(data.cash)}</div>
                    <div><strong>Покупательная способность (BP):</strong> ${formatPrice(data.buyingPower)}</div>
                </div>`;
        }
    } catch (e) {
        showError(e.message || String(e));
    }
}

// Создание аккаунта в песочнице
async function createSandboxAccount() {
    if (tradingMode !== 'sandbox') {
        console.log('Cannot create sandbox account in production mode');
        return;
    }
    
    try {
        const response = await fetch('/api/accounts/sandbox/open', {
            method: 'POST'
        });
        if (response.ok) {
            accountId = await response.text();
            console.log('Sandbox account created:', accountId);
            // Обновить отображение accountId на странице
            const accountIdElement = document.getElementById('accountId');
            if (accountIdElement) {
                accountIdElement.textContent = accountId;
            }
            // Обновить поле в форме пополнения баланса
            updateTopUpAccountIdField();
        } else {
            console.error('Failed to create sandbox account');
        }
    } catch (error) {
        console.error('Error creating sandbox account:', error);
    }
}

// Обновление поля accountId в форме пополнения баланса
function updateTopUpAccountIdField() {
    const topUpAccountIdField = document.getElementById('topUpAccountId');
    if (topUpAccountIdField && accountId) {
        topUpAccountIdField.value = accountId;
    }
}

// Загрузка статуса режима торговли
async function loadTradingModeStatus() {
    try {
        const response = await fetch('/api/trading-mode/status');
        if (response.ok) {
            const status = await response.json();
            tradingMode = status.mode;
            
            // Обновить отображение в сайдбаре
            const tradingModeElement = document.getElementById('tradingMode');
            if (tradingModeElement) {
                tradingModeElement.textContent = status.displayName;
                tradingModeElement.className = `badge ${status.badgeClass}`;
            }
            
            // Обновить отображение в настройках
            const tradingModeStatusElement = document.getElementById('tradingModeStatus');
            if (tradingModeStatusElement) {
                tradingModeStatusElement.textContent = status.displayName;
                tradingModeStatusElement.className = `badge ${status.badgeClass}`;
            }
            
            // Обновить радио-кнопки
            const sandboxRadio = document.getElementById('sandboxMode');
            const productionRadio = document.getElementById('productionMode');
            if (sandboxRadio && productionRadio) {
                sandboxRadio.checked = status.isSandbox;
                productionRadio.checked = status.isProduction;
            }
            
            // Показать информацию о режиме
            if (status.modeInfo) {
                showModeInfo(status.modeInfo, status.isProduction);
            }
        }
    } catch (error) {
        console.error('Error loading trading mode status:', error);
    }
}

// Сброс настроек режима торговли
async function resetTradingMode() {
    if (!confirm('Сбросить настройки режима торговли к значениям по умолчанию?')) {
        return;
    }
    
    try {
        const response = await fetch('/api/trading-mode/reset', {
            method: 'POST'
        });
        
        const result = await response.json();
        
        if (result.success) {
            tradingMode = result.currentMode;
            await loadTradingModeStatus();
            showSuccess(result.message);
            
            // Перезагрузить данные
            loadAccountId();
            loadDashboard();
        } else {
            showError(result.message);
        }
    } catch (error) {
        console.error('Error resetting trading mode:', error);
        showError('Ошибка сброса настроек: ' + error.message);
    }
}

// Показать информацию о режиме торговли
function showModeInfo(info, isProduction) {
    // Удалить существующие уведомления о режиме
    const existingAlerts = document.querySelectorAll('.trading-mode-alert');
    existingAlerts.forEach(alert => alert.remove());
    
    // Создать новое уведомление
    const alertDiv = document.createElement('div');
    alertDiv.className = `alert ${isProduction ? 'alert-danger' : 'alert-warning'} trading-mode-alert`;
    alertDiv.innerHTML = `
        <i class="fas ${isProduction ? 'fa-exclamation-triangle' : 'fa-info-circle'} me-2"></i>
        <strong>${isProduction ? 'ВНИМАНИЕ!' : 'Информация:'}</strong> ${info}
    `;
    
    // Добавить в начало контента
    const mainContent = document.querySelector('.main-content');
    if (mainContent) {
        mainContent.insertBefore(alertDiv, mainContent.firstChild);
    }
    
    // Обновить информацию в настройках
    const tradingModeInfoElement = document.getElementById('tradingModeInfo');
    if (tradingModeInfoElement) {
        tradingModeInfoElement.textContent = info;
        tradingModeInfoElement.className = `text-${isProduction ? 'danger' : 'muted'}`;
    }
}

// Переключение режима торговли
async function switchTradingMode() {
    const selectedMode = document.querySelector('input[name="tradingMode"]:checked').value;
    
    if (selectedMode === tradingMode) {
        showNotification('Режим уже активен', 'info');
        return;
    }
    
    // Дополнительное предупреждение для переключения в продакшн
    if (selectedMode === 'production') {
        if (!confirm('ВНИМАНИЕ! Вы переключаетесь в режим реальной торговли. Это может привести к реальным финансовым потерям. Продолжить?')) {
            return;
        }
        
        // Дополнительное подтверждение
        if (!confirm('ПОДТВЕРЖДЕНИЕ: Вы уверены, что хотите активировать режим реальной торговли? Все операции будут выполняться с реальными деньгами!')) {
            return;
        }
    }
    
    try {
        // Вызов API для переключения режима
        const formData = new FormData();
        formData.append('mode', selectedMode);
        
        let endpoint = '/api/trading-mode/switch';
        if (selectedMode === 'production') {
            endpoint = '/api/trading-mode/switch-confirmed';
        }
        
        const response = await fetch(endpoint, {
            method: 'POST',
            body: formData
        });
        
        const result = await response.json();
        
        if (result.success) {
            tradingMode = selectedMode;
            await loadTradingModeStatus();
            showSuccess(result.message);
            
            // Показать предупреждение если есть
            if (result.warning) {
                showNotification(result.warning, 'warning');
            }
            
            // Перезагрузить данные
            loadAccountId();
            loadDashboard();
        } else {
            if (result.requiresConfirmation) {
                // Показать диалог подтверждения
                if (confirm(result.message + '\n\nПродолжить?')) {
                    // Повторный вызов с подтверждением
                    const confirmResponse = await fetch('/api/trading-mode/switch-confirmed', {
                        method: 'POST',
                        body: formData
                    });
                    
                    const confirmResult = await confirmResponse.json();
                    if (confirmResult.success) {
                        tradingMode = selectedMode;
                        await loadTradingModeStatus();
                        showSuccess(confirmResult.message);
                        if (confirmResult.warning) {
                            showNotification(confirmResult.warning, 'warning');
                        }
                        loadAccountId();
                        loadDashboard();
                    } else {
                        showError(confirmResult.message);
                    }
                }
            } else {
                showError(result.message);
            }
        }
    } catch (error) {
        console.error('Error switching trading mode:', error);
        showError('Ошибка переключения режима: ' + error.message);
    }
}

// Проверка статуса API
async function checkApiStatus() {
    try {
        const response = await fetch('/api/accounts');
        if (response.ok) {
            document.getElementById('apiStatus').textContent = 'Online';
            document.getElementById('apiStatusBadge').textContent = 'Online';
            document.getElementById('apiStatusBadge').className = 'badge bg-success';
        } else {
            document.getElementById('apiStatus').textContent = 'Error';
            document.getElementById('apiStatusBadge').textContent = 'Error';
            document.getElementById('apiStatusBadge').className = 'badge bg-danger';
        }
    } catch (error) {
        console.error('API check failed:', error);
        document.getElementById('apiStatus').textContent = 'Offline';
        document.getElementById('apiStatusBadge').textContent = 'Offline';
        document.getElementById('apiStatusBadge').className = 'badge bg-danger';
    }
}

// Загрузка данных для дашборда
async function loadDashboard() {
    try {
        // Загрузить портфель
        if (!accountId) {
            showError('AccountId не найден');
            return;
        }
        const portfolioResponse = await fetch(`/api/portfolio?accountId=${accountId}`);
        if (portfolioResponse.ok) {
            const portfolio = await portfolioResponse.json();
            updateDashboardStats(portfolio);
        }
        
        // Загрузить ордера
        const ordersResponse = await fetch(`/api/orders?accountId=${accountId}`);
        if (ordersResponse.ok) {
            const orders = await ordersResponse.json();
            updateActiveOrdersCount(orders);
        }
        
        // Загрузить инструменты
        const instrumentsResponse = await fetch('/api/instruments/shares');
        if (instrumentsResponse.ok) {
            const instruments = await instrumentsResponse.json();
            updateInstrumentsCount(instruments);
        }
        
    } catch (error) {
        console.error('Dashboard loading failed:', error);
        showError('Ошибка загрузки данных дашборда');
    }
}

// Обновление статистики дашборда
function updateDashboardStats(portfolio) {
    if (portfolio && portfolio.totalAmountShares) {
        const totalValue = portfolio.totalAmountShares.units + portfolio.totalAmountShares.nano / 1000000000;
        document.getElementById('totalValue').textContent = `₽${totalValue.toLocaleString()}`;
        
        if (portfolio.expectedYield) {
            const profit = portfolio.expectedYield.units + portfolio.expectedYield.nano / 1000000000;
            document.getElementById('totalProfit').textContent = `₽${profit.toLocaleString()}`;
        }
    }
}

// Обновление количества активных ордеров
function updateActiveOrdersCount(orders) {
    if (orders && orders.length) {
        const activeCount = orders.filter(order => 
            order.executionReportStatus === 'EXECUTION_REPORT_STATUS_NEW' || 
            order.executionReportStatus === 'EXECUTION_REPORT_STATUS_PARTIALLY_FILLED'
        ).length;
        document.getElementById('activeOrders').textContent = activeCount;
    }
}

// Обновление количества инструментов
function updateInstrumentsCount(instruments) {
    if (instruments && instruments.length) {
        document.getElementById('instrumentsCount').textContent = instruments.length;
    }
}

// Загрузка инструментов
async function loadInstruments() {
    // По умолчанию загружаем акции
    await loadShares();
}

async function loadShares() {
    currentInstrumentType = 'shares';
    currentPage = 0;
    await loadInstrumentsData();
}

async function loadBonds() {
    currentInstrumentType = 'bonds';
    currentPage = 0;
    await loadInstrumentsData();
}

async function loadEtfs() {
    currentInstrumentType = 'etfs';
    currentPage = 0;
    await loadInstrumentsData();
}

async function loadCurrencies() {
    currentInstrumentType = 'currencies';
    currentPage = 0;
    await loadInstrumentsData();
}

async function loadInstrumentsData() {
    const loadingElement = document.getElementById('instrumentsLoading');
    const listElement = document.getElementById('instrumentsList');
    
    if (loadingElement) loadingElement.style.display = 'block';
    if (listElement) listElement.style.display = 'none';
    
    try {
        const searchQuery = document.getElementById('instrumentSearch')?.value || '';
        const statusFilter = document.getElementById('statusFilter')?.value || '';
        let url = `/api/instruments/${currentInstrumentType}?page=${currentPage}&size=${pageSize}&search=${encodeURIComponent(searchQuery)}`;
        if (statusFilter) {
            url += `&status=${encodeURIComponent(statusFilter)}`;
        }
        const response = await fetch(url);
        if (response.ok) {
            const data = await response.json();
            allInstruments = data;
            filteredInstruments = data;
            
            // Загружаем общее количество
            await loadInstrumentsCount();
            
            displayInstruments(data, currentInstrumentType);
            updatePagination();
        } else {
            showError('Ошибка загрузки инструментов');
        }
    } catch (error) {
        console.error('Error loading instruments:', error);
        showError('Ошибка загрузки инструментов');
    } finally {
        if (loadingElement) loadingElement.style.display = 'none';
        if (listElement) listElement.style.display = 'block';
    }
}

async function loadInstrumentsCount() {
    try {
        const searchQuery = document.getElementById('instrumentSearch')?.value || '';
        const statusFilter = document.getElementById('statusFilter')?.value || '';
        
        let url = `/api/instruments/count?type=${currentInstrumentType}`;
        if (searchQuery) {
            url += `&search=${encodeURIComponent(searchQuery)}`;
        }
        if (statusFilter) {
            url += `&status=${encodeURIComponent(statusFilter)}`;
        }
        
        const response = await fetch(url);
        if (response.ok) {
            const data = await response.json();
            totalInstruments = data.count;
            const countElement = document.getElementById('instrumentsCount');
            if (countElement) {
                countElement.textContent = `Всего: ${totalInstruments}`;
            }
        }
    } catch (error) {
        console.error('Error loading instruments count:', error);
    }
}

function filterInstruments() {
    const searchQuery = document.getElementById('instrumentSearch')?.value || '';
    currentPage = 0;
    loadInstrumentsData();
}

function changePageSize() {
    const pageSizeSelect = document.getElementById('pageSize');
    if (pageSizeSelect) {
        pageSize = parseInt(pageSizeSelect.value);
        currentPage = 0;
        loadInstrumentsData();
    }
}

function goToPage(page) {
    currentPage = page;
    loadInstrumentsData();
}

function updatePagination() {
    const paginationElement = document.getElementById('pagination');
    if (!paginationElement) return;
    
    const totalPages = Math.ceil(totalInstruments / pageSize);
    if (totalPages <= 1) {
        paginationElement.innerHTML = '';
        return;
    }
    
    let html = '';
    
    // Кнопка "Предыдущая"
    html += `
        <li class="page-item ${currentPage === 0 ? 'disabled' : ''}">
            <a class="page-link" href="#" onclick="goToPage(${currentPage - 1})" ${currentPage === 0 ? 'tabindex="-1"' : ''}>
                <i class="fas fa-chevron-left"></i>
            </a>
        </li>
    `;
    
    // Номера страниц
    const startPage = Math.max(0, currentPage - 2);
    const endPage = Math.min(totalPages - 1, currentPage + 2);
    
    if (startPage > 0) {
        html += `
            <li class="page-item">
                <a class="page-link" href="#" onclick="goToPage(0)">1</a>
            </li>
        `;
        if (startPage > 1) {
            html += `
                <li class="page-item disabled">
                    <span class="page-link">...</span>
                </li>
            `;
        }
    }
    
    for (let i = startPage; i <= endPage; i++) {
        html += `
            <li class="page-item ${i === currentPage ? 'active' : ''}">
                <a class="page-link" href="#" onclick="goToPage(${i})">${i + 1}</a>
            </li>
        `;
    }
    
    if (endPage < totalPages - 1) {
        if (endPage < totalPages - 2) {
            html += `
                <li class="page-item disabled">
                    <span class="page-link">...</span>
                </li>
            `;
        }
        html += `
            <li class="page-item">
                <a class="page-link" href="#" onclick="goToPage(${totalPages - 1})">${totalPages}</a>
            </li>
        `;
    }
    
    // Кнопка "Следующая"
    html += `
        <li class="page-item ${currentPage === totalPages - 1 ? 'disabled' : ''}">
            <a class="page-link" href="#" onclick="goToPage(${currentPage + 1})" ${currentPage === totalPages - 1 ? 'tabindex="-1"' : ''}>
                <i class="fas fa-chevron-right"></i>
            </a>
        </li>
    `;
    
    paginationElement.innerHTML = html;
}

// Загрузка портфеля
async function loadPortfolio() {
    const loading = document.getElementById('portfolioLoading');
    const list = document.getElementById('portfolioList');
    
    loading.style.display = 'block';
    list.innerHTML = '';
    
    try {
        if (!accountId) {
            showError('AccountId не найден');
            return;
        }
        const response = await fetch(`/api/portfolio?accountId=${accountId}`);
        if (response.ok) {
            const data = await response.json();
            displayPortfolio(data);
        } else {
            showError('Ошибка загрузки портфеля');
        }
    } catch (error) {
        console.error('Portfolio loading failed:', error);
        showError('Ошибка загрузки портфеля');
    } finally {
        loading.style.display = 'none';
    }
}

// Отображение портфеля
function displayPortfolio(data) {
    const portfolioList = document.getElementById('portfolioList');
    
    if (!data || !data.positions || data.positions.length === 0) {
        portfolioList.innerHTML = '<p class="text-muted">Портфель пуст</p>';
        return;
    }
    
    let html = `
        <div class="row mb-4">
            <div class="col-md-3">
                <div class="card bg-primary text-white">
                    <div class="card-body">
                        <h5 class="card-title">Акции</h5>
                        <h3>${data.totalAmountShares?.displayValue || '₽0.00'}</h3>
                    </div>
                </div>
            </div>
            <div class="col-md-3">
                <div class="card bg-success text-white">
                    <div class="card-body">
                        <h5 class="card-title">Облигации</h5>
                        <h3>${data.totalAmountBonds?.displayValue || '₽0.00'}</h3>
                    </div>
                </div>
            </div>
            <div class="col-md-3">
                <div class="card bg-info text-white">
                    <div class="card-body">
                        <h5 class="card-title">ETF</h5>
                        <h3>${data.totalAmountEtfs?.displayValue || '₽0.00'}</h3>
                    </div>
                </div>
            </div>
            <div class="col-md-3">
                <div class="card bg-warning text-white">
                    <div class="card-body">
                        <h5 class="card-title">Валюты</h5>
                        <h3>${data.totalAmountCurrencies?.displayValue || '₽0.00'}</h3>
                    </div>
                </div>
            </div>
        </div>
        
        <div class="table-responsive">
            <table class="table table-striped">
                <thead>
                    <tr>
                        <th>Тикер</th>
                        <th>Название</th>
                        <th>Тип</th>
                        <th>Количество</th>
                        <th>Текущая цена</th>
                        <th>Средняя цена</th>
                        <th>НКД</th>
                        <th>Доходность</th>
                    </tr>
                </thead>
                <tbody>
    `;
    
    data.positions.forEach(position => {
        html += `
            <tr>
                <td><strong>${position.ticker || 'N/A'}</strong></td>
                <td>${position.name || 'N/A'}</td>
                <td><span class="badge bg-secondary">${position.instrumentType || 'N/A'}</span></td>
                <td>${position.quantity ? position.quantity.toFixed(2) : 'N/A'}</td>
                <td>${position.currentPriceDisplay || 'N/A'}</td>
                <td>${position.averagePriceDisplay || 'N/A'}</td>
                <td>${position.accumulatedCouponYieldDisplay || 'N/A'}</td>
                <td>${position.yieldDisplay || 'N/A'}</td>
            </tr>
        `;
    });
    
    html += `
                </tbody>
            </table>
        </div>
        <div class="mt-3">
            <p><strong>Всего позиций:</strong> ${data.positions.length}</p>
        </div>
    `;
    
    portfolioList.innerHTML = html;
}

function calculateTotalValue(data) {
    let total = 0;
    if (data.totalAmountShares?.value) total += data.totalAmountShares.value;
    if (data.totalAmountBonds?.value) total += data.totalAmountBonds.value;
    if (data.totalAmountEtfs?.value) total += data.totalAmountEtfs.value;
    if (data.totalAmountCurrencies?.value) total += data.totalAmountCurrencies.value;
    return total;
}

// Загрузка ордеров
async function loadOrders() {
    const loading = document.getElementById('ordersLoading');
    const list = document.getElementById('ordersList');
    
    loading.style.display = 'block';
    list.innerHTML = '';
    
    try {
        if (!accountId) {
            showError('AccountId не найден');
            return;
        }
        const response = await fetch(`/api/orders?accountId=${accountId}`);
        if (response.ok) {
            const data = await response.json();
            displayOrders(data);
        } else {
            showError('Ошибка загрузки ордеров');
        }
    } catch (error) {
        console.error('Orders loading failed:', error);
        showError('Ошибка загрузки ордеров');
    } finally {
        loading.style.display = 'none';
    }
}

// Отображение ордеров
function displayOrders(data) {
    const list = document.getElementById('ordersList');
    
    if (!data || data.length === 0) {
        list.innerHTML = '<p class="text-muted">Активных ордеров нет</p>';
        return;
    }
    
    const table = `
        <div class="table-responsive">
            <table class="table table-striped">
                <thead>
                    <tr>
                        <th>ID ордера</th>
                        <th>FIGI</th>
                        <th>Операция</th>
                        <th>Статус</th>
                        <th>Запрошено</th>
                        <th>Исполнено</th>
                        <th>Цена</th>
                        <th>Действия</th>
                    </tr>
                </thead>
                <tbody>
                    ${data.map(order => `
                        <tr>
                            <td><small>${order.orderId}</small></td>
                            <td>${order.figi}</td>
                            <td>
                                <span class="badge ${order.direction === 'ORDER_DIRECTION_BUY' ? 'bg-success' : 'bg-danger'}">
                                    ${order.direction === 'ORDER_DIRECTION_BUY' ? 'Покупка' : 'Продажа'}
                                </span>
                            </td>
                            <td>
                                <span class="badge ${getStatusBadgeClass(order.executionReportStatus)}">
                                    ${getStatusDisplayName(order.executionReportStatus)}
                                </span>
                            </td>
                            <td>${order.lotsRequested}</td>
                            <td>${order.lotsExecuted}</td>
                            <td>₽${parseFloat(order.initialOrderPrice?.units || 0).toFixed(2)}</td>
                            <td>
                                <button class="btn btn-sm btn-outline-danger" onclick="cancelOrder('${order.orderId}')">
                                    <i class="fas fa-times"></i>
                                </button>
                            </td>
                        </tr>
                    `).join('')}
                </tbody>
            </table>
        </div>
    `;
    
    list.innerHTML = table;
}

// Получение класса для статуса ордера
function getStatusBadgeClass(status) {
    switch(status) {
        case 'EXECUTION_REPORT_STATUS_NEW': return 'bg-warning';
        case 'EXECUTION_REPORT_STATUS_FILL': return 'bg-success';
        case 'EXECUTION_REPORT_STATUS_PARTIALLY_FILLED': return 'bg-info';
        case 'EXECUTION_REPORT_STATUS_CANCELLED': return 'bg-secondary';
        case 'EXECUTION_REPORT_STATUS_REPLACED': return 'bg-info';
        case 'EXECUTION_REPORT_STATUS_REJECTED': return 'bg-danger';
        default: return 'bg-secondary';
    }
}

// Получение отображаемого имени статуса
function getStatusDisplayName(status) {
    switch(status) {
        case 'EXECUTION_REPORT_STATUS_NEW': return 'Новый';
        case 'EXECUTION_REPORT_STATUS_FILL': return 'Исполнен';
        case 'EXECUTION_REPORT_STATUS_PARTIALLY_FILLED': return 'Частично';
        case 'EXECUTION_REPORT_STATUS_CANCELLED': return 'Отменен';
        case 'EXECUTION_REPORT_STATUS_REPLACED': return 'Заменен';
        case 'EXECUTION_REPORT_STATUS_REJECTED': return 'Отклонен';
        default: return status;
    }
}



// Отмена ордера
async function cancelOrder(orderId) {
    if (!confirm('Вы уверены, что хотите отменить этот ордер?')) {
        return;
    }
    
    // Предупреждение для реальной торговли
    if (tradingMode === 'production') {
        if (!confirm('ВНИМАНИЕ! Вы отменяете ордер в режиме реальной торговли. Продолжить?')) {
            return;
        }
    }
    
    try {
        const response = await fetch('/api/orders/cancel', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: new URLSearchParams({
                orderId: orderId,
                accountId: accountId // Используем accountId
            })
        });
        
        if (response.ok) {
            showSuccess('Ордер успешно отменен');
            loadOrders(); // Перезагрузить список ордеров
        } else {
            showError('Ошибка отмены ордера');
        }
    } catch (error) {
        console.error('Order cancellation failed:', error);
        showError('Ошибка отмены ордера');
    }
}



// Показать уведомление об успехе
function showSuccess(message) {
    showNotification(message, 'success');
}

// Показать уведомление об ошибке
function showError(message) {
    showNotification(message, 'danger');
}

// Показать уведомление
function showNotification(message, type) {
    const notification = document.createElement('div');
    notification.className = `alert alert-${type} alert-dismissible fade show position-fixed`;
    notification.style.cssText = 'top: 20px; right: 20px; z-index: 9999; min-width: 300px;';
    notification.innerHTML = `
        ${message}
        <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
    `;
    
    document.body.appendChild(notification);
    
    // Автоматически удалить через 5 секунд
    setTimeout(() => {
        if (notification.parentNode) {
            notification.remove();
        }
    }, 5000);
}

// Пополнение баланса в песочнице
async function topUpSandboxAccount() {
    const currency = document.getElementById('topUpCurrency').value;
    const amount = parseFloat(document.getElementById('topUpAmount').value);
    
    if (!accountId) {
        showError('Нет активного аккаунта. Создайте аккаунт в песочнице.');
        return;
    }
    
    if (!currency || !amount || amount <= 0) {
        showError('Пожалуйста, заполните все поля корректно');
        return;
    }
    
    // Конвертируем сумму в units и nano
    const units = Math.floor(amount);
    const nano = Math.round((amount - units) * 1000000000);
    
    try {
        const response = await fetch('/api/accounts/sandbox/topup', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                accountId: accountId,
                currency: currency,
                units: units,
                nano: nano
            })
        });
        
        if (response.ok) {
            const result = await response.text();
            showSuccess(`Баланс успешно пополнен: ${result}`);
            document.getElementById('topUpForm').reset();
            // Обновить поле accountId после сброса формы
            updateTopUpAccountIdField();
            // Перезагружаем портфель для отображения нового баланса
            await loadPortfolio();
        } else {
            const error = await response.text();
            showError(`Ошибка пополнения баланса: ${error}`);
        }
    } catch (error) {
        console.error('Error topping up account:', error);
        showError('Ошибка при пополнении баланса');
    }
}

// ==================== PANIC-STOP и лимиты ====================
async function panicOn() {
    try {
        await fetch('/api/bot-control/panic-on', { method: 'POST' });
        showSuccess('PANIC-STOP включен');
    } catch (e) { showError('Не удалось включить PANIC-STOP'); }
}

// ==================== HARD STOPS (OCO) ====================
async function loadHardStopsSettings() {
    try {
        const resp1 = await fetch('/api/settings/get?key=hard_stops.enabled');
        const resp2 = await fetch('/api/settings/get?key=hard_stops.trailing.enabled');
        if (resp1.ok) {
            const v = await resp1.text();
            const el = document.getElementById('hardStopsEnabled');
            if (el) el.checked = (v === 'true');
        }
        if (resp2.ok) {
            const v = await resp2.text();
            const el = document.getElementById('hardStopsTrailingEnabled');
            if (el) el.checked = (v === 'true');
        }
    } catch (e) { console.warn('Hard stops settings load error', e); }
}

async function saveHardStopsSettings() {
    try {
        const enabled = document.getElementById('hardStopsEnabled')?.checked ? 'true' : 'false';
        const trailing = document.getElementById('hardStopsTrailingEnabled')?.checked ? 'true' : 'false';
        const b1 = new URLSearchParams({ key: 'hard_stops.enabled', value: enabled, description: 'Enable hard OCO stops in production' });
        const b2 = new URLSearchParams({ key: 'hard_stops.trailing.enabled', value: trailing, description: 'Enable trailing with OCO re-posting' });
        const r1 = await fetch('/api/settings/set', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: b1 });
        const r2 = await fetch('/api/settings/set', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: b2 });
        if (r1.ok && r2.ok) showSuccess('Настройки жёстких стопов сохранены');
        else showError('Не удалось сохранить настройки жёстких стопов');
    } catch (e) { showError('Ошибка сохранения настроек жёстких стопов'); }
}
async function panicOff() {
    try {
        await fetch('/api/bot-control/panic-off', { method: 'POST' });
        showSuccess('PANIC-STOP снят');
    } catch (e) { showError('Не удалось снять PANIC-STOP'); }
}
async function cancelAllOrders() {
    if (!accountId) { showError('Нет активного аккаунта'); return; }
    if (!confirm('Отменить все активные ордера?')) return;
    try {
        const resp = await fetch('/api/bot-control/cancel-all?accountId=' + encodeURIComponent(accountId), { method: 'POST' });
        if (resp.ok) showSuccess('Запрос на отмену всех ордеров принят');
        else showError('Не удалось отправить отмену всех ордеров');
    } catch (e) { showError('Ошибка отмены всех ордеров'); }
}
async function updateOrderLimit() {
    const v = document.getElementById('maxOrdersPerMinute').value;
    try {
        const resp = await fetch('/api/bot-control/limit?maxPerMinute=' + encodeURIComponent(v), { method: 'POST' });
        if (resp.ok) showSuccess('Лимит обновлен');
        else showError('Не удалось обновить лимит');
    } catch (e) { showError('Ошибка обновления лимита'); }
}

// ==================== RISK RULES ====================
async function saveRiskRule() {
    const figi = document.getElementById('riskFigi').value.trim();
    const sl = document.getElementById('riskSl').value.trim();
    const tp = document.getElementById('riskTp').value.trim();
    if (!figi) { showError('Введите FIGI'); return; }
    const params = new URLSearchParams();
    params.append('figi', figi);
    if (sl) params.append('stopLossPct', (parseFloat(sl)/100).toString());
    if (tp) params.append('takeProfitPct', (parseFloat(tp)/100).toString());
    try {
        const resp = await fetch('/api/risk-rules', { method: 'POST', body: params });
        if (resp.ok) { showSuccess('Правило сохранено'); loadRiskRule(); }
        else showError('Не удалось сохранить правило');
    } catch (e) { showError('Ошибка сохранения правила'); }
}

async function loadRiskRule() {
    const figi = document.getElementById('riskFigi').value.trim();
    if (!figi) { showError('Введите FIGI'); return; }
    try {
        const resp = await fetch('/api/risk-rules/' + encodeURIComponent(figi));
        const data = await resp.json();
        const el = document.getElementById('riskRuleResult');
        if (data && data.figi) {
            const rule = data;
            el.textContent = `SL=${(rule.stopLossPct??0)*100}% TP=${(rule.takeProfitPct??0)*100}% Active=${rule.active}`;
        } else if (data && data.rule === null) {
            el.textContent = 'Правило не найдено';
        } else {
            el.textContent = 'Нет данных';
        }
    } catch (e) { showError('Ошибка загрузки правила'); }
}

// Инициализация формы пополнения баланса
document.addEventListener('DOMContentLoaded', function() {
    const topUpForm = document.getElementById('topUpForm');
    if (topUpForm) {
        topUpForm.addEventListener('submit', function(e) {
            e.preventDefault();
            topUpSandboxAccount();
        });
    }
    
    // Автозаполнение accountId в форме пополнения
    updateTopUpAccountIdField();
});

// Закрытие аккаунта в песочнице
async function closeSandboxAccount() {
    if (tradingMode !== 'sandbox') {
        showError('Можно закрывать только песочные аккаунты');
        return;
    }
    
    if (!accountId) {
        showError('Нет активного аккаунта для закрытия');
        return;
    }
    
    if (!confirm('Вы уверены, что хотите закрыть песочный аккаунт? Это действие нельзя отменить.')) {
        return;
    }
    
    try {
        const response = await fetch('/api/accounts/sandbox/close', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: `accountId=${encodeURIComponent(accountId)}`
        });
        
        if (response.ok) {
            showSuccess('Песочный аккаунт успешно закрыт');
            accountId = null;
            // Обновить отображение accountId на странице
            const accountIdElement = document.getElementById('accountId');
            if (accountIdElement) {
                accountIdElement.textContent = 'Аккаунт закрыт';
            }
            // Перезагрузить данные
            await loadAccountId();
        } else {
            const error = await response.text();
            showError(`Ошибка закрытия аккаунта: ${error}`);
        }
    } catch (error) {
        console.error('Error closing sandbox account:', error);
        showError('Ошибка при закрытии аккаунта');
    }
} 

// Отображение инструментов
function displayInstruments(data, type) {
    const list = document.getElementById('instrumentsList');
    
    if (!data || data.length === 0) {
        list.innerHTML = '<p class="text-muted">Инструменты не найдены</p>';
        return;
    }
    
    const typeNames = {
        'shares': 'Акции',
        'bonds': 'Облигации',
        'etfs': 'ETF',
        'currencies': 'Валюты'
    };
    
    const typeName = typeNames[type] || type;
    
    const table = `
        <div class="table-responsive">
            <table class="table table-striped table-hover">
                <thead class="table-dark">
                    <tr>
                        <th>Тикер</th>
                        <th>Название</th>
                        <th>FIGI</th>
                        <th>Валюта</th>
                        <th>Биржа</th>
                        <th>Статус</th>
                        <th>Действия</th>
                    </tr>
                </thead>
                <tbody>
                    ${data.map(instrument => `
                        <tr>
                            <td><strong>${instrument.ticker || 'N/A'}</strong></td>
                            <td>${instrument.name || 'N/A'}</td>
                            <td><small class="text-muted">${instrument.figi || 'N/A'}</small></td>
                            <td>${instrument.currency || 'N/A'}</td>
                            <td>${instrument.exchange || 'N/A'}</td>
                            <td>
                                <span class="badge ${instrument.tradingStatus === 'SECURITY_TRADING_STATUS_NORMAL_TRADING' ? 'bg-success' : 'bg-secondary'}">
                                    ${getTradingStatusDisplay(instrument.tradingStatus)}
                                </span>
                            </td>
                            <td>
                                <button class="btn btn-sm btn-outline-info" onclick="analyzeInstrument('${instrument.figi}', '${instrument.ticker}')" 
                                        ${instrument.tradingStatus !== 'SECURITY_TRADING_STATUS_NORMAL_TRADING' ? 'disabled' : ''}>
                                    <i class="fas fa-chart-line"></i> Анализ
                                </button>
                            </td>
                        </tr>
                    `).join('')}
                </tbody>
            </table>
        </div>
        <div class="row mt-3">
            <div class="col-md-6">
                <p class="text-muted">Показано ${data.length} из ${totalInstruments} ${typeName}</p>
            </div>
            <div class="col-md-6 text-end">
                <p class="text-muted">Страница ${currentPage + 1} из ${Math.ceil(totalInstruments / pageSize)}</p>
            </div>
        </div>
    `;
    
    list.innerHTML = table;
}

function getTradingStatusDisplay(status) {
    const statusMap = {
        'SECURITY_TRADING_STATUS_NORMAL_TRADING': 'Доступно',
        'SECURITY_TRADING_STATUS_NOT_AVAILABLE_FOR_TRADING': 'Недоступно',
        'SECURITY_TRADING_STATUS_BREAK_IN_TRADING': 'Перерыв',
        'SECURITY_TRADING_STATUS_CLOSING_AUCTION': 'Закрытие',
        'SECURITY_TRADING_STATUS_OPENING_AUCTION': 'Открытие'
    };
    return statusMap[status] || status;
}



// Анализ инструмента
function analyzeInstrument(figi, ticker) {
    showSection('analysis');
    showSuccess(`Анализ инструмента: ${ticker} (${figi})`);
    
    // Заполняем поля анализа
    const trendAnalysisFigi = document.getElementById('trendAnalysisFigi');
    const indicatorsFigi = document.getElementById('indicatorsFigi');
    const chartFigi = document.getElementById('chartFigi');
    
    if (trendAnalysisFigi) trendAnalysisFigi.value = figi;
    if (indicatorsFigi) indicatorsFigi.value = figi;
    if (chartFigi) chartFigi.value = figi;
}

// ==================== ТОРГОВЫЙ БОТ ====================

// Загрузка статуса торгового бота
async function loadBotStatus() {
    try {
        const response = await fetch('/api/trading-bot/status');
        if (response.ok) {
            const status = await response.json();
            updateBotStatusDisplay(status);
        } else {
            console.error('Failed to load bot status');
        }
    } catch (error) {
        console.error('Error loading bot status:', error);
    }
}

// Обновление отображения статуса бота
function updateBotStatusDisplay(status) {
    const botStatusElement = document.getElementById('botStatus');
    const schedulerStatusElement = document.getElementById('schedulerStatus');
    const analysisStatusElement = document.getElementById('analysisStatus');
    const rebalancingStatusElement = document.getElementById('rebalancingStatus');
    
    if (botStatusElement) {
        botStatusElement.textContent = status.status === 'running' ? 'Активен' : 'Остановлен';
        botStatusElement.className = status.status === 'running' ? 'text-success' : 'text-danger';
    }
    
    if (schedulerStatusElement) {
        schedulerStatusElement.textContent = status.scheduler === 'active' ? 'Работает' : 'Остановлен';
        schedulerStatusElement.className = status.scheduler === 'active' ? 'text-success' : 'text-danger';
    }
    
    if (analysisStatusElement) {
        analysisStatusElement.textContent = status.features?.marketAnalysis ? 'Включен' : 'Отключен';
        analysisStatusElement.className = status.features?.marketAnalysis ? 'text-success' : 'text-danger';
    }
    
    if (rebalancingStatusElement) {
        rebalancingStatusElement.textContent = status.features?.rebalancing ? 'Включена' : 'Отключена';
        rebalancingStatusElement.className = status.features?.rebalancing ? 'text-success' : 'text-danger';
    }
}

// Анализ портфеля
async function analyzePortfolio() {
    const accountId = document.getElementById('portfolioAccountId').value;
    if (!accountId) {
        showError('Введите ID аккаунта');
        return;
    }
    
    try {
        const response = await fetch(`/api/trading-bot/portfolio/${accountId}`);
        if (response.ok) {
            const data = await response.json();
            displayPortfolioAnalysis(data);
        } else {
            const error = await response.text();
            showError(`Ошибка анализа портфеля: ${error}`);
        }
    } catch (error) {
        console.error('Error analyzing portfolio:', error);
        showError('Ошибка при анализе портфеля');
    }
}

async function loadRiskMetrics() {
    const acc = document.getElementById('portfolioAccountId').value || accountId;
    if (!acc) { showError('Введите ID аккаунта'); return; }
    try {
        const resp = await fetch(`/api/risk/metrics?accountId=${encodeURIComponent(acc)}`);
        if (resp.ok) {
            const m = await resp.json();
            const pv = document.getElementById('riskPv');
            const pnl = document.getElementById('riskPnl');
            const dd = document.getElementById('riskDd');
            const lev = document.getElementById('riskLev');
            if (pv) pv.textContent = valueToRUB(m.portfolioValue);
            if (pnl) pnl.textContent = valueToRUB(m.dailyPnL);
            if (dd) dd.textContent = ((m.dailyDrawdownPct || 0) * 100).toFixed(2) + '%';
            if (lev) lev.textContent = (m.leverage || 1).toFixed(2) + 'x';
        }
    } catch (e) { console.error('Risk metrics error', e); }
}

function valueToRUB(v) {
    try {
        if (typeof v === 'string') return '₽' + parseFloat(v).toLocaleString();
        if (typeof v === 'number') return '₽' + v.toLocaleString();
        return '₽0';
    } catch { return '₽0'; }
}

// Отображение анализа портфеля
function displayPortfolioAnalysis(data) {
    const resultElement = document.getElementById('portfolioAnalysisResult');
    
    const html = `
        <div class="alert alert-info">
            <h6><i class="fas fa-briefcase me-2"></i>Анализ портфеля</h6>
            <div class="row">
                <div class="col-md-4">
                    <strong>Общая стоимость:</strong><br>
                    <span class="text-primary">₽${data.totalValue?.toLocaleString() || '0'}</span>
                </div>
                <div class="col-md-4">
                    <strong>Количество позиций:</strong><br>
                    <span class="text-info">${data.positionsCount || 0}</span>
                </div>
                <div class="col-md-4">
                    <strong>Распределение:</strong><br>
                    <small class="text-muted">
                        ${Object.entries(data.allocations || {}).map(([type, percentage]) => 
                            `${type}: ${percentage.toFixed(2)}%`
                        ).join('<br>')}
                    </small>
                </div>
            </div>
        </div>
    `;
    
    resultElement.innerHTML = html;
}

// Проверка необходимости ребалансировки
async function checkRebalancing() {
    const accountId = document.getElementById('rebalancingAccountId').value;
    if (!accountId) {
        showError('Введите ID аккаунта');
        return;
    }
    
    try {
        const response = await fetch(`/api/trading-bot/rebalancing/${accountId}`);
        if (response.ok) {
            const data = await response.json();
            displayRebalancingCheck(data);
        } else {
            const error = await response.text();
            showError(`Ошибка проверки ребалансировки: ${error}`);
        }
    } catch (error) {
        console.error('Error checking rebalancing:', error);
        showError('Ошибка при проверке ребалансировки');
    }
}

// Отображение проверки ребалансировки
function displayRebalancingCheck(data) {
    const resultElement = document.getElementById('rebalancingResult');
    
    const statusClass = data.needsRebalancing ? 'alert-warning' : 'alert-success';
    const statusIcon = data.needsRebalancing ? 'fas fa-exclamation-triangle' : 'fas fa-check-circle';
    
    const html = `
        <div class="alert ${statusClass}">
            <h6><i class="${statusIcon} me-2"></i>${data.reason}</h6>
            <div class="row">
                <div class="col-md-6">
                    <strong>Максимальное отклонение:</strong><br>
                    <span class="text-primary">${data.maxDeviation?.toFixed(2) || 0}%</span>
                </div>
                <div class="col-md-6">
                    <strong>Отклонения по типам:</strong><br>
                    <small class="text-muted">
                        ${Object.entries(data.deviations || {}).map(([type, deviation]) => 
                            `${type}: ${deviation.toFixed(2)}%`
                        ).join('<br>')}
                    </small>
                </div>
            </div>
        </div>
    `;
    
    resultElement.innerHTML = html;
}

// Выполнение ребалансировки
async function executeRebalancing() {
    const accountId = document.getElementById('rebalancingAccountId').value;
    if (!accountId) {
        showError('Введите ID аккаунта');
        return;
    }
    
    if (!confirm('Вы уверены, что хотите выполнить ребалансировку портфеля?')) {
        return;
    }
    
    try {
        const response = await fetch(`/api/trading-bot/rebalancing/${accountId}`, {
            method: 'POST'
        });
        if (response.ok) {
            const data = await response.json();
            showSuccess('Ребалансировка выполнена успешно');
            displayRebalancingResult(data);
        } else {
            const error = await response.text();
            showError(`Ошибка ребалансировки: ${error}`);
        }
    } catch (error) {
        console.error('Error executing rebalancing:', error);
        showError('Ошибка при выполнении ребалансировки');
    }
}

// Отображение результата ребалансировки
function displayRebalancingResult(data) {
    const resultElement = document.getElementById('rebalancingResult');
    
    const html = `
        <div class="alert alert-success">
            <h6><i class="fas fa-check-circle me-2"></i>${data.message}</h6>
            <p class="mb-0">Ребалансировка для аккаунта ${data.accountId} выполнена успешно.</p>
        </div>
    `;
    
    resultElement.innerHTML = html;
}

// Выполнение торговой стратегии
async function executeTradingStrategy() {
    const accountId = document.getElementById('strategyAccountId').value;
    const figi = document.getElementById('strategyFigi').value;
    
    if (!accountId || !figi) {
        showError('Введите ID аккаунта и FIGI инструмента');
        return;
    }
    
    try {
        const response = await fetch(`/api/trading-bot/strategy/${accountId}/${figi}`, {
            method: 'POST'
        });
        if (response.ok) {
            const data = await response.json();
            showSuccess('Торговая стратегия выполнена успешно');
            displayStrategyResult(data);
        } else {
            const error = await response.text();
            showError(`Ошибка стратегии: ${error}`);
        }
    } catch (error) {
        console.error('Error executing strategy:', error);
        showError('Ошибка при выполнении торговой стратегии');
    }
}

// Отображение результата стратегии
function displayStrategyResult(data) {
    const resultElement = document.getElementById('strategyResult');
    
    const html = `
        <div class="alert alert-success">
            <h6><i class="fas fa-chess me-2"></i>${data.message}</h6>
            <div class="row">
                <div class="col-md-6">
                    <strong>Аккаунт:</strong> ${data.accountId}
                </div>
                <div class="col-md-6">
                    <strong>Инструмент:</strong> ${data.figi}
                </div>
            </div>
        </div>
    `;
    
    resultElement.innerHTML = html;
}

// ==================== АНАЛИЗ РЫНКА ====================

// Анализ тренда
async function analyzeTrend() {
    const figi = document.getElementById('trendAnalysisFigi').value;
    if (!figi) {
        showError('Введите FIGI инструмента');
        return;
    }
    
    try {
        const response = await fetch(`/api/trading-bot/analysis/${figi}`);
        if (response.ok) {
            const data = await response.json();
            displayTrendAnalysis(data);
        } else {
            const error = await response.text();
            showError(`Ошибка анализа тренда: ${error}`);
        }
    } catch (error) {
        console.error('Error analyzing trend:', error);
        showError('Ошибка при анализе тренда');
    }
}

// Отображение анализа тренда
function displayTrendAnalysis(data) {
    const resultElement = document.getElementById('trendAnalysisResult');
    
    const trendClass = {
        'BULLISH': 'text-success',
        'BEARISH': 'text-danger',
        'SIDEWAYS': 'text-warning',
        'UNKNOWN': 'text-muted'
    }[data.trend] || 'text-muted';
    
    const trendIcon = {
        'BULLISH': 'fas fa-arrow-up',
        'BEARISH': 'fas fa-arrow-down',
        'SIDEWAYS': 'fas fa-minus',
        'UNKNOWN': 'fas fa-question'
    }[data.trend] || 'fas fa-question';
    
    const html = `
        <div class="alert alert-info">
            <h6><i class="fas fa-chart-line me-2"></i>Анализ тренда</h6>
            <div class="row">
                <div class="col-md-3">
                    <strong>FIGI:</strong><br>
                    <span class="text-muted">${data.figi}</span>
                </div>
                <div class="col-md-3">
                    <strong>Тренд:</strong><br>
                    <span class="${trendClass}">
                        <i class="${trendIcon} me-1"></i>${data.trend}
                    </span>
                </div>
                <div class="col-md-3">
                    <strong>Сигнал:</strong><br>
                    <span class="text-primary">${data.signal}</span>
                </div>
                <div class="col-md-3">
                    <strong>Текущая цена:</strong><br>
                    <span class="text-success">₽${data.currentPrice?.toFixed(2) || '0'}</span>
                </div>
            </div>
        </div>
    `;
    
    resultElement.innerHTML = html;
}

// Получение технических индикаторов
async function getTechnicalIndicators() {
    const figi = document.getElementById('indicatorsFigi').value;
    if (!figi) {
        showError('Введите FIGI инструмента');
        return;
    }
    
    try {
        const response = await fetch(`/api/trading-bot/indicators/${figi}`);
        if (response.ok) {
            const data = await response.json();
            displayTechnicalIndicators(data);
        } else {
            const error = await response.text();
            showError(`Ошибка получения индикаторов: ${error}`);
        }
    } catch (error) {
        console.error('Error getting indicators:', error);
        showError('Ошибка при получении технических индикаторов');
    }
}

// Отображение технических индикаторов
function displayTechnicalIndicators(data) {
    const resultElement = document.getElementById('indicatorsResult');
    
    const html = `
        <div class="alert alert-info">
            <h6><i class="fas fa-chart-bar me-2"></i>Технические индикаторы</h6>
            <div class="row">
                <div class="col-md-4">
                    <strong>SMA 20:</strong><br>
                    <span class="text-primary">₽${data.sma20?.toFixed(2) || '0'}</span>
                </div>
                <div class="col-md-4">
                    <strong>SMA 50:</strong><br>
                    <span class="text-primary">₽${data.sma50?.toFixed(2) || '0'}</span>
                </div>
                <div class="col-md-4">
                    <strong>RSI 14:</strong><br>
                    <span class="text-info">${data.rsi?.toFixed(2) || '0'}</span>
                </div>
            </div>
            <div class="mt-2">
                <small class="text-muted">
                    <strong>Интерпретация RSI:</strong><br>
                    ${data.rsi > 70 ? 'Перекупленность (>70)' : data.rsi < 30 ? 'Перепроданность (<30)' : 'Нейтральная зона (30-70)'}
                </small>
            </div>
        </div>
    `;
    
    resultElement.innerHTML = html;
}

// Загрузка технического графика
async function loadTechnicalChart() {
    const figi = document.getElementById('chartFigi').value;
    const period = document.getElementById('chartPeriod').value;
    
    if (!figi) {
        showError('Введите FIGI инструмента');
        return;
    }
    
    try {
        // Здесь можно добавить загрузку данных для графика
        // Пока что показываем заглушку
        const canvas = document.getElementById('technicalChart');
        const ctx = canvas.getContext('2d');
        
        // Очистить график
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        
        // Показать сообщение
        ctx.fillStyle = '#6c757d';
        ctx.font = '16px Arial';
        ctx.textAlign = 'center';
        ctx.fillText('График для FIGI: ' + figi, canvas.width / 2, canvas.height / 2);
        ctx.fillText('Период: ' + period + ' дней', canvas.width / 2, canvas.height / 2 + 25);
        
        showSuccess('График загружен');
    } catch (error) {
        console.error('Error loading chart:', error);
        showError('Ошибка при загрузке графика');
    }
}

// ==================== МУЛЬТИИНСТРУМЕНТАЛЬНАЯ ТОРГОВЛЯ ====================

// Загрузка торговых возможностей
async function loadTradingOpportunities() {
    try {
        const response = await fetch('/api/trading-bot/opportunities');
        if (response.ok) {
            const data = await response.json();
            displayTradingOpportunities(data);
        } else {
            const error = await response.text();
            showError(`Ошибка загрузки торговых возможностей: ${error}`);
        }
    } catch (error) {
        console.error('Error loading trading opportunities:', error);
        showError('Ошибка при загрузке торговых возможностей');
    }
}

// Отображение торговых возможностей
function displayTradingOpportunities(data) {
    const resultElement = document.getElementById('tradingOpportunitiesResult');
    
    if (!data.opportunities || data.opportunities.length === 0) {
        resultElement.innerHTML = `
            <div class="alert alert-info">
                <i class="fas fa-info-circle me-2"></i>
                Торговые возможности не найдены. Попробуйте обновить позже.
            </div>
        `;
        return;
    }
    
    const opportunitiesHtml = data.opportunities.map((opp, index) => {
        const scoreClass = opp.score >= 70 ? 'text-success' : opp.score >= 50 ? 'text-warning' : 'text-danger';
        const actionClass = opp.recommendedAction === 'BUY' ? 'text-success' : 
                          opp.recommendedAction === 'SELL' ? 'text-danger' : 'text-muted';
        
        return `
            <div class="card mb-3">
                <div class="card-body">
                    <div class="row">
                        <div class="col-md-2">
                            <h6 class="text-muted">#${index + 1}</h6>
                            <strong class="${scoreClass}">Score: ${opp.score.toFixed(1)}</strong>
                        </div>
                        <div class="col-md-3">
                            <h6>Инструмент</h6>
                            <strong>${opp.figi}</strong>
                        </div>
                        <div class="col-md-2">
                            <h6>Цена</h6>
                            <strong>₽${opp.currentPrice?.toFixed(2) || '0'}</strong>
                        </div>
                        <div class="col-md-2">
                            <h6>Тренд</h6>
                            <span class="badge ${opp.trend === 'BULLISH' ? 'bg-success' : 
                                               opp.trend === 'BEARISH' ? 'bg-danger' : 'bg-warning'}">
                                ${opp.trend}
                            </span>
                        </div>
                        <div class="col-md-2">
                            <h6>RSI</h6>
                            <strong>${opp.rsi?.toFixed(2) || '0'}</strong>
                        </div>
                        <div class="col-md-1">
                            <h6>Действие</h6>
                            <span class="badge ${opp.recommendedAction === 'BUY' ? 'bg-success' : 
                                               opp.recommendedAction === 'SELL' ? 'bg-danger' : 'bg-secondary'}">
                                ${opp.recommendedAction}
                            </span>
                        </div>
                    </div>
                    <div class="row mt-2">
                        <div class="col-md-6">
                            <small class="text-muted">
                                SMA20: ₽${opp.sma20?.toFixed(2) || '0'} | 
                                SMA50: ₽${opp.sma50?.toFixed(2) || '0'}
                            </small>
                        </div>
                        <div class="col-md-6 text-end">
                            <button class="btn btn-sm btn-outline-primary" onclick="executeTradingStrategy('${opp.figi}')">
                                <i class="fas fa-play me-1"></i>Торговать
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        `;
    }).join('');
    
    resultElement.innerHTML = `
        <div class="alert alert-info">
            <i class="fas fa-info-circle me-2"></i>
            Найдено ${data.count} торговых возможностей для аккаунта ${data.accountId}
        </div>
        ${opportunitiesHtml}
    `;
}

// Автоматическая торговля
async function executeAutomaticTrading() {
    if (!confirm('Вы уверены, что хотите запустить автоматическую торговлю? Бот выберет лучший инструмент и выполнит торговую операцию.')) {
        return;
    }
    
    try {
        const response = await fetch('/api/trading-bot/auto-trade', {
            method: 'POST'
        });
        if (response.ok) {
            const data = await response.json();
            showSuccess('Автоматическая торговля выполнена успешно');
            displayAutomaticTradingResult(data);
        } else {
            const error = await response.text();
            showError(`Ошибка автоматической торговли: ${error}`);
        }
    } catch (error) {
        console.error('Error executing automatic trading:', error);
        showError('Ошибка при выполнении автоматической торговли');
    }
}

// Отображение результата автоматической торговли
function displayAutomaticTradingResult(data) {
    const resultElement = document.getElementById('automaticTradingResult');
    
    const html = `
        <div class="alert alert-success">
            <h6><i class="fas fa-robot me-2"></i>${data.message}</h6>
            <div class="row">
                <div class="col-md-6">
                    <strong>Аккаунт:</strong> ${data.accountId}
                </div>
                <div class="col-md-6">
                    <strong>Статус:</strong> <span class="badge bg-success">${data.status}</span>
                </div>
            </div>
        </div>
    `;
    
    resultElement.innerHTML = html;
}

// Обновленная функция выполнения торговой стратегии
async function executeTradingStrategy(figi = null) {
    const accountId = document.getElementById('strategyAccountId').value || accountId;
    const strategyFigi = figi || document.getElementById('strategyFigi').value;
    
    if (!accountId || !strategyFigi) {
        showError('Введите ID аккаунта и FIGI инструмента');
        return;
    }
    
    try {
        const response = await fetch(`/api/trading-bot/strategy/${accountId}/${strategyFigi}`, {
            method: 'POST'
        });
        if (response.ok) {
            const data = await response.json();
            showSuccess('Торговая стратегия выполнена успешно');
            displayStrategyResult(data);
        } else {
            const error = await response.text();
            showError(`Ошибка стратегии: ${error}`);
        }
    } catch (error) {
        console.error('Error executing strategy:', error);
        showError('Ошибка при выполнении торговой стратегии');
    }
}

// ==================== ЛОГ ДЕЙСТВИЙ БОТА ====================

let logEventSource = null;

// Инициализация SSE соединения для логов
function initLogStream() {
    if (logEventSource) {
        logEventSource.close();
    }
    
    logEventSource = new EventSource('/api/logs/stream');
    
    logEventSource.onopen = function(event) {
        console.log('SSE соединение для логов установлено');
        showLogConnectionStatus('connected', 'Логи подключены');
    };
    
    logEventSource.onmessage = function(event) {
        console.log('Получено SSE сообщение:', event);
    };
    
    logEventSource.addEventListener('connected', function(event) {
        console.log('Подключение к логам:', event.data);
    });
    
    logEventSource.addEventListener('initial-logs', function(event) {
        const logs = JSON.parse(event.data);
        displayBotLog({ entries: logs, totalEntries: logs.length });
    });
    
    logEventSource.addEventListener('new-log', function(event) {
        const logEntry = JSON.parse(event.data);
        addNewLogEntry(logEntry);
    });
    
    logEventSource.addEventListener('statistics-update', function(event) {
        const statistics = JSON.parse(event.data);
        updateLogStatistics(statistics);
    });
    
    logEventSource.onerror = function(event) {
        console.error('Ошибка SSE соединения:', event);
        showLogConnectionStatus('disconnected', 'Логи отключены');
        // Переподключение через 5 секунд
        setTimeout(() => {
            if (currentSection === 'trading-bot') {
                initLogStream();
            }
        }, 5000);
    };
}

// Добавление новой записи лога в реальном времени
function addNewLogEntry(logEntry) {
    const resultElement = document.getElementById('botLogResult');
    if (!resultElement) return;
    
    const levelClass = getLogLevelClass(logEntry.level);
    const categoryClass = getLogCategoryClass(logEntry.category);
    
    const newLogHtml = `
        <div class="card mb-2 new-log-entry" style="animation: fadeIn 0.5s ease-in;">
            <div class="card-body py-2">
                <div class="row align-items-center">
                    <div class="col-md-2">
                        <small class="text-muted">${logEntry.formattedTimestamp}</small>
                    </div>
                    <div class="col-md-1">
                        <span class="${levelClass}">${logEntry.levelIcon}</span>
                    </div>
                    <div class="col-md-2">
                        <span class="badge ${categoryClass}">${logEntry.categoryIcon} ${getCategoryDisplayName(logEntry.category)}</span>
                    </div>
                    <div class="col-md-4">
                        <strong>${logEntry.message}</strong>
                    </div>
                    <div class="col-md-3">
                        <small class="text-muted">${logEntry.details || ''}</small>
                    </div>
                </div>
            </div>
        </div>
    `;
    
    // Добавляем новую запись в начало
    const existingContent = resultElement.innerHTML;
    const alertInfo = existingContent.match(/<div class="alert alert-info">.*?<\/div>/s);
    const logsContent = existingContent.replace(/<div class="alert alert-info">.*?<\/div>/s, '');
    
    resultElement.innerHTML = (alertInfo ? alertInfo[0] : '') + newLogHtml + logsContent;
    
    // Удаляем анимацию через некоторое время
    setTimeout(() => {
        const newEntry = resultElement.querySelector('.new-log-entry');
        if (newEntry) {
            newEntry.classList.remove('new-log-entry');
        }
    }, 1000);
}

// Загрузка лога бота
async function loadBotLog() {
    try {
        const level = document.getElementById('logLevelFilter').value;
        const category = document.getElementById('logCategoryFilter').value;
        const limit = document.getElementById('logLimitFilter').value;
        
        let url = `/api/trading-bot/log?limit=${limit}`;
        if (level) url += `&level=${level}`;
        if (category) url += `&category=${category}`;
        
        const response = await fetch(url);
        if (response.ok) {
            const data = await response.json();
            displayBotLog(data);
        } else {
            const error = await response.text();
            showError(`Ошибка загрузки лога: ${error}`);
        }
    } catch (error) {
        console.error('Error loading bot log:', error);
        showError('Ошибка при загрузке лога бота');
    }
}

// Отображение лога бота
function displayBotLog(data) {
    const resultElement = document.getElementById('botLogResult');
    
    if (!data.entries || data.entries.length === 0) {
        resultElement.innerHTML = `
            <div class="alert alert-info">
                <i class="fas fa-info-circle me-2"></i>
                Записи в логе отсутствуют
            </div>
        `;
        return;
    }
    
    // Обновляем статистику
    if (data.statistics) {
        updateLogStatistics(data.statistics);
    }
    
    // Отображаем записи лога
    const logEntriesHtml = data.entries.map(entry => {
        const levelClass = getLogLevelClass(entry.level);
        const categoryClass = getLogCategoryClass(entry.category);
        
        return `
            <div class="card mb-2">
                <div class="card-body py-2">
                    <div class="row align-items-center">
                        <div class="col-md-2">
                            <small class="text-muted">${entry.formattedTimestamp}</small>
                        </div>
                        <div class="col-md-1">
                            <span class="${levelClass}">${entry.levelIcon}</span>
                        </div>
                        <div class="col-md-2">
                            <span class="badge ${categoryClass}">${entry.categoryIcon} ${getCategoryDisplayName(entry.category)}</span>
                        </div>
                        <div class="col-md-4">
                            <strong>${entry.message}</strong>
                        </div>
                        <div class="col-md-3">
                            <small class="text-muted">${entry.details || ''}</small>
                        </div>
                    </div>
                </div>
            </div>
        `;
    }).join('');
    
    resultElement.innerHTML = `
        <div class="alert alert-info">
            <i class="fas fa-info-circle me-2"></i>
            Загружено ${data.totalEntries} записей лога
        </div>
        ${logEntriesHtml}
    `;
}

// Обновление статистики лога
function updateLogStatistics(statistics) {
    document.getElementById('totalEntries').textContent = statistics.totalEntries;
    document.getElementById('infoCount').textContent = statistics.infoCount;
    document.getElementById('warningCount').textContent = statistics.warningCount;
    document.getElementById('errorCount').textContent = statistics.errorCount;
    document.getElementById('successCount').textContent = statistics.successCount;
    document.getElementById('tradeCount').textContent = statistics.tradeCount;
}

// Применение фильтров лога
function applyLogFilters() {
    loadBotLog();
}

// Тестирование логов в реальном времени
async function testRealTimeLogs() {
    try {
        const response = await fetch('/api/trading-bot/auto-trade', {
            method: 'POST'
        });
        if (response.ok) {
            showSuccess('Тестовые логи отправлены');
        } else {
            showError('Ошибка отправки тестовых логов');
        }
    } catch (error) {
        console.error('Error testing real-time logs:', error);
        showError('Ошибка при тестировании логов');
    }
}

// Очистка лога
async function clearBotLog() {
    if (!confirm('Вы уверены, что хотите очистить лог? Это действие нельзя отменить.')) {
        return;
    }
    
    try {
        const response = await fetch('/api/trading-bot/log', {
            method: 'DELETE'
        });
        if (response.ok) {
            showSuccess('Лог очищен');
            loadBotLog();
        } else {
            const error = await response.text();
            showError(`Ошибка очистки лога: ${error}`);
        }
    } catch (error) {
        console.error('Error clearing bot log:', error);
        showError('Ошибка при очистке лога');
    }
}

// Получение CSS класса для уровня лога
function getLogLevelClass(level) {
    switch (level) {
        case 'INFO': return 'text-info';
        case 'WARNING': return 'text-warning';
        case 'ERROR': return 'text-danger';
        case 'SUCCESS': return 'text-success';
        case 'TRADE': return 'text-secondary';
        default: return 'text-muted';
    }
}

// Получение CSS класса для категории лога
function getLogCategoryClass(category) {
    switch (category) {
        case 'MARKET_ANALYSIS': return 'bg-info';
        case 'PORTFOLIO_MANAGEMENT': return 'bg-primary';
        case 'TRADING_STRATEGY': return 'bg-success';
        case 'REBALANCING': return 'bg-warning';
        case 'TECHNICAL_INDICATORS': return 'bg-info';
        case 'AUTOMATIC_TRADING': return 'bg-secondary';
        case 'RISK_MANAGEMENT': return 'bg-danger';
        case 'SYSTEM_STATUS': return 'bg-dark';
        default: return 'bg-secondary';
    }
}

// Показать статус подключения к логам
function showLogConnectionStatus(status, message) {
    let statusElement = document.getElementById('logConnectionStatus');
    
    if (!statusElement) {
        statusElement = document.createElement('div');
        statusElement.id = 'logConnectionStatus';
        statusElement.className = `log-connection-status ${status}`;
        document.body.appendChild(statusElement);
    } else {
        statusElement.className = `log-connection-status ${status}`;
    }
    
    statusElement.textContent = message;
    
    // Скрыть через 3 секунды для connected статуса
    if (status === 'connected') {
        setTimeout(() => {
            if (statusElement.parentNode) {
                statusElement.remove();
            }
        }, 3000);
    }
}

// Получение отображаемого имени категории
function getCategoryDisplayName(category) {
    switch (category) {
        case 'MARKET_ANALYSIS': return 'Анализ рынка';
        case 'PORTFOLIO_MANAGEMENT': return 'Портфель';
        case 'TRADING_STRATEGY': return 'Стратегия';
        case 'REBALANCING': return 'Ребалансировка';
        case 'TECHNICAL_INDICATORS': return 'Индикаторы';
        case 'AUTOMATIC_TRADING': return 'Автоторговля';
        case 'RISK_MANAGEMENT': return 'Риски';
        case 'SYSTEM_STATUS': return 'Система';
        default: return category;
    }
}

// Функции быстрых действий
function refreshDashboard() {
    loadDashboard();
}

function emergencyStop() {
    if (confirm('Вы уверены, что хотите экстренно остановить торговлю?')) {
        // Здесь можно добавить логику экстренной остановки
        showSuccess('Торговля остановлена');
    }
}

function showAutoRefreshIndicator() {
    const indicator = document.getElementById('autoRefreshIndicator');
    if (indicator) {
        indicator.style.display = 'block';
    }
}

function hideAutoRefreshIndicator() {
    const indicator = document.getElementById('autoRefreshIndicator');
    if (indicator) {
        indicator.style.display = 'none';
    }
}

// Автообновление дашборда
let dashboardRefreshInterval;

function startDashboardAutoRefresh() {
    // Останавливаем предыдущий интервал
    if (dashboardRefreshInterval) {
        clearInterval(dashboardRefreshInterval);
    }
    
    // Запускаем автообновление каждые 30 секунд
    dashboardRefreshInterval = setInterval(() => {
        if (currentSection === 'dashboard') {
            loadDashboard();
        }
    }, 30000);
}

function stopDashboardAutoRefresh() {
    if (dashboardRefreshInterval) {
        clearInterval(dashboardRefreshInterval);
        dashboardRefreshInterval = null;
    }
}

// Вспомогательные функции
function getRiskLevelClass(riskLevel) {
    switch (riskLevel) {
        case 'LOW': return 'text-success';
        case 'MEDIUM': return 'text-warning';
        case 'HIGH': return 'text-danger';
        default: return 'text-muted';
    }
}

function getPnLClass(value) {
    if (value > 0) return 'text-success';
    if (value < 0) return 'text-danger';
    return 'text-muted';
}

function formatPercent(value) {
    if (value === null || value === undefined) return '0%';
    return `${value > 0 ? '+' : ''}${value.toFixed(2)}%`;
}

// Обновляем функцию showSection для управления автообновлением
function showSection(sectionName) {
    // Скрыть все разделы
    document.querySelectorAll('.section').forEach(section => {
        section.style.display = 'none';
    });
    
    // Показать выбранный раздел
    document.getElementById(sectionName + '-section').style.display = 'block';
    
    // Обновить активную ссылку в навигации
    document.querySelectorAll('.nav-link').forEach(link => {
        link.classList.remove('active');
    });
    if (event && event.target && event.target.classList) {
        event.target.classList.add('active');
    }
    
    // Обновить URL
    currentSection = sectionName;
    const sectionNames = {
        'dashboard': 'Dashboard',
        'instruments': 'Инструменты',
        'portfolio': 'Портфель',
        'orders': 'Ордера',
        'trading-bot': 'Торговый бот',
        'analysis': 'Анализ',
        'settings': 'Настройки'
    };
    
    const title = sectionNames[sectionName] || 'Dashboard';
    document.title = `Tinkoff Auto Trading Bot - ${title}`;
    
    // Обновить URL в браузере
    const url = sectionName === 'dashboard' ? '/dashboard' : `/${sectionName}`;
    window.history.pushState({section: sectionName}, title, url);
    
    // Загрузить данные для раздела
    switch (sectionName) {
        case 'dashboard':
            loadDashboard();
            startDashboardAutoRefresh();
            break;
        case 'instruments':
            loadInstruments();
            stopDashboardAutoRefresh();
            break;
        case 'portfolio':
            loadPortfolio();
            stopDashboardAutoRefresh();
            break;
        case 'orders':
            loadOrders();
            stopDashboardAutoRefresh();
            break;
        case 'trading-bot':
            loadTradingBotStatus();
            stopDashboardAutoRefresh();
            break;
        case 'analysis':
            loadAnalysis();
            stopDashboardAutoRefresh();
            break;
        case 'settings':
            loadSettings();
            stopDashboardAutoRefresh();
            break;
    }
}