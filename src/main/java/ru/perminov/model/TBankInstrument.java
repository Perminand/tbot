package ru.perminov.model;

import lombok.Data;

@Data
public class TBankInstrument {
    private String figi;
    private String ticker;
    private String name;
    private String type;
    // ... другие поля по документации T-Банк
} 