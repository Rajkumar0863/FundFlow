package com.fundflow.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI fundFlowOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FundFlow Payment API")
                        .version("1.0.0")
                        .description(
                            "Payment processing REST API built with Java, " +
                            "Spring Boot and PostgreSQL. Supports payment " +
                            "creation, idempotency, lifecycle transitions, " +
                            "audit history and refunds."
                        ));
    }
}