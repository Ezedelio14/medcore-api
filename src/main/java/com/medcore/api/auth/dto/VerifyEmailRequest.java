package com.medcore.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record VerifyEmailRequest(
        @Schema(description = "Token received by e-mail", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String token) {
}
