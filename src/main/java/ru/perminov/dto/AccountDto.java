package ru.perminov.dto;

import lombok.Data;

@Data
public class AccountDto {
    private String id;
    private String name;
    private String type;
    private String status;
    
    public static AccountDto from(ru.tinkoff.piapi.contract.v1.Account account) {
        AccountDto dto = new AccountDto();
        dto.setId(account.getId());
        dto.setName(account.getName());
        dto.setType(account.getType().name());
        dto.setStatus(account.getStatus().name());
        return dto;
    }
} 