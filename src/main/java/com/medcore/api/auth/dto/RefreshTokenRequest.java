package com.medcore.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Refresh token to rotate or revoke")
public record RefreshTokenRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String refreshToken) {
}
