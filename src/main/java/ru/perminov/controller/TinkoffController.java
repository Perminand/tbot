package ru.perminov.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.perminov.service.TinkoffApiService;
import ru.perminov.service.TradingService;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@Slf4j
@RestController
@RequestMapping("/api/tinkoff")
@RequiredArgsConstructor
public class TinkoffController {
    
    private final TinkoffApiService tinkoffApiService;
    private final TradingService tradingService;
    
    /**
     * Получение списка всех инструментов
     */
    @GetMapping("/instruments")
    public Mono<ResponseEntity<String>> getInstruments() {
        return tinkoffApiService.getInstruments()
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
    
    /**
     * Получение информации об инструменте по FIGI
     */
    @GetMapping("/instruments/{figi}")
    public Mono<ResponseEntity<String>> getInstrumentByFigi(@PathVariable("figi") String figi) {
        return tinkoffApiService.getInstrumentByFigi(figi)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
    
    /**
     * Получение портфеля по ID аккаунта
     */
    @GetMapping("/portfolio")
    public Mono<ResponseEntity<String>> getPortfolio(@RequestParam("accountId") String accountId) {
        return tinkoffApiService.getPortfolio(accountId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
    
    /**
     * Получение списка аккаунтов
     */
    @GetMapping("/accounts")
    public Mono<ResponseEntity<String>> getAccounts() {
        return tinkoffApiService.getAccounts()
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
    
    /**
     * Получение рыночных данных по FIGI
     */
    @GetMapping("/market-data/{figi}")
    public Mono<ResponseEntity<String>> getMarketData(@PathVariable String figi) {
        return tinkoffApiService.getMarketData(figi)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
    
    // ========== ТОРГОВЫЕ ОПЕРАЦИИ ==========
    
    /**
     * Размещение рыночного ордера на покупку
     */
    @PostMapping("/orders/market-buy")
    public Mono<ResponseEntity<String>> placeMarketBuyOrder(
            @RequestParam("figi") String figi,
            @RequestParam("lots") int lots,
            @RequestParam("accountId") String accountId) {
        return tradingService.placeMarketBuyOrder(figi, lots, accountId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.badRequest().build());
    }
    
    /**
     * Размещение рыночного ордера на продажу
     */
    @PostMapping("/orders/market-sell")
    public Mono<ResponseEntity<String>> placeMarketSellOrder(
            @RequestParam("figi") String figi,
            @RequestParam("lots") int lots,
            @RequestParam("accountId") String accountId) {
        return tradingService.placeMarketSellOrder(figi, lots, accountId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.badRequest().build());
    }
    
    /**
     * Размещение лимитного ордера
     */
    @PostMapping("/orders/limit")
    public Mono<ResponseEntity<String>> placeLimitOrder(
            @RequestParam("figi") String figi,
            @RequestParam("lots") int lots,
            @RequestParam("price") BigDecimal price,
            @RequestParam("direction") String direction,
            @RequestParam("accountId") String accountId) {
        return tradingService.placeLimitOrder(figi, lots, price, direction, accountId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.badRequest().build());
    }
    
    /**
     * Отмена ордера
     */
    @PostMapping("/orders/cancel")
    public Mono<ResponseEntity<String>> cancelOrder(
            @RequestParam("orderId") String orderId,
            @RequestParam("accountId") String accountId) {
        return tradingService.cancelOrder(orderId, accountId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.badRequest().build());
    }
    
    /**
     * Получение статуса ордера
     */
    @GetMapping("/orders/{orderId}")
    public Mono<ResponseEntity<String>> getOrderState(
            @PathVariable("orderId") String orderId,
            @RequestParam("accountId") String accountId) {
        return tradingService.getOrderState(orderId, accountId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
    
    /**
     * Получение списка активных ордеров
     */
    @GetMapping("/orders")
    public Mono<ResponseEntity<String>> getActiveOrders(@RequestParam("accountId") String accountId) {
        return tradingService.getActiveOrders(accountId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
} 