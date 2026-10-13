package com.medcore.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Second login step for accounts with 2FA enabled")
public record TwoFactorLoginRequest(
        @Schema(description = "challengeToken returned by /login", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String challengeToken,
        @Schema(description = "6-digit code sent by e-mail", example = "482913", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Pattern(regexp = "^\\d{6}$") String code) {
}
