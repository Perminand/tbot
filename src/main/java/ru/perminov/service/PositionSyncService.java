package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.perminov.repository.PositionRepository;
import ru.perminov.model.Position;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PositionSyncService {

    private final AccountService accountService;
    private final PortfolioService portfolioService;
    private final PositionRepository positionRepository;

    @Scheduled(fixedRate = 60_000)
    public void syncPositions() {
        try {
            List<String> accountIds = accountService.getAccounts().stream().map(a -> a.getId()).toList();
            for (String accountId : accountIds) {
                var portfolio = portfolioService.getPortfolio(accountId);
                if (portfolio.getPositions() == null) continue;
                for (ru.tinkoff.piapi.core.models.Position p : portfolio.getPositions()) {
                    if (p.getFigi() == null || p.getFigi().isEmpty()) continue;
                    if (p.getQuantity() == null || p.getQuantity().compareTo(BigDecimal.ZERO) == 0) continue;
                    Position entity = positionRepository.findByFigi(p.getFigi()).orElseGet(Position::new);
                    entity.setFigi(p.getFigi());
                    entity.setTicker(null);
                    entity.setIsin(null);
                    entity.setInstrumentType(p.getInstrumentType());
                    entity.setBalance(null);
                    entity.setBlocked(null);
                    entity.setLots(p.getQuantity());
                    entity.setAveragePositionPrice(extractDecimal(p.getAveragePositionPrice()));
                    entity.setAveragePositionPriceNoNkd(null);
                    entity.setName(null);
                    entity.setCurrency(null);
                    entity.setCurrentPrice(null);
                    entity.setAveragePositionPriceFifo(null);
                    entity.setQuantityLots(null);
                    positionRepository.save(entity);
                }
                log.info("Синхронизация позиций завершена (accountId={})", accountId);
            }
        } catch (Exception e) {
            log.warn("Ошибка синхронизации позиций: {}", e.getMessage());
        }
    }

    private BigDecimal extractDecimal(Object moneyLike) {
        try {
            if (moneyLike == null) return null;
            if (moneyLike instanceof ru.tinkoff.piapi.core.models.Money) {
                return ((ru.tinkoff.piapi.core.models.Money) moneyLike).getValue();
            }
            String text = moneyLike.toString();
            if (text.contains("value=")) {
                String valuePart = text.substring(text.indexOf("value=") + 6);
                int comma = valuePart.indexOf(',');
                if (comma > 0) valuePart = valuePart.substring(0, comma);
                return new BigDecimal(valuePart);
            }
            return null;
        } catch (Exception ignore) {
            return null;
        }
    }
}


