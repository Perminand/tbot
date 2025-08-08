package ru.perminov.dto;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class PortfolioDto {
    private String accountId;
    private List<PositionDto> positions;
    private String message;
    
    // Добавляем поля для сумм по типам инструментов
    private AmountDto totalAmountShares;
    private AmountDto totalAmountBonds;
    private AmountDto totalAmountEtfs;
    private AmountDto totalAmountCurrencies;
    
    @Data
    public static class AmountDto {
        private BigDecimal value;
        private String displayValue;
        private String currency;
        
        public AmountDto(BigDecimal value, String currency) {
            this.value = value;
            this.currency = currency;
            this.displayValue = "₽" + value.setScale(2, BigDecimal.ROUND_HALF_UP);
        }
    }
    
    @Data
    public static class PositionDto {
        private String figi;
        private String ticker;
        private String name;
        private String instrumentType;
        private BigDecimal quantity;
        private String currency;
        private String displayValue;
        private BigDecimal currentPrice;
        private BigDecimal averagePrice;
        private BigDecimal accumulatedCouponYield;
        private BigDecimal yield;
        
        // Добавляем форматированные поля для отображения
        private String currentPriceDisplay;
        private String averagePriceDisplay;
        private String accumulatedCouponYieldDisplay;
        private String yieldDisplay;
        
        public static PositionDto from(ru.tinkoff.piapi.core.models.Position position) {
            PositionDto dto = new PositionDto();
            dto.setFigi(position.getFigi());
            dto.setInstrumentType(position.getInstrumentType());
            dto.setQuantity(position.getQuantity());
            
            // Упрощенная обработка currentPrice
            BigDecimal currentPrice = extractPriceFromMoney(position.getCurrentPrice());
            dto.setCurrentPrice(currentPrice);
            System.out.println("DEBUG: FIGI=" + position.getFigi() + ", CurrentPrice=" + currentPrice);
            
            // Упрощенная обработка averagePrice
            BigDecimal averagePrice = extractPriceFromMoney(position.getAveragePositionPrice());
            dto.setAveragePrice(averagePrice);
            System.out.println("DEBUG: FIGI=" + position.getFigi() + ", AveragePrice=" + averagePrice);
            
            // Обработка accumulatedCouponYield (НКД) и yield (доходность)
            dto.setAccumulatedCouponYield(BigDecimal.ZERO); // НКД пока не доступен из позиции
            
            // Рассчитываем доходность для всех инструментов
            try {
                if (dto.getCurrentPrice() != null && dto.getAveragePrice() != null && 
                    dto.getAveragePrice().compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal yield = dto.getCurrentPrice()
                        .subtract(dto.getAveragePrice())
                        .divide(dto.getAveragePrice(), 4, BigDecimal.ROUND_HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                    dto.setYield(yield);
                    } else {
                    dto.setYield(BigDecimal.ZERO);
                }
            } catch (Exception e) {
                dto.setYield(BigDecimal.ZERO);
            }
            
            // Устанавливаем форматированные значения для отображения
            dto.setCurrentPriceDisplay(formatPrice(dto.getCurrentPrice()));
            dto.setAveragePriceDisplay(formatPrice(dto.getAveragePrice()));
            dto.setAccumulatedCouponYieldDisplay(formatPrice(dto.getAccumulatedCouponYield()));
            dto.setYieldDisplay(formatYield(dto.getYield()));
            
            // Для валютных позиций добавляем специальную обработку
            if ("currency".equals(position.getInstrumentType())) {
                dto.setTicker("RUB");
                dto.setName("Российский рубль");
                dto.setCurrency("rub");
                dto.setCurrentPrice(BigDecimal.ONE);
                dto.setAveragePrice(BigDecimal.ONE);
                dto.setDisplayValue("₽" + position.getQuantity().setScale(2, BigDecimal.ROUND_HALF_UP));
            } else {
                // Для других инструментов пытаемся получить более точную информацию
                try {
                    // Пытаемся извлечь тикер из FIGI (обычно первые символы)
                    String figi = position.getFigi();
                    if (figi != null && figi.length() > 0) {
                        // Для разных типов инструментов разные префиксы
                        if (figi.startsWith("TCS")) {
                            dto.setTicker("TCS");
                            dto.setName("Тинькофф Банк");
                        } else if (figi.startsWith("BBG")) {
                            // Для BBG кодов пытаемся получить правильный тикер
                            String ticker = getTickerFromFigi(figi);
                            dto.setTicker(ticker);
                            // Пытаемся получить реальное название инструмента
                            String realName = getInstrumentRealName(figi, position.getInstrumentType());
                            if (realName != null && !realName.isEmpty()) {
                                dto.setName(realName);
                            } else {
                                dto.setName(getInstrumentTypeDisplayName(position.getInstrumentType()) + " " + dto.getTicker());
                            }
                        } else {
                            dto.setTicker(figi.substring(0, Math.min(8, figi.length())));
                            // Пытаемся получить реальное название инструмента
                            String realName = getInstrumentRealName(figi, position.getInstrumentType());
                            if (realName != null && !realName.isEmpty()) {
                                dto.setName(realName);
                    } else {
                                dto.setName(getInstrumentTypeDisplayName(position.getInstrumentType()) + " " + dto.getTicker());
                            }
                        }
                    } else {
                        dto.setTicker("N/A");
                        dto.setName(getInstrumentTypeDisplayName(position.getInstrumentType()));
                    }
                } catch (Exception e) {
                    dto.setTicker("N/A");
                    dto.setName(getInstrumentTypeDisplayName(position.getInstrumentType()));
                }
                dto.setCurrency("rub");
                
                // Расчет отображаемой стоимости
                if (dto.getCurrentPrice() != null && position.getQuantity() != null) {
                    BigDecimal totalValue = dto.getCurrentPrice().multiply(position.getQuantity());
                    dto.setDisplayValue("₽" + totalValue.setScale(2, BigDecimal.ROUND_HALF_UP));
                } else if (position.getQuantity() != null) {
                    dto.setDisplayValue("₽" + position.getQuantity().setScale(2, BigDecimal.ROUND_HALF_UP));
            } else {
                    dto.setDisplayValue("N/A");
                }
            }
            
            return dto;
        }
        
        public static PositionDto fromWithNames(ru.tinkoff.piapi.core.models.Position position, 
                                              ru.perminov.service.InstrumentNameService nameService) {
            PositionDto dto = new PositionDto();
            dto.setFigi(position.getFigi());
            dto.setInstrumentType(position.getInstrumentType());
            dto.setQuantity(position.getQuantity());
            
            // Упрощенная обработка currentPrice
            BigDecimal currentPrice = extractPriceFromMoney(position.getCurrentPrice());
            dto.setCurrentPrice(currentPrice);
            
            // Упрощенная обработка averagePrice
            BigDecimal averagePrice = extractPriceFromMoney(position.getAveragePositionPrice());
            dto.setAveragePrice(averagePrice);
            
            // Обработка accumulatedCouponYield (НКД) и yield (доходность)
            dto.setAccumulatedCouponYield(BigDecimal.ZERO); // НКД пока не доступен из позиции
            
            // Рассчитываем доходность для всех инструментов
            try {
                if (dto.getCurrentPrice() != null && dto.getAveragePrice() != null && 
                    dto.getAveragePrice().compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal yield = dto.getCurrentPrice()
                        .subtract(dto.getAveragePrice())
                        .divide(dto.getAveragePrice(), 4, BigDecimal.ROUND_HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                    dto.setYield(yield);
                } else {
                    dto.setYield(BigDecimal.ZERO);
                }
            } catch (Exception e) {
            dto.setYield(BigDecimal.ZERO);
            }
            
            // Устанавливаем форматированные значения для отображения
            dto.setCurrentPriceDisplay(formatPrice(dto.getCurrentPrice()));
            dto.setAveragePriceDisplay(formatPrice(dto.getAveragePrice()));
            dto.setAccumulatedCouponYieldDisplay(formatPrice(dto.getAccumulatedCouponYield()));
            dto.setYieldDisplay(formatYield(dto.getYield()));
            
            // Для валютных позиций добавляем специальную обработку
            if ("currency".equals(position.getInstrumentType())) {
                dto.setTicker("RUB");
                dto.setName("Российский рубль");
                dto.setCurrency("rub");
                dto.setCurrentPrice(BigDecimal.ONE);
                dto.setAveragePrice(BigDecimal.ONE);
                dto.setDisplayValue("₽" + position.getQuantity().setScale(2, BigDecimal.ROUND_HALF_UP));
            } else {
                // Используем сервис для получения реальных названий и тикеров
                String ticker = nameService.getTicker(position.getFigi(), position.getInstrumentType());
                String name = nameService.getInstrumentName(position.getFigi(), position.getInstrumentType());
                
                dto.setTicker(ticker != null ? ticker : "N/A");
                dto.setName(name != null ? name : getInstrumentTypeDisplayName(position.getInstrumentType()));
                dto.setCurrency("rub");
                
                // Расчет отображаемой стоимости
                if (dto.getCurrentPrice() != null && position.getQuantity() != null) {
                    BigDecimal totalValue = dto.getCurrentPrice().multiply(position.getQuantity());
                    dto.setDisplayValue("₽" + totalValue.setScale(2, BigDecimal.ROUND_HALF_UP));
                } else if (position.getQuantity() != null) {
                    dto.setDisplayValue("₽" + position.getQuantity().setScale(2, BigDecimal.ROUND_HALF_UP));
                } else {
                    dto.setDisplayValue("N/A");
                }
            }
            
            return dto;
        }
        
        private static BigDecimal extractPriceFromMoney(ru.tinkoff.piapi.core.models.Money money) {
            if (money == null) {
                return BigDecimal.ZERO;
            }
            
            try {
                // Пытаемся получить цену через getValue()
                Object value = money.getValue();
                if (value instanceof BigDecimal) {
                    return (BigDecimal) value;
                } else if (value instanceof String) {
                    return new BigDecimal((String) value);
                } else {
                    return new BigDecimal(value.toString());
                }
            } catch (Exception e) {
                try {
                    // Если не получилось, пробуем парсить из строки
                    String moneyStr = money.toString();
                    System.out.println("DEBUG: Money string: " + moneyStr); // Отладочный вывод
                    if (moneyStr.contains("value=")) {
                        String valuePart = moneyStr.substring(moneyStr.indexOf("value=") + 6);
                        valuePart = valuePart.substring(0, valuePart.indexOf(","));
                        System.out.println("DEBUG: Extracted value: " + valuePart); // Отладочный вывод
                        return new BigDecimal(valuePart);
                    }
                } catch (Exception ex) {
                    System.out.println("DEBUG: Failed to parse money: " + ex.getMessage()); // Отладочный вывод
                    // Если все не получилось, возвращаем 0
                    return BigDecimal.ZERO;
                }
            }
            return BigDecimal.ZERO;
        }
        
        private static String getInstrumentTypeDisplayName(String instrumentType) {
            switch (instrumentType) {
                case "share":
                    return "Акция";
                case "bond":
                    return "Облигация";
                case "etf":
                    return "ETF";
                case "currency":
                    return "Валюта";
                default:
                    return "Инструмент (" + instrumentType + ")";
            }
        }
        
        private static String formatPrice(BigDecimal price) {
            if (price == null || price.compareTo(BigDecimal.ZERO) == 0) {
                return "N/A";
            }
            return "₽" + price.setScale(2, BigDecimal.ROUND_HALF_UP);
        }
        
        private static String formatYield(BigDecimal yield) {
            if (yield == null || yield.compareTo(BigDecimal.ZERO) == 0) {
                return "N/A";
            }
            return yield.setScale(2, BigDecimal.ROUND_HALF_UP) + "%";
        }
        
        private static String getInstrumentRealName(String figi, String instrumentType) {
            try {
                // Словарь известных инструментов с их реальными названиями
                switch (figi) {
                    // Акции
                    case "TCS00A106YF0":
                        return "Тинькофф Банк";
                    case "BBG004730N88":
                        return "Сбербанк России";
                    case "BBG0047315Y7":
                        return "Газпром";
                    case "BBG004731354":
                        return "Лукойл";
                    case "BBG004731489":
                        return "Новатэк";
                    case "BBG004731032":
                        return "Роснефть";
                    case "BBG0047315D0":
                        return "Магнит";
                    case "BBG0047312Z9":
                        return "Яндекс";
                    case "BBG0047319J7":
                        return "ВкусВилл";
                    case "BBG0047319J8":
                        return "Ozon";
                        
                    // ETF
                    case "BBG00QPYJ5X5":
                        return "FinEx MSCI Russia";
                    case "BBG00QPYJ5X6":
                        return "FinEx USA";
                    case "BBG00QPYJ5X7":
                        return "FinEx Germany";
                    case "BBG00QPYJ5X8":
                        return "FinEx China";
                    case "BBG00QPYJ5X9":
                        return "FinEx Gold";
                        
                    // Облигации
                    case "BBG00QPYJ5X0":
                        return "ОФЗ-26238";
                    case "BBG00QPYJ5X1":
                        return "ОФЗ-26239";
                    case "BBG00QPYJ5X2":
                        return "ОФЗ-26240";
                    case "BBG00QPYJ5X3":
                        return "Сбербанк-001Р";
                    case "BBG00QPYJ5X4":
                        return "Газпром-001Р";
                        
                    // Валюты
                    case "BBG00QPYJ5Y0":
                        return "Доллар США";
                    case "BBG00QPYJ5Y1":
                        return "Евро";
                        
                    default:
                        // Для неизвестных FIGI пытаемся извлечь название из тикера
                        if (figi.startsWith("BBG") && figi.length() > 7) {
                            String ticker = figi.substring(4, 8);
                            return getInstrumentNameByTicker(ticker, instrumentType);
                        }
                        return null;
                }
            } catch (Exception e) {
                return null;
            }
        }
        
        private static String getTickerFromFigi(String figi) {
            // Словарь FIGI -> тикер
            switch (figi) {
                // Акции
                case "TCS00A106YF0":
                    return "TCS";
                case "BBG004730N88":
                    return "SBER";
                case "BBG0047315Y7":
                    return "GAZP";
                case "BBG004731354":
                    return "LKOH";
                case "BBG004731489":
                    return "NVTK";
                case "BBG004731032":
                    return "ROSN";
                case "BBG0047315D0":
                    return "MGNT";
                case "BBG0047312Z9":
                    return "YNDX";
                case "BBG0047319J7":
                    return "VKUS";
                case "BBG0047319J8":
                    return "OZON";
                    
                // ETF
                case "BBG00QPYJ5X5":
                    return "FXRL";
                case "BBG00QPYJ5X6":
                    return "FXUS";
                case "BBG00QPYJ5X7":
                    return "FXDE";
                case "BBG00QPYJ5X8":
                    return "FXCN";
                case "BBG00QPYJ5X9":
                    return "FXGD";
                    
                // Облигации
                case "BBG00QPYJ5X0":
                    return "SU26238RMFS";
                case "BBG00QPYJ5X1":
                    return "SU26239RMFS";
                case "BBG00QPYJ5X2":
                    return "SU26240RMFS";
                case "BBG00QPYJ5X3":
                    return "RU000A105WX7";
                case "BBG00QPYJ5X4":
                    return "RU000A105WX8";
                    
                // Валюты
                case "BBG00QPYJ5Y0":
                    return "USD000UTSTOM";
                case "BBG00QPYJ5Y1":
                    return "EUR_RUB__TOM";
                    
                default:
                    // Для неизвестных FIGI пытаемся извлечь тикер из кода
                    if (figi.startsWith("BBG") && figi.length() > 7) {
                        return figi.substring(4, 8);
                    }
                    return figi.substring(0, Math.min(8, figi.length()));
            }
        }
        
        private static String getInstrumentNameByTicker(String ticker, String instrumentType) {
            // Словарь названий по тикерам
            switch (ticker) {
                // ETF тикеры
                case "1CCN":
                    return "Сбербанк - Консервативный";
                case "1HLF":
                    return "Сбербанк - Сбалансированный";
                case "14M8":
                    return "Сбербанк - Активный";
                case "19HZ":
                    return "Сбербанк - Золото";
                    
                // Акции
                case "TCS":
                    return "Тинькофф Банк";
                case "SBER":
                    return "Сбербанк России";
                case "GAZP":
                    return "Газпром";
                case "LKOH":
                    return "Лукойл";
                case "NVTK":
                    return "Новатэк";
                case "ROSN":
                    return "Роснефть";
                case "MGNT":
                    return "Магнит";
                case "YNDX":
                    return "Яндекс";
                case "VKUS":
                    return "ВкусВилл";
                case "OZON":
                    return "Ozon";
                    
                default:
                    return null;
            }
        }
    }
    
    public static PortfolioDto from(ru.tinkoff.piapi.core.models.Portfolio portfolio) {
        PortfolioDto dto = new PortfolioDto();
        dto.setMessage("Portfolio loaded successfully");
        
        if (portfolio.getPositions() != null) {
            dto.setPositions(portfolio.getPositions().stream()
                    .map(PositionDto::from)
                    .collect(Collectors.toList()));
            
            // Рассчитываем суммы по типам инструментов
            calculateAmountsByType(dto);
        }
        
        return dto;
    }
    
    /**
     * Создание PortfolioDto с использованием сервиса названий инструментов
     */
    public static PortfolioDto fromWithNames(ru.tinkoff.piapi.core.models.Portfolio portfolio, 
                                           ru.perminov.service.InstrumentNameService nameService) {
        PortfolioDto dto = new PortfolioDto();
        dto.setMessage("Portfolio loaded successfully");
        
        if (portfolio.getPositions() != null) {
            dto.setPositions(portfolio.getPositions().stream()
                    .map(position -> PositionDto.fromWithNames(position, nameService))
                    .collect(Collectors.toList()));
            
            // Рассчитываем суммы по типам инструментов
            calculateAmountsByType(dto);
        }
        
        return dto;
    }
    
    private static void calculateAmountsByType(PortfolioDto dto) {
        BigDecimal sharesTotal = BigDecimal.ZERO;
        BigDecimal bondsTotal = BigDecimal.ZERO;
        BigDecimal etfsTotal = BigDecimal.ZERO;
        BigDecimal currenciesTotal = BigDecimal.ZERO;
        
        for (PositionDto position : dto.getPositions()) {
            BigDecimal positionValue = BigDecimal.ZERO;
            
            // Рассчитываем стоимость позиции
            if (position.getCurrentPrice() != null && position.getQuantity() != null) {
                positionValue = position.getCurrentPrice().multiply(position.getQuantity());
            } else if (position.getQuantity() != null) {
                positionValue = position.getQuantity();
            }
            
            // Группируем по типам инструментов
            switch (position.getInstrumentType()) {
                case "share":
                    sharesTotal = sharesTotal.add(positionValue);
                    break;
                case "bond":
                    bondsTotal = bondsTotal.add(positionValue);
                    break;
                case "etf":
                    etfsTotal = etfsTotal.add(positionValue);
                    break;
                case "currency":
                    currenciesTotal = currenciesTotal.add(positionValue);
                    break;
                default:
                    // Для неизвестных типов добавляем к акциям
                    sharesTotal = sharesTotal.add(positionValue);
                    break;
            }
        }
        
        // Устанавливаем суммы по типам
        dto.setTotalAmountShares(new AmountDto(sharesTotal, "RUB"));
        dto.setTotalAmountBonds(new AmountDto(bondsTotal, "RUB"));
        dto.setTotalAmountEtfs(new AmountDto(etfsTotal, "RUB"));
        dto.setTotalAmountCurrencies(new AmountDto(currenciesTotal, "RUB"));
    }
} 