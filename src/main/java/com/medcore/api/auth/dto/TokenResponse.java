package com.medcore.api.auth.dto;

import com.medcore.api.user.Role;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Issued credentials")
public record TokenResponse(
        @Schema(description = "JWT access token") String accessToken,
        @Schema(description = "Opaque, single-use refresh token") String refreshToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(description = "Access token lifetime in seconds", example = "900") long expiresIn,
        @Schema(description = "Authenticated user id") UUID userId,
        @Schema(example = "PATIENT") Role role) {
}
