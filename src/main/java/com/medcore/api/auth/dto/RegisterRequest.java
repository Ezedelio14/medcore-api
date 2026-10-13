package com.medcore.api.auth.dto;

import com.medcore.api.patient.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = "Self-registration of a patient account. Staff and doctor accounts are created by clinic admins.")
public record RegisterRequest(
        @Schema(example = "ana.silva@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Email @Size(max = 255) String email,

        @Schema(description = "At least 10 characters, including a letter and a digit", example = "Str0ng-Passw0rd",
                requiredMode = Schema.RequiredMode.REQUIRED, format = "password")
        @NotBlank @Size(min = 10, max = 128)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "must contain at least one letter and one digit")
        String password,

        @Schema(example = "Ana", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 100) String firstName,
        @Schema(example = "Silva", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 100) String lastName,
        @Schema(example = "1990-04-12") @Past LocalDate dateOfBirth,
        @Schema(example = "FEMALE") Gender gender,
        @Schema(example = "+351912345678") @Pattern(regexp = "^\\+?[0-9 ]{7,20}$") String phone) {
}
