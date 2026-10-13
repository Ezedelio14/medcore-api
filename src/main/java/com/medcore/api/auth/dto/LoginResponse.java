package com.medcore.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Either the issued tokens, or a 2FA challenge to complete with /login/2fa")
public record LoginResponse(
        @Schema(description = "True when a 2FA code was sent and must be submitted") boolean twoFactorRequired,
        @Schema(description = "Challenge to send to /login/2fa", nullable = true) String challengeToken,
        @Schema(description = "Tokens, when no second factor is required", nullable = true) TokenResponse tokens) {

    public static LoginResponse authenticated(TokenResponse tokens) {
        return new LoginResponse(false, null, tokens);
    }

    public static LoginResponse challenge(String challengeToken) {
        return new LoginResponse(true, challengeToken, null);
    }
}
