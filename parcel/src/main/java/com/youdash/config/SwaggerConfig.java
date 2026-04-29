package com.youdash.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    /**
     * Declares API base URLs for Swagger UI "Try it out".
     * Without this, Swagger often picks http:// and browsers block or mis-handle redirects to https.
     * Override production URL via env OPENAPI_SERVER_URL or property openapi.server.url.
     */
    @Bean
    public OpenAPI openAPI(
            @Value("${openapi.server.url:https://www.youdashexpress.com}") String productionServerUrl,
            @Value("${openapi.server.local-url:http://localhost:8080}") String localServerUrl) {
        return new OpenAPI()
                .servers(List.of(
                        new Server().url(trimTrailingSlash(productionServerUrl)).description("Production (HTTPS)"),
                        new Server().url(trimTrailingSlash(localServerUrl)).description("Local development")))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .name("Authorization")
                        ))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }

    private static String trimTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            return "https://www.youdashexpress.com";
        }
        String t = url.trim();
        while (t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
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
                .pathsToMatch("/orders/**", "/coupons/**", "/users/**", "/customer/**")
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

