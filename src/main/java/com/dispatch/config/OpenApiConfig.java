package com.dispatch.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata shown in Swagger UI at {@code /swagger-ui.html}.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI dispatchOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Dispatch Load Balancer API")
                .version("1.0.0")
                .description("Assigns delivery orders to vehicles, respecting capacity and priority "
                        + "and minimising travel distance (Haversine)."));
    }
}
