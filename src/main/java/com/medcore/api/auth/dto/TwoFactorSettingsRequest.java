package com.medcore.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record TwoFactorSettingsRequest(
        @Schema(description = "Enable or disable e-mail based two-factor authentication", example = "true") boolean enabled) {
}
