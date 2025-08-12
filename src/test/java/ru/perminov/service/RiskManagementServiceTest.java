package ru.perminov.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
public class RiskManagementServiceTest {

    private RiskManagementService riskManagementService;

    @BeforeEach
    void setUp() {
        riskManagementService = new RiskManagementService();
    }

    @Test
    void testDailyLossLimitWithinBounds() {
        // Тест: дневной убыток в пределах лимита (1% от портфеля)
        BigDecimal dailyPnL = new BigDecimal("-10000"); // Убыток 10,000 руб
        BigDecimal portfolioValue = new BigDecimal("1000000"); // Портфель 1,000,000 руб
        
        boolean result = riskManagementService.checkDailyLossLimit(dailyPnL, portfolioValue);
        
        assertTrue(result, "Дневной убыток 1% должен быть в пределах лимита (2%)");
    }

    @Test
    void testDailyLossLimitExceeded() {
        // Тест: дневной убыток превышает лимит (3% от портфеля)
        BigDecimal dailyPnL = new BigDecimal("-30000"); // Убыток 30,000 руб
        BigDecimal portfolioValue = new BigDecimal("1000000"); // Портфель 1,000,000 руб
        
        boolean result = riskManagementService.checkDailyLossLimit(dailyPnL, portfolioValue);
        
        assertFalse(result, "Дневной убыток 3% должен превышать лимит (2%)");
    }

    @Test
    void testDailyLossLimitExactBoundary() {
        // Тест: дневной убыток точно на границе лимита (2% от портфеля)
        BigDecimal dailyPnL = new BigDecimal("-20000"); // Убыток 20,000 руб
        BigDecimal portfolioValue = new BigDecimal("1000000"); // Портфель 1,000,000 руб
        
        boolean result = riskManagementService.checkDailyLossLimit(dailyPnL, portfolioValue);
        
        assertFalse(result, "Дневной убыток 2% должен быть на границе лимита (не в пределах)");
    }

    @Test
    void testDailyProfit() {
        // Тест: дневная прибыль (должна проходить проверку)
        BigDecimal dailyPnL = new BigDecimal("50000"); // Прибыль 50,000 руб
        BigDecimal portfolioValue = new BigDecimal("1000000"); // Портфель 1,000,000 руб
        
        boolean result = riskManagementService.checkDailyLossLimit(dailyPnL, portfolioValue);
        
        assertTrue(result, "Дневная прибыль должна проходить проверку лимита убытков");
    }

    @Test
    void testZeroPortfolioValue() {
        // Тест: нулевая стоимость портфеля
        BigDecimal dailyPnL = new BigDecimal("-1000");
        BigDecimal portfolioValue = BigDecimal.ZERO;
        
        assertThrows(ArithmeticException.class, () -> {
            riskManagementService.checkDailyLossLimit(dailyPnL, portfolioValue);
        }, "Должна быть ошибка при делении на ноль");
    }

    @Test
    void testSmallPortfolio() {
        // Тест: маленький портфель
        BigDecimal dailyPnL = new BigDecimal("-100"); // Убыток 100 руб
        BigDecimal portfolioValue = new BigDecimal("5000"); // Портфель 5,000 руб
        
        boolean result = riskManagementService.checkDailyLossLimit(dailyPnL, portfolioValue);
        
        assertTrue(result, "Дневной убыток 2% должен быть в пределах лимита");
    }

    @Test
    void testLargePortfolio() {
        // Тест: большой портфель
        BigDecimal dailyPnL = new BigDecimal("-1000000"); // Убыток 1,000,000 руб
        BigDecimal portfolioValue = new BigDecimal("50000000"); // Портфель 50,000,000 руб
        
        boolean result = riskManagementService.checkDailyLossLimit(dailyPnL, portfolioValue);
        
        assertTrue(result, "Дневной убыток 2% должен быть в пределах лимита");
    }
}
