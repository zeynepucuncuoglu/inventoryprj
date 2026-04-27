package com.forecast.forecastsvc.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient mlWebClient(@Value("${ml.service.url}") String mlServiceUrl,
                                  ObjectMapper objectMapper) {
        return WebClient.builder()
                .baseUrl(mlServiceUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .codecs(config -> {
                    // Use Spring Boot's auto-configured ObjectMapper so LocalDate
                    // serializes as "2026-03-28" (ISO-8601), not [2026, 3, 28]
                    config.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(objectMapper));
                    config.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(objectMapper));
                    config.defaultCodecs().maxInMemorySize(2 * 1024 * 1024);
                })
                .build();
    }
}
