package com.medcore.api.payment.dto;

import com.medcore.api.payment.PaymentProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Start a payment for an appointment")
public record CreatePaymentRequest(
        @Schema(description = "Appointment to pay for", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull UUID appointmentId,
        @Schema(description = "Payment method / strategy", example = "STRIPE", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull PaymentProvider method) {
}
