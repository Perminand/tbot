package ru.perminov.dto;

import lombok.Data;
import ru.tinkoff.piapi.contract.v1.PostOrderResponse;

@Data
public class OrderResponseDto {
    private String orderId;
    private String executionReportStatus;
    private String message;
    private long lotsRequested;
    private long lotsExecuted;
    private String initialOrderPrice;
    private String executedOrderPrice;
    private String totalOrderAmount;
    private String initialCommission;
    private String executedCommission;
    private String aciValue;
    private String figi;
    private String direction;
    private String initialSecurityPrice;
    private String orderType;

    public static OrderResponseDto from(PostOrderResponse response) {
        OrderResponseDto dto = new OrderResponseDto();
        dto.setOrderId(response.getOrderId());
        dto.setExecutionReportStatus(response.getExecutionReportStatus().name());
        dto.setMessage(response.getMessage());
        dto.setLotsRequested(response.getLotsRequested());
        dto.setLotsExecuted(response.getLotsExecuted());
        
        if (response.hasInitialOrderPrice()) {
            dto.setInitialOrderPrice(response.getInitialOrderPrice().getUnits() + "." + 
                String.format("%09d", response.getInitialOrderPrice().getNano()));
        }
        
        if (response.hasExecutedOrderPrice()) {
            dto.setExecutedOrderPrice(response.getExecutedOrderPrice().getUnits() + "." + 
                String.format("%09d", response.getExecutedOrderPrice().getNano()));
        }
        
        if (response.hasTotalOrderAmount()) {
            dto.setTotalOrderAmount(response.getTotalOrderAmount().getUnits() + "." + 
                String.format("%09d", response.getTotalOrderAmount().getNano()));
        }
        
        if (response.hasInitialCommission()) {
            dto.setInitialCommission(response.getInitialCommission().getUnits() + "." + 
                String.format("%09d", response.getInitialCommission().getNano()));
        }
        
        if (response.hasExecutedCommission()) {
            dto.setExecutedCommission(response.getExecutedCommission().getUnits() + "." + 
                String.format("%09d", response.getExecutedCommission().getNano()));
        }
        
        if (response.hasAciValue()) {
            dto.setAciValue(response.getAciValue().getUnits() + "." + 
                String.format("%09d", response.getAciValue().getNano()));
        }
        
        dto.setFigi(response.getFigi());
        dto.setDirection(response.getDirection().name());
        
        if (response.hasInitialSecurityPrice()) {
            dto.setInitialSecurityPrice(response.getInitialSecurityPrice().getUnits() + "." + 
                String.format("%09d", response.getInitialSecurityPrice().getNano()));
        }
        
        dto.setOrderType(response.getOrderType().name());
        
        return dto;
    }
} 