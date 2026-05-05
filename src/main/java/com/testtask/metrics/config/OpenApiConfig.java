package com.testtask.metrics.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Настройка OpenAPI 3: описание сервиса и схема Bearer JWT для Swagger UI.
 */
@Configuration
public class OpenApiConfig {

    /**
     * Возвращает корневой объект OpenAPI с заголовком и компонентом {@code bearer-jwt}.
     *
     * @return OpenAPI для публикации на {@code /v3/api-docs}
     */
    @Bean
    public OpenAPI metricsOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("Metrics Service API").version("1.0"))
                .components(new Components()
                        .addSecuritySchemes("bearer-jwt", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT из POST /auth")));
    }
}
