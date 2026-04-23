package com.forecast.forecastsvc.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient mlWebClient(@Value("${ml.service.url}") String mlServiceUrl) {
        return WebClient.builder()
                .baseUrl(mlServiceUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .codecs(config -> config.defaultCodecs()
                        .maxInMemorySize(2 * 1024 * 1024)) // 2MB — forecast responses can be large
                .build();
    }
}
