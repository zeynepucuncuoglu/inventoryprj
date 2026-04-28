package com.forecast.product.config;

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
    public OpenAPI productServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Product Service API")
                        .description("Manages product catalogue and inventory stock levels. " +
                                "Stock changes publish events to Kafka for downstream consumers.")
                        .version("v1.0.0")
                        .contact(new Contact().name("Demand Forecast Platform")))
                .servers(List.of(
                        new Server().url("http://localhost:8081").description("Local"),
                        new Server().url("http://localhost:8080/product-service").description("Via API Gateway")
                ));
    }
}
