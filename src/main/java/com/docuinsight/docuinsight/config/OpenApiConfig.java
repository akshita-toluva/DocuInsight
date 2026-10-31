package com.docuinsight.docuinsight.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

//  API docs. Adds an "Authorize" button where you paste the JWT from /api/auth/login.
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "DocuInsight API", version = "1.0",
                description = "Upload documents, generate AI reports, and ask grounded questions with cited sources."),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}