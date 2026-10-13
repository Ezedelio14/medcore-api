package com.medcore.api.appointment.dto;

import com.medcore.api.appointment.AppointmentPaymentStatus;
import com.medcore.api.appointment.AppointmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Optional appointment search criteria (combined with AND)")
public record AppointmentFilter(
        @Schema(description = "Filter by doctor") UUID doctorId,
        @Schema(description = "Filter by patient") UUID patientId,
        @Schema(description = "Filter by clinic") UUID clinicId,
        @Schema(description = "Filter by status") AppointmentStatus status,
        @Schema(description = "Filter by payment status") AppointmentPaymentStatus paymentStatus,
        @Schema(description = "Scheduled at or after", example = "2026-11-01T00:00:00Z") Instant from,
        @Schema(description = "Scheduled before", example = "2026-12-01T00:00:00Z") Instant to) {
}
