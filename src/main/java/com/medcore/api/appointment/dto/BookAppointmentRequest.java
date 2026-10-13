package com.medcore.api.appointment.dto;

import com.medcore.api.payment.PaymentProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Request to book an appointment in one of the doctor's free slots")
public record BookAppointmentRequest(

        @Schema(description = "Doctor to book", example = "8f14e45f-ceea-467a-9c3a-1f2d3c4b5a6e", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        UUID doctorId,

        @Schema(description = "Patient to book for. Ignored for PATIENT callers (always themselves); required for clinic staff.",
                example = "3c9d1a7e-2b4f-4d8a-9e6f-0a1b2c3d4e5f", nullable = true)
        UUID patientId,

        @Schema(description = "Slot start (ISO-8601 instant). Must match a free slot returned by the availability endpoint.",
                example = "2026-11-03T09:30:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @Future
        Instant scheduledAt,

        @Schema(description = "Reason for the visit, shared with the doctor", example = "Recurring headaches for two weeks", maxLength = 1000)
        @Size(max = 1000)
        String reason,

        @Schema(description = "How the appointment will be paid. STRIPE returns a client secret to confirm the card payment.",
                example = "STRIPE", defaultValue = "STRIPE")
        PaymentProvider paymentMethod) {
}
