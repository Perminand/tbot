package ru.perminov.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.perminov.service.PortfolioService;
import ru.perminov.service.InvestApiManager;
import ru.perminov.service.InstrumentNameService;
import ru.perminov.dto.PortfolioDto;
import ru.tinkoff.piapi.core.models.Portfolio;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
@Slf4j
public class PortfolioController {
    private final PortfolioService portfolioService;
    private final InvestApiManager investApiManager;

    @GetMapping
    public ResponseEntity<?> getPortfolio(@RequestParam("accountId") String accountId) {
        try {
            log.info("Getting portfolio for accountId: {}", accountId);
            
            // Автоматически определяем режим по типу счета
            String currentMode = investApiManager.getCurrentMode();
            log.info("Current mode: {}", currentMode);
            
            Portfolio portfolio = portfolioService.getEnrichedPortfolio(accountId);
            log.info("Portfolio retrieved successfully, positions count: {}", 
                    portfolio.getPositions() != null ? portfolio.getPositions().size() : 0);
            
            PortfolioDto dto = PortfolioDto.from(portfolio);
            dto.setAccountId(accountId); // Устанавливаем accountId в DTO
            
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            log.error("Error getting portfolio for accountId: " + accountId, e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Error getting portfolio: " + e.getMessage());
            error.put("errorType", e.getClass().getSimpleName());
            return ResponseEntity.status(500).body(error);
        }
    }

    @GetMapping("/mode")
    public ResponseEntity<?> getCurrentMode() {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("currentMode", investApiManager.getCurrentMode());
            response.put("availableModes", investApiManager.getAvailableModesInfo());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting current mode", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Error getting current mode: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
    
    @GetMapping("/with-names")
    public ResponseEntity<?> getPortfolioWithNames(@RequestParam("accountId") String accountId) {
        try {
            log.info("Getting portfolio with real names for accountId: {}", accountId);
            
            // Автоматически определяем режим по типу счета
            String currentMode = investApiManager.getCurrentMode();
            log.info("Current mode: {}", currentMode);
            
            // Используем новый метод с реальными названиями
            PortfolioDto dto = portfolioService.getPortfolioWithNames(accountId, 
                new InstrumentNameService(investApiManager));
            dto.setAccountId(accountId);
            
            log.info("Portfolio with names retrieved successfully, positions count: {}", 
                    dto.getPositions() != null ? dto.getPositions().size() : 0);
            
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            log.error("Error getting portfolio with names for accountId: " + accountId, e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Error getting portfolio with names: " + e.getMessage());
            error.put("errorType", e.getClass().getSimpleName());
            return ResponseEntity.status(500).body(error);
        }
    }
} 