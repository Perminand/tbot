package ru.perminov.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Data
@Configuration
@ConfigurationProperties(prefix = "tbank.api")
public class TBankApiConfig {
    private String baseUrl;
    private String token;

    @Bean
    public WebClient tbankWebClient() {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-API-KEY", token)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
} 