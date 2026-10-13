package com.medcore.api.payment.dto;

import com.medcore.api.payment.Payment;
import com.medcore.api.payment.PaymentProvider;
import com.medcore.api.payment.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Payment state")
public record PaymentResponse(
        @Schema(description = "Payment id") UUID id,
        @Schema(description = "Appointment paid by this payment") UUID appointmentId,
        @Schema(description = "Amount", example = "60.00") BigDecimal amount,
        @Schema(description = "ISO 4217 currency (lowercase)", example = "eur") String currency,
        @Schema(description = "Payment method", example = "STRIPE") PaymentProvider provider,
        @Schema(description = "Status", example = "SUCCEEDED") PaymentStatus status,
        @Schema(description = "Failure reason when FAILED", nullable = true) String failureReason,
        @Schema(description = "When the payment succeeded", nullable = true) Instant paidAt,
        @Schema(description = "Creation timestamp") Instant createdAt) {

    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(p.getId(), p.getAppointment().getId(), p.getAmount(), p.getCurrency(),
                p.getProvider(), p.getStatus(), p.getFailureReason(), p.getPaidAt(), p.getCreatedAt());
    }
}
