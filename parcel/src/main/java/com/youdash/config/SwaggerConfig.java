package com.youdash.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .name("Authorization")
                        ))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }

    // --- Swagger "folders" (groups) ---

    @Bean
    public GroupedOpenApi groupPublicAndAuth() {
        return GroupedOpenApi.builder()
                .group("01 - Public & Auth")
                .pathsToMatch("/public/**", "/auth/**", "/rider-auth/**", "/rider/auth/**")
                .build();
    }

    @Bean
    public GroupedOpenApi groupUserOrders() {
        return GroupedOpenApi.builder()
                .group("02 - User Orders")
                .pathsToMatch("/orders/**", "/coupons/**")
                .pathsToExclude("/admin/**")
                .build();
    }

    @Bean
    public GroupedOpenApi groupPayments() {
        return GroupedOpenApi.builder()
                .group("03 - Payments")
                .pathsToMatch("/payments/**")
                .build();
    }

    @Bean
    public GroupedOpenApi groupRiders() {
        return GroupedOpenApi.builder()
                .group("04 - Rider App")
                .pathsToMatch("/riders/**", "/rider/**", "/order/**")
                .build();
    }

    @Bean
    public GroupedOpenApi groupAdmin() {
        return GroupedOpenApi.builder()
                .group("05 - Admin")
                .pathsToMatch("/admin/**")
                .build();
    }
}

