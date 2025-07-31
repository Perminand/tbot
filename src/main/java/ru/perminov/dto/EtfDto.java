package ru.perminov.dto;

import lombok.Data;

@Data
public class EtfDto {
    private String figi;
    private String ticker;
    private String name;
    private String exchange;
    private String currency;
    private String tradingStatus;
    private String sector;
    private String countryOfRisk;
    private String countryOfRiskName;

    public static EtfDto from(ru.tinkoff.piapi.contract.v1.Etf etf) {
        EtfDto dto = new EtfDto();
        dto.setFigi(etf.getFigi());
        dto.setTicker(etf.getTicker());
        dto.setName(etf.getName());
        dto.setExchange(etf.getExchange());
        dto.setCurrency(etf.getCurrency());
        dto.setTradingStatus(etf.getTradingStatus().name());
        dto.setSector(etf.getSector());
        dto.setCountryOfRisk(etf.getCountryOfRisk());
        dto.setCountryOfRiskName(etf.getCountryOfRiskName());
        return dto;
    }
} 