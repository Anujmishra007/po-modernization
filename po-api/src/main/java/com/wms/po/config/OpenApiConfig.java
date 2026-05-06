package com.wms.po.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI (Swagger) configuration for PO Modernization API.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(servers())
                .tags(tags())
                .components(components())
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    private Info apiInfo() {
        return new Info()
                .title("PO Modernization API")
                .version("1.0.0")
                .description("""
                    REST API for Purchase Order (PO) Modernization system.

                    ## Overview
                    This API provides endpoints for:
                    - **PO Management**: Create, update, delete, and query Purchase Orders
                    - **Population**: Populate POs with receipt and inventory data
                    - **Finalization**: Finalize receipts with inventory posting
                    - **Receipts**: Manage receipt lifecycle

                    ## Authentication
                    All endpoints require authentication via JWT token in the `Authorization` header.

                    ## Error Handling
                    All errors return a standard error response with:
                    - `code`: Error code (e.g., PO_001, RCV_001)
                    - `message`: Human-readable error message
                    - `details`: Additional context about the error

                    ## Versioning
                    API version is included in the URL path: `/api/v1/...`
                    """)
                .contact(new Contact()
                        .name("WMS Team")
                        .email("wms-team@example.com"))
                .license(new License()
                        .name("Proprietary")
                        .url("https://example.com/license"));
    }

    private List<Server> servers() {
        return List.of(
                new Server()
                        .url("http://localhost:8080")
                        .description("Local Development Server"),
                new Server()
                        .url("https://api.wms.example.com")
                        .description("Production Server")
        );
    }

    private List<Tag> tags() {
        return List.of(
                new Tag()
                        .name("PO")
                        .description("Purchase Order management endpoints"),
                new Tag()
                        .name("Population")
                        .description("PO population workflow endpoints"),
                new Tag()
                        .name("Finalization")
                        .description("Receipt finalization endpoints"),
                new Tag()
                        .name("Receipt")
                        .description("Receipt management endpoints"),
                new Tag()
                        .name("Health")
                        .description("Health check and monitoring endpoints")
        );
    }

    private Components components() {
        return new Components()
                .addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT authentication token"));
    }
}
