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
        
        public static PositionDto from(ru.tinkoff.piapi.core.models.Position position) {
            PositionDto dto = new PositionDto();
            dto.setFigi(position.getFigi());
            dto.setInstrumentType(position.getInstrumentType());
            dto.setQuantity(position.getQuantity());
            
            // Обработка currentPrice
            if (position.getCurrentPrice() != null) {
                try {
                    // Извлекаем цену из строкового представления объекта
                    String priceStr = position.getCurrentPrice().toString();
                    
                    // Ищем числовое значение в строке
                    if (priceStr.contains("value=")) {
                        String valuePart = priceStr.substring(priceStr.indexOf("value=") + 6);
                        valuePart = valuePart.substring(0, valuePart.indexOf(","));
                        dto.setCurrentPrice(new BigDecimal(valuePart));
                    } else {
                        // Попробуем найти любое число в строке
                        String[] parts = priceStr.split("[^0-9.]");
                        for (String part : parts) {
                            if (!part.isEmpty() && part.matches("\\d+\\.?\\d*")) {
                                dto.setCurrentPrice(new BigDecimal(part));
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    dto.setCurrentPrice(BigDecimal.ZERO);
                }
            } else {
                dto.setCurrentPrice(BigDecimal.ZERO);
            }
            
            // Обработка averagePrice
            if (position.getAveragePositionPrice() != null) {
                try {
                    // Извлекаем цену из строкового представления объекта
                    String avgPriceStr = position.getAveragePositionPrice().toString();
                    
                    // Ищем числовое значение в строке
                    if (avgPriceStr.contains("value=")) {
                        String valuePart = avgPriceStr.substring(avgPriceStr.indexOf("value=") + 6);
                        valuePart = valuePart.substring(0, valuePart.indexOf(","));
                        dto.setAveragePrice(new BigDecimal(valuePart));
                    } else {
                        // Попробуем найти любое число в строке
                        String[] parts = avgPriceStr.split("[^0-9.]");
                        for (String part : parts) {
                            if (!part.isEmpty() && part.matches("\\d+\\.?\\d*")) {
                                dto.setAveragePrice(new BigDecimal(part));
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    dto.setAveragePrice(BigDecimal.ZERO);
                }
            } else {
                dto.setAveragePrice(BigDecimal.ZERO);
            }
            
            // Обработка accumulatedCouponYield (НКД) и yield (доходность)
            // Пока устанавливаем нулевые значения, расчеты будут добавлены позже
            dto.setAccumulatedCouponYield(BigDecimal.ZERO);
            dto.setYield(BigDecimal.ZERO);
            
            // Для валютных позиций добавляем специальную обработку
            if ("currency".equals(position.getInstrumentType())) {
                dto.setTicker("RUB");
                dto.setName("Российский рубль");
                dto.setCurrency("rub");
                // Для валюты используем количество как текущую цену
                dto.setCurrentPrice(position.getQuantity());
                dto.setAveragePrice(position.getQuantity());
                dto.setDisplayValue("₽" + position.getQuantity().setScale(2, BigDecimal.ROUND_HALF_UP));
            } else {
                // Для других инструментов показываем FIGI как тикер и добавляем тип инструмента
                dto.setTicker(position.getFigi().substring(0, Math.min(8, position.getFigi().length())));
                dto.setName(getInstrumentTypeDisplayName(position.getInstrumentType()));
                dto.setCurrency("rub");
                
                // Правильная обработка стоимости - используем currentPrice
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
    }
    
    public static PortfolioDto from(ru.tinkoff.piapi.core.models.Portfolio portfolio) {
        PortfolioDto dto = new PortfolioDto();
        dto.setMessage("Portfolio loaded successfully");
        
        if (portfolio.getPositions() != null) {
            dto.setPositions(portfolio.getPositions().stream()
                    .map(PositionDto::from)
                    .collect(Collectors.toList()));
        }
        
        return dto;
    }
} 