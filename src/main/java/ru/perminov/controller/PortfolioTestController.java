package ru.perminov.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.perminov.service.PortfolioService;
import ru.perminov.service.InvestApiManager;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/portfolio-test")
@RequiredArgsConstructor
@Slf4j
public class PortfolioTestController {
    
    private final PortfolioService portfolioService;
    private final InvestApiManager investApiManager;
    
    /**
     * Тестовый endpoint для проверки портфеля с детальным логированием
     */
    @GetMapping("/debug")
    public ResponseEntity<?> debugPortfolio(@RequestParam("accountId") String accountId) {
        try {
            log.info("=== НАЧАЛО ОТЛАДКИ ПОРТФЕЛЯ ===");
            log.info("AccountId: {}", accountId);
            
            // Получаем портфель
            var portfolio = portfolioService.getPortfolio(accountId);
            log.info("Портфель получен, позиций: {}", portfolio.getPositions().size());
            
            // Анализируем каждую позицию
            for (var position : portfolio.getPositions()) {
                log.info("=== ПОЗИЦИЯ: {} ===", position.getFigi());
                log.info("Тип инструмента: {}", position.getInstrumentType());
                log.info("Количество: {}", position.getQuantity());
                log.info("Текущая цена (объект): {}", position.getCurrentPrice());
                log.info("Средняя цена (объект): {}", position.getAveragePositionPrice());
                
                if (position.getCurrentPrice() != null) {
                    log.info("Текущая цена (toString): {}", position.getCurrentPrice().toString());
                    log.info("Текущая цена (getValue): {}", position.getCurrentPrice().getValue());
                }
                
                if (position.getAveragePositionPrice() != null) {
                    log.info("Средняя цена (toString): {}", position.getAveragePositionPrice().toString());
                    log.info("Средняя цена (getValue): {}", position.getAveragePositionPrice().getValue());
                }
            }
            
            // Конвертируем в DTO
            var portfolioDto = ru.perminov.dto.PortfolioDto.from(portfolio);
            log.info("DTO создан, позиций: {}", portfolioDto.getPositions().size());
            
            // Анализируем DTO
            for (var positionDto : portfolioDto.getPositions()) {
                log.info("=== DTO ПОЗИЦИЯ: {} ===", positionDto.getFigi());
                log.info("Тикер: {}", positionDto.getTicker());
                log.info("Название: {}", positionDto.getName());
                log.info("Текущая цена: {}", positionDto.getCurrentPrice());
                log.info("Средняя цена: {}", positionDto.getAveragePrice());
                log.info("Текущая цена (display): {}", positionDto.getCurrentPriceDisplay());
                log.info("Средняя цена (display): {}", positionDto.getAveragePriceDisplay());
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Отладка завершена, проверьте логи");
            response.put("portfolio", portfolioDto);
            
            log.info("=== КОНЕЦ ОТЛАДКИ ПОРТФЕЛЯ ===");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при отладке портфеля: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Ошибка при отладке портфеля: " + e.getMessage());
            error.put("errorType", e.getClass().getSimpleName());
            return ResponseEntity.status(500).body(error);
        }
    }
}



