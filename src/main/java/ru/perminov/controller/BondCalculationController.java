package ru.perminov.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.perminov.service.BondCalculationService;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/bond-calculations")
@RequiredArgsConstructor
@Slf4j
public class BondCalculationController {
    
    private final BondCalculationService bondCalculationService;
    
    /**
     * Расчет НКД для облигации
     */
    @GetMapping("/nkd/{figi}")
    public ResponseEntity<?> calculateNKD(@PathVariable String figi, 
                                        @RequestParam(defaultValue = "100") BigDecimal currentPrice) {
        try {
            BigDecimal nkd = bondCalculationService.calculateAccumulatedCouponYield(figi, currentPrice);
            
            Map<String, Object> response = new HashMap<>();
            response.put("figi", figi);
            response.put("currentPrice", currentPrice);
            response.put("nkd", nkd);
            response.put("message", "НКД рассчитан успешно");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка расчета НКД для {}: {}", figi, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("Ошибка расчета НКД: " + e.getMessage());
        }
    }
    
    /**
     * Расчет текущей доходности облигации
     */
    @GetMapping("/yield/{figi}")
    public ResponseEntity<?> calculateYield(@PathVariable String figi, 
                                          @RequestParam(defaultValue = "100") BigDecimal currentPrice) {
        try {
            BigDecimal yield = bondCalculationService.calculateCurrentYield(figi, currentPrice);
            
            Map<String, Object> response = new HashMap<>();
            response.put("figi", figi);
            response.put("currentPrice", currentPrice);
            response.put("yield", yield);
            response.put("yieldPercent", yield + "%");
            response.put("message", "Доходность рассчитана успешно");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка расчета доходности для {}: {}", figi, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("Ошибка расчета доходности: " + e.getMessage());
        }
    }
    
    /**
     * Расчет доходности к погашению (YTM)
     */
    @GetMapping("/ytm/{figi}")
    public ResponseEntity<?> calculateYTM(@PathVariable String figi, 
                                        @RequestParam(defaultValue = "100") BigDecimal currentPrice,
                                        @RequestParam(defaultValue = "1000") BigDecimal faceValue) {
        try {
            BigDecimal ytm = bondCalculationService.calculateYieldToMaturity(figi, currentPrice, faceValue);
            
            Map<String, Object> response = new HashMap<>();
            response.put("figi", figi);
            response.put("currentPrice", currentPrice);
            response.put("faceValue", faceValue);
            response.put("ytm", ytm);
            response.put("ytmPercent", ytm + "%");
            response.put("message", "YTM рассчитан успешно");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка расчета YTM для {}: {}", figi, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("Ошибка расчета YTM: " + e.getMessage());
        }
    }
    
    /**
     * Полный анализ облигации
     */
    @GetMapping("/analysis/{figi}")
    public ResponseEntity<?> analyzeBond(@PathVariable String figi, 
                                       @RequestParam(defaultValue = "100") BigDecimal currentPrice,
                                       @RequestParam(defaultValue = "1000") BigDecimal faceValue) {
        try {
            BigDecimal nkd = bondCalculationService.calculateAccumulatedCouponYield(figi, currentPrice);
            BigDecimal yield = bondCalculationService.calculateCurrentYield(figi, currentPrice);
            BigDecimal ytm = bondCalculationService.calculateYieldToMaturity(figi, currentPrice, faceValue);
            
            Map<String, Object> response = new HashMap<>();
            response.put("figi", figi);
            response.put("currentPrice", currentPrice);
            response.put("faceValue", faceValue);
            response.put("nkd", nkd);
            response.put("currentYield", yield);
            response.put("ytm", ytm);
            response.put("currentYieldPercent", yield + "%");
            response.put("ytmPercent", ytm + "%");
            response.put("message", "Анализ облигации выполнен успешно");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка анализа облигации {}: {}", figi, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("Ошибка анализа облигации: " + e.getMessage());
        }
    }
} 