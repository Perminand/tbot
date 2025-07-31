package ru.perminov.model;

import lombok.Data;
import jakarta.persistence.*;

@Data
@Entity
@Table(name = "instruments")
public class Instrument {
    
    @Id
    private String figi;
    
    private String ticker;
    private String isin;
    private String name;
    private String currency;
    private String exchange;
    private String sector;
    private String countryOfRisk;
    private String countryOfRiskName;
    private String instrumentType;
    private String instrumentKind;
    private String shareType;
    private String nominal;
    private String nominalCurrency;
    private String tradingStatus;
    private String otcFlag;
    private String buyAvailableFlag;
    private String sellAvailableFlag;
    private String minPriceIncrement;
    private String apiTradeAvailableFlag;
    private String uid;
    private String realExchange;
    private String positionUid;
    private String forIisFlag;
    private String forQualInvestorFlag;
    private String weekendFlag;
    private String blockedTcaFlag;
    private String first1minCandleDate;
    private String first1dayCandleDate;
    private String riskLevel;
} 