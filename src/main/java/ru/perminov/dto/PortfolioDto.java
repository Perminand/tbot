package ru.perminov.dto;

import lombok.Data;
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
        // Убираем проблемные поля
        // private BigDecimal averagePrice;
        // private BigDecimal accumulatedCouponYield;
        // private BigDecimal yield;
        
        public static PositionDto from(ru.tinkoff.piapi.core.models.Position position) {
            PositionDto dto = new PositionDto();
            dto.setFigi(position.getFigi());
            dto.setInstrumentType(position.getInstrumentType());
            dto.setQuantity(position.getQuantity());
            
            // Упрощенная обработка currentPrice
            if (position.getCurrentPrice() != null) {
                try {
                    // Пытаемся получить значение как строку и конвертировать
                    String priceStr = position.getCurrentPrice().toString();
                    dto.setCurrentPrice(new BigDecimal(priceStr));
                } catch (Exception e) {
                    dto.setCurrentPrice(BigDecimal.ZERO);
                }
            }
            
            // Для валютных позиций добавляем специальную обработку
            if ("currency".equals(position.getInstrumentType())) {
                dto.setTicker("RUB");
                dto.setName("Российский рубль");
                dto.setCurrency("rub");
                dto.setDisplayValue("₽" + position.getQuantity().setScale(2, BigDecimal.ROUND_HALF_UP));
            } else {
                // Для других инструментов показываем FIGI как тикер и добавляем тип инструмента
                dto.setTicker(position.getFigi().substring(0, Math.min(8, position.getFigi().length())));
                dto.setName(getInstrumentTypeDisplayName(position.getInstrumentType()));
                dto.setCurrency("rub");
                
                // Упрощенная обработка стоимости
                if (position.getQuantity() != null) {
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