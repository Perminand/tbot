package ru.perminov.dto;

import lombok.Data;

@Data
public class ShareDto {
    private String figi;
    private String ticker;
    private String name;
    private String exchange;
    private String currency;
    private String tradingStatus;
    private String sector;
    private String countryOfRisk;
    private String countryOfRiskName;
    
    public static ShareDto from(ru.tinkoff.piapi.contract.v1.Share share) {
        ShareDto dto = new ShareDto();
        dto.setFigi(share.getFigi());
        dto.setTicker(share.getTicker());
        dto.setName(share.getName());
        dto.setExchange(share.getExchange());
        dto.setCurrency(share.getCurrency());
        dto.setTradingStatus(share.getTradingStatus().name());
        dto.setSector(share.getSector());
        dto.setCountryOfRisk(share.getCountryOfRisk());
        dto.setCountryOfRiskName(share.getCountryOfRiskName());
        return dto;
    }
} 