package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.core.InvestApi;

@Service
@RequiredArgsConstructor
public class TradingModeService {
    
    private final InvestApi investApi;
    
    @Value("${tinkoff.api.use-sandbox:true}")
    private boolean useSandbox;
    
    public String getCurrentMode() {
        return useSandbox ? "sandbox" : "production";
    }
    
    public boolean isSandboxMode() {
        return useSandbox;
    }
    
    public boolean isProductionMode() {
        return !useSandbox;
    }
    
    public String getModeDisplayName() {
        return isSandboxMode() ? "Песочница" : "Реальная торговля";
    }
    
    public String getModeBadgeClass() {
        return isSandboxMode() ? "bg-warning" : "bg-danger";
    }
} 