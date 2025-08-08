package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.core.InvestApi;
import ru.tinkoff.piapi.core.models.Portfolio;
import ru.tinkoff.piapi.core.models.Position;
import ru.tinkoff.piapi.contract.v1.Share;
import ru.tinkoff.piapi.contract.v1.Bond;
import ru.tinkoff.piapi.contract.v1.Etf;
import ru.perminov.dto.PortfolioDto;

import java.util.List;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioService {
    private final InvestApiManager investApiManager;
    // private final InstrumentService instrumentService;

    public Portfolio getPortfolio(String accountId) {
        try {
            log.info("Запрос портфеля для accountId: {}", accountId);
            
            InvestApi api = investApiManager.getCurrentInvestApi();
            if (api == null) {
                log.error("InvestApi не инициализирован");
                throw new RuntimeException("InvestApi не инициализирован");
            }
            
            Portfolio portfolio = api.getOperationsService().getPortfolio(accountId).join();
            log.info("Портфель успешно загружен для accountId: {}, позиций: {}", 
                    accountId, portfolio.getPositions() != null ? portfolio.getPositions().size() : 0);
            
            return portfolio;
        } catch (Exception e) {
            log.error("Ошибка при получении портфеля для accountId: {}", accountId, e);
            throw new RuntimeException("Ошибка при получении портфеля: " + e.getMessage(), e);
        }
    }

    public Portfolio getEnrichedPortfolio(String accountId) {
        return getPortfolio(accountId);
    }
    
    /**
     * Получение портфеля с реальными названиями инструментов
     */
    public PortfolioDto getPortfolioWithNames(String accountId, InstrumentNameService nameService) {
        try {
            log.info("Запрос портфеля с названиями для accountId: {}", accountId);
            
            Portfolio portfolio = getPortfolio(accountId);
            PortfolioDto portfolioDto = PortfolioDto.fromWithNames(portfolio, nameService);
            
            log.info("Портфель с названиями успешно загружен для accountId: {}, позиций: {}", 
                    accountId, portfolioDto.getPositions() != null ? portfolioDto.getPositions().size() : 0);
            
            return portfolioDto;
        } catch (Exception e) {
            log.error("Ошибка при получении портфеля с названиями для accountId: {}", accountId, e);
            throw new RuntimeException("Ошибка при получении портфеля с названиями: " + e.getMessage(), e);
        }
    }
} 