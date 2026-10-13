package com.medcore.api.appointment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

@Schema(description = "Move an appointment to another free slot of the same doctor")
public record RescheduleAppointmentRequest(
        @Schema(description = "New slot start (ISO-8601 instant)", example = "2026-11-05T14:00:00Z",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @Future
        Instant scheduledAt) {
}
