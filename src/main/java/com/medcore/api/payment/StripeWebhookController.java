package com.medcore.api.payment;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Webhooks", description = "Inbound provider callbacks")
public class StripeWebhookController {

    private final PaymentService paymentService;

    @PostMapping("/api/v1/webhooks/stripe")
    @Operation(summary = "Stripe webhook",
            description = """
                    Receives Stripe events (payment_intent.succeeded, payment_intent.payment_failed,
                    payment_intent.canceled, charge.refunded). The `Stripe-Signature` header is verified against the
                    endpoint secret; the raw body must not be modified. Processing is idempotent per event id.""",
            security = {})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Event accepted (or ignored)"),
            @ApiResponse(responseCode = "400", description = "Invalid signature")
    })
    public ResponseEntity<Void> handle(
            @Parameter(description = "Raw Stripe event JSON") @RequestBody String payload,
            @Parameter(description = "Stripe signature header", required = true) @RequestHeader("Stripe-Signature") String signature) {
        paymentService.handleWebhook(payload, signature);
        return ResponseEntity.ok().build();
    }
}
