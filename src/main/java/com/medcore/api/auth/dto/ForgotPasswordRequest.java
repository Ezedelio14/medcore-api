package com.medcore.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
        @Schema(example = "ana.silva@example.com", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Email String email) {
}
