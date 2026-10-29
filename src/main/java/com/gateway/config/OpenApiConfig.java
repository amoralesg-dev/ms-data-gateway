package com.gateway.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("MS Data Gateway API")
                        .description("Microservicio gateway para acceso controlado a bases de datos Progress OpenEdge mediante JDBC")
                        .version("1.0.0")
                        .contact(new Contact().name("Data Gateway Team").email("gateway@company.com"))
                        .license(new License().name("Internal Use Only")))
                .addSecurityItem(new SecurityRequirement().addList("ApiKeyAuth"))
                .addSecurityItem(new SecurityRequirement().addList("AdminKeyAuth"))
                .components(new Components()
                        .addSecuritySchemes("ApiKeyAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-API-Key"))
                        .addSecuritySchemes("AdminKeyAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Admin-Key")));
    }
}
