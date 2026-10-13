package com.medcore.api.config;

import com.medcore.api.common.web.CorrelationIdFilter;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** OpenAPI 3 / Swagger UI configuration. UI: {@code /swagger-ui.html}, spec: {@code /v3/api-docs}. */
@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    OpenAPI medcoreOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("MedCore Healthcare Platform API")
                        .version("v1")
                        .description("""
                                Backend API for managing clinics, doctors, patients, appointments, medical records,
                                prescriptions and payments.

                                **Authentication**: obtain an access token with `POST /api/v1/auth/login` and send it as
                                `Authorization: Bearer <token>`. Access tokens last 15 minutes; renew them with
                                `POST /api/v1/auth/refresh`.

                                **Errors** follow RFC 9457 (`application/problem+json`) with a stable `code` and the
                                request `correlationId`.

                                **Tracing**: send `X-Correlation-Id` to correlate your request across logs and events.""")
                        .contact(new Contact().name("MedCore Engineering").email("engineering@medcore.health"))
                        .license(new License().name("Proprietary")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local")))
                .tags(List.of(
                        new Tag().name("Authentication").description("Registration, login, tokens, password reset, e-mail verification and 2FA"),
                        new Tag().name("Appointments").description("Availability, booking and appointment lifecycle"),
                        new Tag().name("Payments").description("Payment strategies, refunds and receipts"),
                        new Tag().name("Webhooks").description("Inbound provider callbacks")))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Access token from /api/v1/auth/login")));
    }

    /** Documents the optional correlation header on every operation. */
    @Bean
    OperationCustomizer correlationIdHeader() {
        return (operation, handlerMethod) -> operation.addParametersItem(new HeaderParameter()
                .name(CorrelationIdFilter.HEADER)
                .required(false)
                .description("Optional request id propagated to logs and Kafka events; echoed in the response")
                .schema(new StringSchema().example("4f9c2d1e-7a3b-4c5d-8e9f-0a1b2c3d4e5f")));
    }
}
