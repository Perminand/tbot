package ru.perminov.dto;

import lombok.Data;

@Data
public class BondDto {
    private String figi;
    private String ticker;
    private String name;
    private String exchange;
    private String currency;
    private String tradingStatus;
    private String sector;
    private String countryOfRisk;
    private String countryOfRiskName;

    public static BondDto from(ru.tinkoff.piapi.contract.v1.Bond bond) {
        BondDto dto = new BondDto();
        dto.setFigi(bond.getFigi());
        dto.setTicker(bond.getTicker());
        dto.setName(bond.getName());
        dto.setExchange(bond.getExchange());
        dto.setCurrency(bond.getCurrency());
        dto.setTradingStatus(bond.getTradingStatus().name());
        dto.setSector(bond.getSector());
        dto.setCountryOfRisk(bond.getCountryOfRisk());
        dto.setCountryOfRiskName(bond.getCountryOfRiskName());
        return dto;
    }
} 