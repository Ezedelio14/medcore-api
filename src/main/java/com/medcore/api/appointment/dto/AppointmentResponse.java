package com.medcore.api.appointment.dto;

import com.medcore.api.appointment.Appointment;
import com.medcore.api.appointment.AppointmentPaymentStatus;
import com.medcore.api.appointment.AppointmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Appointment details")
public record AppointmentResponse(
        @Schema(description = "Appointment id", example = "6a1f9b0e-5a7c-4c4f-8e2a-7d1b3c5e9f00") UUID id,
        @Schema(description = "Patient summary") PersonSummary patient,
        @Schema(description = "Doctor summary") PersonSummary doctor,
        @Schema(description = "Clinic where the appointment takes place") UUID clinicId,
        @Schema(description = "Start (UTC)", example = "2026-11-03T09:30:00Z") Instant scheduledAt,
        @Schema(description = "End (UTC)", example = "2026-11-03T10:00:00Z") Instant endsAt,
        @Schema(description = "Duration in minutes", example = "30") int duration,
        @Schema(description = "Lifecycle status", example = "CONFIRMED") AppointmentStatus status,
        @Schema(description = "Reason for the visit") String reason,
        @Schema(description = "Cancellation reason, when cancelled", nullable = true) String cancellationReason,
        @Schema(description = "Consultation price", example = "60.00") BigDecimal price,
        @Schema(description = "Payment status", example = "PAID") AppointmentPaymentStatus paymentStatus,
        @Schema(description = "Creation timestamp") Instant createdAt,
        @Schema(description = "Last update timestamp") Instant updatedAt) {

    @Schema(description = "Minimal person reference")
    public record PersonSummary(
            @Schema(example = "3c9d1a7e-2b4f-4d8a-9e6f-0a1b2c3d4e5f") UUID id,
            @Schema(example = "Ana Silva") String name) {
    }

    public static AppointmentResponse from(Appointment a) {
        return new AppointmentResponse(a.getId(),
                new PersonSummary(a.getPatient().getId(), a.getPatient().fullName()),
                new PersonSummary(a.getDoctor().getId(), a.getDoctor().fullName()),
                a.getClinic().getId(), a.getScheduledAt(), a.getEndsAt(), a.getDuration(), a.getStatus(),
                a.getReason(), a.getCancellationReason(), a.getPrice(), a.getPaymentStatus(),
                a.getCreatedAt(), a.getUpdatedAt());
    }
}
