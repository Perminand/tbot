package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.perminov.dto.ShareDto;
import ru.tinkoff.piapi.core.models.Position;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmartAnalysisService {
    
    private final DynamicInstrumentService dynamicInstrumentService;
    private final PortfolioManagementService portfolioManagementService;
    private final MarketAnalysisService marketAnalysisService;
    private final BotLogService botLogService;
    
    // Индекс для ротации инструментов
    private int rotationIndex = 0;
    private final int ROTATION_BATCH_SIZE = 20; // Размер группы для ротации
    
    // Кэш приоритетов инструментов
    private final Map<String, Integer> instrumentPriorities = new ConcurrentHashMap<>();
    private final Map<String, Long> lastAnalysisTime = new ConcurrentHashMap<>();
    
    // Настройки анализа
    private static final int QUICK_ANALYSIS_LIMIT = 10; // Быстрый анализ
    private static final int FULL_ANALYSIS_LIMIT = 30;  // Полный анализ
    private static final long ANALYSIS_CACHE_TTL = 5 * 60 * 1000; // 5 минут
    
    /**
     * Получение инструментов для быстрого анализа (30 сек)
     */
    public List<ShareDto> getInstrumentsForQuickAnalysis(String accountId) {
        List<ShareDto> instruments = new ArrayList<>();
        
        try {
            // 1. Существующие позиции (высший приоритет)
            List<ShareDto> existingPositions = getExistingPositions(accountId);
            instruments.addAll(existingPositions);
            log.info("Добавлено {} существующих позиций для быстрого анализа", existingPositions.size());
            
            // 2. Приоритетные инструменты (сигналы, тренды)
            List<ShareDto> priorityInstruments = getPriorityInstruments(5);
            instruments.addAll(priorityInstruments);
            log.info("Добавлено {} приоритетных инструментов для быстрого анализа", priorityInstruments.size());
            
            // 3. Ротируемые инструменты
            List<ShareDto> rotationInstruments = getRotationInstruments(5);
            instruments.addAll(rotationInstruments);
            log.info("Добавлено {} ротируемых инструментов для быстрого анализа", rotationInstruments.size());
            
            // Обновляем индекс ротации
            updateRotationIndex();
            
            // Убираем дубликаты и фильтруем заблокированные по ликвидности
            instruments = instruments.stream()
                .distinct()
                .filter(instrument -> {
                    if (portfolioManagementService.isLiquidityBlocked(instrument.getFigi())) {
                        long minutesLeft = portfolioManagementService.getLiquidityBlockRemainingMinutes(instrument.getFigi());
                        log.debug("Пропускаем {} из быстрого анализа - заблокирован по ликвидности (осталось ~{} мин)", 
                            instrument.getFigi(), minutesLeft);
                        return false;
                    }
                    return true;
                })
                .limit(QUICK_ANALYSIS_LIMIT)
                .collect(Collectors.toList());
            
            log.info("Итого для быстрого анализа: {} инструментов (заблокированные по ликвидности исключены)", instruments.size());
            
        } catch (Exception e) {
            log.error("Ошибка при получении инструментов для быстрого анализа: {}", e.getMessage());
            // Возвращаем резервный список
            return getFallbackInstruments();
        }
        
        return instruments;
    }
    
    /**
     * Получение инструментов для полного анализа (2 мин)
     */
    public List<ShareDto> getInstrumentsForFullAnalysis(String accountId) {
        List<ShareDto> instruments = new ArrayList<>();
        
        try {
            // 1. Существующие позиции (высший приоритет)
            List<ShareDto> existingPositions = getExistingPositions(accountId);
            instruments.addAll(existingPositions);
            log.info("Добавлено {} существующих позиций для полного анализа", existingPositions.size());
            
            // 2. Приоритетные инструменты (больше для полного анализа)
            List<ShareDto> priorityInstruments = getPriorityInstruments(15);
            instruments.addAll(priorityInstruments);
            log.info("Добавлено {} приоритетных инструментов для полного анализа", priorityInstruments.size());
            
            // 3. Ротируемые инструменты (больше для полного анализа)
            List<ShareDto> rotationInstruments = getRotationInstruments(15);
            instruments.addAll(rotationInstruments);
            log.info("Добавлено {} ротируемых инструментов для полного анализа", rotationInstruments.size());
            
            // Убираем дубликаты и фильтруем заблокированные по ликвидности
            instruments = instruments.stream()
                .distinct()
                .filter(instrument -> {
                    if (portfolioManagementService.isLiquidityBlocked(instrument.getFigi())) {
                        long minutesLeft = portfolioManagementService.getLiquidityBlockRemainingMinutes(instrument.getFigi());
                        log.debug("Пропускаем {} из полного анализа - заблокирован по ликвидности (осталось ~{} мин)", 
                            instrument.getFigi(), minutesLeft);
                        return false;
                    }
                    return true;
                })
                .limit(FULL_ANALYSIS_LIMIT)
                .collect(Collectors.toList());
            
            log.info("Итого для полного анализа: {} инструментов (заблокированные по ликвидности исключены)", instruments.size());
            
        } catch (Exception e) {
            log.error("Ошибка при получении инструментов для полного анализа: {}", e.getMessage());
            return getFallbackInstruments();
        }
        
        return instruments;
    }
    
    /**
     * Получение существующих позиций
     */
    private List<ShareDto> getExistingPositions(String accountId) {
        try {
            PortfolioManagementService.PortfolioAnalysis analysis = 
                portfolioManagementService.analyzePortfolio(accountId);
            
            List<ShareDto> positions = new ArrayList<>();
            
            for (Position position : analysis.getPositions()) {
                if (position.getQuantity().compareTo(BigDecimal.ZERO) != 0 && 
                    !"currency".equals(position.getInstrumentType())) {
                    
                    // Пропускаем заблокированные по ликвидности инструменты
                    if (portfolioManagementService.isLiquidityBlocked(position.getFigi())) {
                        long minutesLeft = portfolioManagementService.getLiquidityBlockRemainingMinutes(position.getFigi());
                        log.debug("Пропускаем позицию {} - заблокирована по ликвидности (осталось ~{} мин)", 
                            position.getFigi(), minutesLeft);
                        continue;
                    }
                    
                    boolean isShort = position.getQuantity().compareTo(BigDecimal.ZERO) < 0;
                    log.info("🔍 ПОЗИЦИЯ для анализа: FIGI={}, quantity={}, тип={}, шорт={}", 
                        position.getFigi(), position.getQuantity(), position.getInstrumentType(), isShort);
                    
                    // Конвертируем Position в ShareDto
                    ShareDto shareDto = new ShareDto();
                    shareDto.setFigi(position.getFigi());
                    shareDto.setTicker(position.getFigi().substring(0, Math.min(8, position.getFigi().length())));
                    shareDto.setName("Позиция: " + position.getInstrumentType() + (isShort ? " (ШОРТ)" : " (ЛОНГ)"));
                    shareDto.setInstrumentType(position.getInstrumentType());
                    shareDto.setCurrency("RUB");
                    shareDto.setExchange("MOEX");
                    shareDto.setTradingStatus("SECURITY_TRADING_STATUS_NORMAL_TRADING");
                    
                    positions.add(shareDto);
                }
            }
            
            log.info("🎯 Итого позиций для анализа: {}", positions.size());
            return positions;
            
        } catch (Exception e) {
            log.error("Ошибка при получении существующих позиций: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Получение приоритетных инструментов
     */
    private List<ShareDto> getPriorityInstruments(int count) {
        List<ShareDto> allInstruments = dynamicInstrumentService.getAvailableInstruments();
        
        // Сортируем по приоритету и фильтруем заблокированные по ликвидности
        return allInstruments.stream()
            .filter(this::isHighPriority)
            .filter(instrument -> {
                if (portfolioManagementService.isLiquidityBlocked(instrument.getFigi())) {
                    return false; // Пропускаем заблокированные
                }
                return true;
            })
            .sorted((i1, i2) -> {
                int priority1 = getInstrumentPriority(i1.getFigi());
                int priority2 = getInstrumentPriority(i2.getFigi());
                return Integer.compare(priority2, priority1); // По убыванию
            })
            .limit(count)
            .collect(Collectors.toList());
    }
    
    /**
     * Получение инструментов для ротации
     */
    private List<ShareDto> getRotationInstruments(int count) {
        List<ShareDto> allInstruments = dynamicInstrumentService.getAvailableInstruments();
        
        if (allInstruments.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Выбираем группу инструментов для ротации
        int startIndex = rotationIndex;
        int endIndex = Math.min(startIndex + count, allInstruments.size());
        
        List<ShareDto> rotationGroup = allInstruments.subList(startIndex, endIndex);
        
        // Если не хватает инструментов, берем с начала
        if (rotationGroup.size() < count) {
            int remaining = count - rotationGroup.size();
            rotationGroup.addAll(allInstruments.subList(0, remaining));
        }
        
        // Фильтруем заблокированные по ликвидности
        return rotationGroup.stream()
            .filter(instrument -> {
                if (portfolioManagementService.isLiquidityBlocked(instrument.getFigi())) {
                    return false; // Пропускаем заблокированные
                }
                return true;
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Проверка высокого приоритета
     */
    private boolean isHighPriority(ShareDto instrument) {
        // Проверяем, когда последний раз анализировали этот инструмент
        long lastAnalysis = lastAnalysisTime.getOrDefault(instrument.getFigi(), 0L);
        long currentTime = System.currentTimeMillis();
        
        // Если анализировали недавно, считаем низким приоритетом
        if (currentTime - lastAnalysis < ANALYSIS_CACHE_TTL) {
            return false;
        }
        
        // Логика определения приоритета
        // - Инструменты с сигналами (RSI, тренды)
        // - Ликвидные инструменты
        // - Инструменты в тренде
        return true; // Упрощенная логика для начала
    }
    
    /**
     * Получение приоритета инструмента
     */
    private int getInstrumentPriority(String figi) {
        return instrumentPriorities.getOrDefault(figi, 0);
    }
    
    /**
     * Обновление приоритета инструмента
     */
    public void updateInstrumentPriority(String figi, int priority) {
        instrumentPriorities.put(figi, priority);
        lastAnalysisTime.put(figi, System.currentTimeMillis());
    }
    
    /**
     * Обновление индекса ротации
     */
    private void updateRotationIndex() {
        List<ShareDto> allInstruments = dynamicInstrumentService.getAvailableInstruments();
        if (!allInstruments.isEmpty()) {
            rotationIndex = (rotationIndex + ROTATION_BATCH_SIZE) % allInstruments.size();
        }
    }
    
    /**
     * Улучшенный резервный список инструментов
     * Используется при ошибках API или пустом кэше
     */
    private List<ShareDto> getFallbackInstruments() {
        List<ShareDto> instruments = new ArrayList<>();
        
        // 🔵 АКЦИИ - Голубые фишки
        addFallbackInstrument(instruments, "TCS00A106YF0", "TCS", "Тинькофф Банк", "share");
        addFallbackInstrument(instruments, "BBG004730N88", "SBER", "Сбербанк России", "share");
        addFallbackInstrument(instruments, "BBG0047315Y7", "GAZP", "Газпром", "share");
        addFallbackInstrument(instruments, "BBG004731354", "LKOH", "Лукойл", "share");
        addFallbackInstrument(instruments, "BBG004731489", "NVTK", "Новатэк", "share");
        addFallbackInstrument(instruments, "BBG004731032", "ROSN", "Роснефть", "share");
        addFallbackInstrument(instruments, "BBG0047315D0", "MGNT", "Магнит", "share");
        addFallbackInstrument(instruments, "BBG0047312Z9", "YNDX", "Яндекс", "share");
        addFallbackInstrument(instruments, "BBG0047319J7", "VKUS", "ВкусВилл", "share");
        addFallbackInstrument(instruments, "BBG0047319J7", "OZON", "Ozon", "share");
        
        // 🟢 ОБЛИГАЦИИ - Государственные
        addFallbackInstrument(instruments, "BBG00QPYJ5X0", "SU26238RMFS", "ОФЗ-26238", "bond");
        addFallbackInstrument(instruments, "BBG00QPYJ5X1", "SU26239RMFS", "ОФЗ-26239", "bond");
        addFallbackInstrument(instruments, "BBG00QPYJ5X2", "SU26240RMFS", "ОФЗ-26240", "bond");
        
        // 🟡 ОБЛИГАЦИИ - Корпоративные
        addFallbackInstrument(instruments, "BBG00QPYJ5X3", "RU000A105WX7", "Сбербанк-001Р", "bond");
        addFallbackInstrument(instruments, "BBG00QPYJ5X4", "RU000A105WX8", "Газпром-001Р", "bond");
        
        // 🟣 ETF - Основные фонды
        addFallbackInstrument(instruments, "BBG00QPYJ5X5", "FXRL", "FinEx MSCI Russia", "etf");
        addFallbackInstrument(instruments, "BBG00QPYJ5X6", "FXUS", "FinEx USA", "etf");
        addFallbackInstrument(instruments, "BBG00QPYJ5X7", "FXDE", "FinEx Germany", "etf");
        addFallbackInstrument(instruments, "BBG00QPYJ5X8", "FXCN", "FinEx China", "etf");
        addFallbackInstrument(instruments, "BBG00QPYJ5X9", "FXGD", "FinEx Gold", "etf");
        
        // 💰 ВАЛЮТЫ
        addFallbackInstrument(instruments, "BBG00QPYJ5Y0", "USD000UTSTOM", "Доллар США", "currency");
        addFallbackInstrument(instruments, "BBG00QPYJ5Y1", "EUR_RUB__TOM", "Евро", "currency");
        
        // Фильтруем заблокированные по ликвидности инструменты (как в быстром и полном анализе)
        instruments = instruments.stream()
            .filter(instrument -> {
                if (portfolioManagementService.isLiquidityBlocked(instrument.getFigi())) {
                    long minutesLeft = portfolioManagementService.getLiquidityBlockRemainingMinutes(instrument.getFigi());
                    log.debug("Пропускаем {} из резервного списка - заблокирован по ликвидности (осталось ~{} мин)", 
                        instrument.getFigi(), minutesLeft);
                    return false;
                }
                return true;
            })
            .collect(Collectors.toList());
        
        log.info("Используется улучшенный резервный список из {} инструментов (заблокированные по ликвидности исключены)", instruments.size());
        log.warn("⚠️ Резервный список активирован! Проверьте подключение к API Tinkoff");
        
        return instruments;
    }
    
    /**
     * Вспомогательный метод для добавления инструмента в резервный список
     */
    private void addFallbackInstrument(List<ShareDto> instruments, String figi, String ticker, 
                                     String name, String instrumentType) {
        ShareDto instrument = new ShareDto();
        instrument.setFigi(figi);
        instrument.setTicker(ticker);
        instrument.setName(name);
        instrument.setCurrency("RUB");
        instrument.setExchange("MOEX");
        instrument.setTradingStatus("SECURITY_TRADING_STATUS_NORMAL_TRADING");
        instrument.setInstrumentType(instrumentType);
        instruments.add(instrument);
    }
    
    /**
     * Статистика анализа
     */
    public Map<String, Object> getAnalysisStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalInstruments", dynamicInstrumentService.getAvailableInstruments().size());
        stats.put("rotationIndex", rotationIndex);
        stats.put("rotationBatchSize", ROTATION_BATCH_SIZE);
        stats.put("quickAnalysisLimit", QUICK_ANALYSIS_LIMIT);
        stats.put("fullAnalysisLimit", FULL_ANALYSIS_LIMIT);
        stats.put("prioritizedInstruments", instrumentPriorities.size());
        stats.put("fallbackInstrumentsCount", 20); // Количество в резервном списке
        return stats;
    }
    
    /**
     * Проверка состояния резервного режима
     */
    public boolean isInFallbackMode() {
        List<ShareDto> availableInstruments = dynamicInstrumentService.getAvailableInstruments();
        return availableInstruments.isEmpty();
    }
    
    /**
     * Получение информации о резервном режиме
     */
    public Map<String, Object> getFallbackModeInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("isInFallbackMode", isInFallbackMode());
        info.put("availableInstrumentsCount", dynamicInstrumentService.getAvailableInstruments().size());
        info.put("fallbackInstrumentsCount", 20);
        info.put("recommendation", isInFallbackMode() ? 
            "Проверьте подключение к API Tinkoff" : "Система работает в нормальном режиме");
        return info;
    }
}





