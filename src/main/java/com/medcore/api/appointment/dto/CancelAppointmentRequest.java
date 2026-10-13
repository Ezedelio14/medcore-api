package com.medcore.api.appointment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Cancellation details")
public record CancelAppointmentRequest(
        @Schema(description = "Why the appointment is being cancelled", example = "Patient travelling",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 500)
        @NotBlank
        @Size(max = 500)
        String reason) {
}
