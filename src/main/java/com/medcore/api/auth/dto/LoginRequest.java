package com.medcore.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Credentials")
public record LoginRequest(
        @Schema(example = "ana.silva@example.com", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Email String email,
        @Schema(example = "Str0ng-Passw0rd", requiredMode = Schema.RequiredMode.REQUIRED, format = "password") @NotBlank String password) {
}
