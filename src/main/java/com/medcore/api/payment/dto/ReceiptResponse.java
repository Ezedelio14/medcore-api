package com.medcore.api.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Payment receipt")
public record ReceiptResponse(
        @Schema(description = "Receipt number", example = "MC-2026-6A1F9B0E") String receiptNumber,
        @Schema(description = "Payment id") UUID paymentId,
        @Schema(description = "Clinic legal name") String clinicName,
        @Schema(description = "Doctor name") String doctorName,
        @Schema(description = "Patient name") String patientName,
        @Schema(description = "Appointment start") Instant appointmentAt,
        @Schema(description = "Amount paid") BigDecimal amount,
        @Schema(description = "Currency", example = "eur") String currency,
        @Schema(description = "Payment method", example = "STRIPE") String method,
        @Schema(description = "When the payment succeeded") Instant paidAt) {
}
