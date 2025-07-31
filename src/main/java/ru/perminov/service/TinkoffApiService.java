package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.tinkoff.piapi.core.InvestApi;
import ru.tinkoff.piapi.contract.v1.InstrumentStatus;

@Slf4j
@Service
@RequiredArgsConstructor
public class TinkoffApiService {
    
    private final InvestApi investApi;
    
    /**
     * Получение списка инструментов
     */
    public Mono<String> getInstruments() {
        return Mono.fromCallable(() -> {
            var response = investApi.getInstrumentsService().getSharesSync(InstrumentStatus.INSTRUMENT_STATUS_ALL);
            log.info("Получен список инструментов");
            return response.toString();
        }).doOnError(error -> log.error("Ошибка при получении инструментов: {}", error.getMessage()));
    }
    
    /**
     * Получение информации об инструменте по FIGI
     */
    public Mono<String> getInstrumentByFigi(String figi) {
        return Mono.fromCallable(() -> {
            // Пока возвращаем пустой ответ, так как метод не найден
            log.info("Получена информация об инструменте: {}", figi);
            return "{\"figi\": \"" + figi + "\", \"status\": \"not_implemented\"}";
        }).doOnError(error -> log.error("Ошибка при получении инструмента {}: {}", figi, error.getMessage()));
    }
    
    /**
     * Получение портфеля
     */
    public Mono<String> getPortfolio(String accountId) {
        return Mono.fromCallable(() -> {
            var response = investApi.getOperationsService().getPortfolioSync(accountId);
            log.info("Получен портфель для аккаунта: {}", accountId);
            return response.toString();
        }).doOnError(error -> log.error("Ошибка при получении портфеля: {}", error.getMessage()));
    }
    
    /**
     * Получение списка аккаунтов
     */
    public Mono<String> getAccounts() {
        return Mono.fromCallable(() -> {
            var response = investApi.getUserService().getAccountsSync();
            log.info("Получен список аккаунтов");
            return response.toString();
        }).doOnError(error -> log.error("Ошибка при получении аккаунтов: {}", error.getMessage()));
    }
    
    /**
     * Получение рыночных данных
     */
    public Mono<String> getMarketData(String figi) {
        return Mono.fromCallable(() -> {
            var response = investApi.getMarketDataService().getLastPricesSync(java.util.List.of(figi));
            log.info("Получены рыночные данные для: {}", figi);
            return response.toString();
        }).doOnError(error -> log.error("Ошибка при получении рыночных данных: {}", error.getMessage()));
    }
} 