package com.medcore.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @Schema(description = "Token received by e-mail", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String token,
        @Schema(format = "password", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(min = 10, max = 128)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "must contain at least one letter and one digit")
        String newPassword) {
}
