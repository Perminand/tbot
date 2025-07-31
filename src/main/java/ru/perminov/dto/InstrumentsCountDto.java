package ru.perminov.dto;

import lombok.Data;

@Data
public class InstrumentsCountDto {
    private String type;
    private int count;

    public InstrumentsCountDto(String type, int count) {
        this.type = type;
        this.count = count;
    }
} 