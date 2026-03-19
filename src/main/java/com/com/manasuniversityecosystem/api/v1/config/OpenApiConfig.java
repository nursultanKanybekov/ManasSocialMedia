package com.com.manasuniversityecosystem.api.v1.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .externalDocs(new ExternalDocumentation()
                        .description("ManasMezun Platform Documentation")
                        .url("https://manas.edu.kg"))
                .servers(List.of(
                        new Server().url("http://localhost:8081").description("Local Dev")
                ))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, bearerScheme()));
    }

    private Info apiInfo() {
        return new Info()
                .title("ManasMezun REST API")
                .version("1.0.0")
                .description("""
                        RESTful API for ManasMezun University Alumni Ecosystem.
                        
                        **Authentication:** All endpoints require a Bearer JWT token obtained from
                        `POST /api/v1/auth/login` or `POST /api/v1/auth/obis-login`.
                        
                        **Response format:** All responses are wrapped in `ApiResponse<T>`.
                        
                        **Pagination:** Paginated endpoints accept `page` (0-based) and `size` params.
                        """)
                .contact(new Contact()
                        .name("ManasMezun Team")
                        .email("dev@manas.edu.kg"))
                .license(new License().name("Private").url("#"));
    }

    private SecurityScheme bearerScheme() {
        return new SecurityScheme()
                .name(BEARER_SCHEME)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Paste your JWT access token here (without 'Bearer ' prefix).");
    }
}