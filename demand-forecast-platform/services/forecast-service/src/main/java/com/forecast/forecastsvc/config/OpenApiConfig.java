package com.forecast.forecastsvc.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI forecastServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Forecast Service API")
                        .description("Triggers and tracks demand forecast jobs. " +
                                "Forecasts run asynchronously — POST returns 202 Accepted, " +
                                "then poll GET /{id} until status = COMPLETED.")
                        .version("v1.0.0")
                        .contact(new Contact().name("Demand Forecast Platform")))
                .servers(List.of(
                        new Server().url("http://localhost:8083").description("Local"),
                        new Server().url("http://localhost:8080/forecast-service").description("Via API Gateway")
                ));
    }
}
