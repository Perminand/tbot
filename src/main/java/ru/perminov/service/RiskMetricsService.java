package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.perminov.repository.TradingSettingsRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RiskMetricsService {

    @SuppressWarnings("unused")
    private final PortfolioService portfolioService;
    private final PortfolioManagementService portfolioManagementService;
    private final TradingSettingsRepository settingsRepository;
    @SuppressWarnings("unused")
    private final MarginService marginService;

    private static final String KEY_BASELINE_VALUE = "risk_baseline_value";
    private static final String KEY_BASELINE_DATE = "risk_baseline_date";

    public Map<String, Object> getMetrics(String accountId) {
        Map<String, Object> m = new HashMap<>();

        PortfolioManagementService.PortfolioAnalysis analysis = portfolioManagementService.analyzePortfolio(accountId);
        
        // Рассчитываем стоимость портфеля из позиций напрямую
        BigDecimal portfolioValue = analysis.getPositions().stream()
                .map(p -> {
                    BigDecimal quantity = p.getQuantity();
                    BigDecimal currentPrice = BigDecimal.ZERO;
                    
                    if (p.getCurrentPrice() != null) {
                        try {
                            // Пробуем использовать правильный метод для Money
                            if (p.getCurrentPrice() instanceof ru.tinkoff.piapi.core.models.Money) {
                                ru.tinkoff.piapi.core.models.Money money = (ru.tinkoff.piapi.core.models.Money) p.getCurrentPrice();
                                currentPrice = money.getValue();
                                log.debug("Цена для {} через getValue(): {}", p.getFigi(), currentPrice);
                            } else {
                                // Фоллбек на парсинг строки
                                String priceStr = p.getCurrentPrice().toString();
                                log.debug("Парсинг цены для {}: {}", p.getFigi(), priceStr);
                                
                                if (priceStr.contains("value=")) {
                                    String valuePart = priceStr.substring(priceStr.indexOf("value=") + 6);
                                    valuePart = valuePart.substring(0, valuePart.indexOf(","));
                                    currentPrice = new BigDecimal(valuePart);
                                    log.debug("Найдена цена через value=: {}", currentPrice);
                                } else {
                                    String[] parts = priceStr.split("[^0-9.]");
                                    for (String part : parts) {
                                        if (!part.isEmpty() && part.matches("\\d+\\.?\\d*")) {
                                            currentPrice = new BigDecimal(part);
                                            log.debug("Найдена цена через парсинг: {}", currentPrice);
                                            break;
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.warn("Не удалось получить цену для позиции {}: {}", p.getFigi(), e.getMessage());
                            currentPrice = BigDecimal.ZERO;
                        }
                    }
                    
                    BigDecimal positionValue;
                    // Для валютных позиций используем количество как стоимость
                    if ("currency".equals(p.getInstrumentType())) {
                        positionValue = quantity;
                    } else {
                        positionValue = quantity.multiply(currentPrice);
                    }
                    
                    log.debug("Позиция {}: количество={}, цена={}, стоимость={}", 
                        p.getFigi(), quantity, currentPrice, positionValue);
                    
                    return positionValue;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        log.info("Портфель {}: рассчитанная стоимость из позиций = {}", accountId, portfolioValue);

        // Baseline init/reset per day
        String today = LocalDate.now().toString();
        String storedDate = settingsRepository.findByKey(KEY_BASELINE_DATE).map(s -> s.getValue()).orElse(null);
        BigDecimal baseline;
        if (storedDate == null || !storedDate.equals(today)) {
            baseline = portfolioValue;
            upsert(KEY_BASELINE_VALUE, portfolioValue.toPlainString(), "Базовая стоимость портфеля на начало дня");
            upsert(KEY_BASELINE_DATE, today, "Дата базовой стоимости");
        } else {
            baseline = settingsRepository.findByKey(KEY_BASELINE_VALUE)
                    .map(s -> safeBig(s.getValue())).orElse(portfolioValue);
        }

        // Санити-проверка: если baseline за сегодня и отличается от текущей стоимости > 5x — сбросим baseline
        if (today.equals(storedDate)) {
            BigDecimal ratio = safeRatio(portfolioValue, baseline);
            if (ratio.compareTo(new BigDecimal("5")) > 0 || ratio.compareTo(new BigDecimal("0.2")) < 0) {
                baseline = portfolioValue;
                upsert(KEY_BASELINE_VALUE, portfolioValue.toPlainString(), "Автокоррекция базовой стоимости");
            }
        }

        BigDecimal dailyPnL = portfolioValue.subtract(baseline);
        BigDecimal drawdownPct = baseline.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO :
                dailyPnL.divide(baseline, 6, RoundingMode.HALF_UP);

        // Примерная оценка плеча: для обычного портфеля = 1.0
        BigDecimal leverage = BigDecimal.ONE;
        
        // Если есть отрицательные позиции (шорт), считаем плечо
        BigDecimal shortExposure = analysis.getPositions().stream()
                .filter(p -> p.getQuantity().compareTo(BigDecimal.ZERO) < 0)
                .map(p -> {
                    BigDecimal quantity = p.getQuantity().abs();
                    BigDecimal currentPrice = BigDecimal.ZERO;
                    
                    if (p.getCurrentPrice() != null) {
                        try {
                            // Пробуем использовать правильный метод для Money
                            if (p.getCurrentPrice() instanceof ru.tinkoff.piapi.core.models.Money) {
                                ru.tinkoff.piapi.core.models.Money money = (ru.tinkoff.piapi.core.models.Money) p.getCurrentPrice();
                                currentPrice = money.getValue();
                            } else {
                                // Фоллбек на парсинг строки
                                String priceStr = p.getCurrentPrice().toString();
                                if (priceStr.contains("value=")) {
                                    String valuePart = priceStr.substring(priceStr.indexOf("value=") + 6);
                                    valuePart = valuePart.substring(0, valuePart.indexOf(","));
                                    currentPrice = new BigDecimal(valuePart);
                                } else {
                                    String[] parts = priceStr.split("[^0-9.]");
                                    for (String part : parts) {
                                        if (!part.isEmpty() && part.matches("\\d+\\.?\\d*")) {
                                            currentPrice = new BigDecimal(part);
                                            break;
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.warn("Не удалось получить цену для позиции {}: {}", p.getFigi(), e.getMessage());
                        }
                    }
                    
                    return quantity.multiply(currentPrice);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Для простого портфеля без маржи плечо = 1.0
        // Если есть шорт позиции, добавляем их к плечу
        if (shortExposure.compareTo(BigDecimal.ZERO) > 0 && portfolioValue.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal shortLeverage = shortExposure.divide(portfolioValue, 4, RoundingMode.HALF_UP);
            leverage = BigDecimal.ONE.add(shortLeverage);
            log.debug("Плечо: шорт={}, портфель={}, плечо={}", shortExposure, portfolioValue, leverage);
        }
        
        // Ограничиваем плечо разумными пределами
        if (leverage.compareTo(new BigDecimal("5")) > 0) {
            leverage = new BigDecimal("5");
        }

        m.put("portfolioValue", portfolioValue);
        m.put("baseline", baseline);
        m.put("dailyPnL", dailyPnL);
        m.put("dailyDrawdownPct", drawdownPct);
        m.put("leverage", leverage);
        return m;
    }

    private void upsert(String key, String value, String description) {
        var s = settingsRepository.findByKey(key).orElseGet(ru.perminov.model.TradingSettings::new);
        s.setKey(key);
        s.setValue(value);
        s.setDescription(description);
        settingsRepository.save(s);
    }

    private BigDecimal safeBig(String s) {
        try { return new BigDecimal(s); } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private BigDecimal anyMoneyToBig(Object o) {
        try {
            if (o == null) return BigDecimal.ZERO;
            // Если это MoneyValue, используем правильный метод
            if (o instanceof ru.tinkoff.piapi.contract.v1.MoneyValue) {
                ru.tinkoff.piapi.contract.v1.MoneyValue m = (ru.tinkoff.piapi.contract.v1.MoneyValue) o;
                return BigDecimal.valueOf(m.getUnits()).add(BigDecimal.valueOf(m.getNano(), 9));
            }
            // Фоллбек на парсинг строки
            String s = o.toString();
            if (s.contains("units=") && s.contains("nano=")) {
                String us = s.substring(s.indexOf("units=") + 6);
                us = us.substring(0, us.indexOf(','));
                String ns = s.substring(s.indexOf("nano=") + 5);
                ns = ns.replaceAll("[^0-9]", "");
                long units = Long.parseLong(us.trim());
                int nano = ns.isEmpty() ? 0 : Integer.parseInt(ns);
                return BigDecimal.valueOf(units).add(BigDecimal.valueOf(nano, 9));
            }
            if (s.contains("value=")) {
                String part = s.substring(s.indexOf("value=") + 6);
                part = part.substring(0, part.indexOf(','));
                return new BigDecimal(part);
            }
            String[] parts = s.split("[^0-9.]");
            for (String part : parts) {
                if (!part.isEmpty() && part.matches("\\d+\\.?\\d*")) {
                    return new BigDecimal(part);
                }
            }
        } catch (Exception ignore) {}
        return BigDecimal.ZERO;
    }

    private BigDecimal safeRatio(BigDecimal a, BigDecimal b) {
        if (a == null || b == null || b.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ONE;
        try { return a.divide(b, 6, RoundingMode.HALF_UP); } catch (Exception e) { return BigDecimal.ONE; }
    }
}


