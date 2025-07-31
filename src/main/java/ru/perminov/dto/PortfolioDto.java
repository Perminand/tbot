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
        
        public static PositionDto from(ru.tinkoff.piapi.core.models.Position position) {
            PositionDto dto = new PositionDto();
            dto.setFigi(position.getFigi());
            dto.setInstrumentType(position.getInstrumentType());
            dto.setQuantity(position.getQuantity());
            
            // Для валютных позиций добавляем специальную обработку
            if ("currency".equals(position.getInstrumentType())) {
                dto.setTicker("RUB");
                dto.setName("Российский рубль");
                dto.setCurrency("rub");
                dto.setDisplayValue("₽" + position.getQuantity().setScale(2, BigDecimal.ROUND_HALF_UP));
            } else {
                dto.setTicker("N/A");
                dto.setName("N/A");
                dto.setCurrency("N/A");
                dto.setDisplayValue("N/A");
            }
            
            return dto;
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