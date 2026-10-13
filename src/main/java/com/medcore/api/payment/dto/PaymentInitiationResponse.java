package com.medcore.api.payment.dto;

import com.medcore.api.payment.PaymentProvider;
import com.medcore.api.payment.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Payment created and what the client must do next")
public record PaymentInitiationResponse(
        @Schema(description = "Payment id") UUID paymentId,
        @Schema(description = "Payment method", example = "STRIPE") PaymentProvider provider,
        @Schema(description = "Status after initiation", example = "REQUIRES_ACTION") PaymentStatus status,
        @Schema(description = "Stripe PaymentIntent client secret, for stripe.confirmPayment(). Only for STRIPE.",
                example = "pi_3Q..._secret_...", nullable = true) String clientSecret,
        @Schema(description = "Next step for the patient", example = "Confirm the card payment on the client with Stripe.js")
        String instructions) {
}
