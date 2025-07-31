package ru.perminov.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.perminov.service.PortfolioService;
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

    @GetMapping
    public ResponseEntity<?> getPortfolio(@RequestParam("accountId") String accountId) {
        try {
            log.info("Getting portfolio for accountId: {}", accountId);
            Portfolio portfolio = portfolioService.getPortfolio(accountId);
            log.info("Portfolio retrieved successfully, positions count: {}", 
                    portfolio.getPositions() != null ? portfolio.getPositions().size() : 0);
            
            PortfolioDto dto = PortfolioDto.from(portfolio);
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            log.error("Error getting portfolio for accountId: " + accountId, e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Error getting portfolio: " + e.getMessage());
            error.put("errorType", e.getClass().getSimpleName());
            return ResponseEntity.status(500).body(error);
        }
    }
} 