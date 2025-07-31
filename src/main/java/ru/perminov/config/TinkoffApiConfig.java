package ru.perminov.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import ru.tinkoff.piapi.core.InvestApi;

@Slf4j
@Configuration
public class TinkoffApiConfig {
    
    @Value("${tinkoff.api.base-url:https://invest-public-api.tinkoff.ru}")
    private String baseUrl;
    
    @Bean
    public InvestApi investApi(@Value("${tinkoff.api.token}") String token,
                              @Value("${tinkoff.api.use-sandbox:true}") boolean useSandbox) {
        try {
            if (useSandbox) {
                log.info("Инициализация InvestApi в песочнице с токеном: {}", token.substring(0, Math.min(10, token.length())) + "...");
                return InvestApi.createSandbox(token);
            } else {
                log.info("Инициализация InvestApi в продакшене с токеном: {}", token.substring(0, Math.min(10, token.length())) + "...");
                return InvestApi.create(token);
            }
        } catch (Exception e) {
            log.error("Ошибка при инициализации InvestApi: {}", e.getMessage());
            throw e;
        }
    }
    
    @Bean
    public WebClient tinkoffWebClient() {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }
} 