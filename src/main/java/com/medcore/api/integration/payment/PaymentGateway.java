package com.medcore.api.integration.payment;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * Port for card payment providers. Domain code talks in BigDecimal amounts and plain records;
 * the adapter translates to the provider SDK (minor units, SDK objects, exceptions).
 */
public interface PaymentGateway {

    String createCustomer(String email, String fullName, Map<String, String> metadata);

    PaymentIntentResult createPaymentIntent(PaymentIntentCommand command);

    RefundResult refund(String providerPaymentId, BigDecimal amount);

    /** Verifies the webhook signature and returns the parsed event, or empty if the event type is irrelevant. */
    Optional<GatewayEvent> parseWebhook(String payload, String signatureHeader);

    record PaymentIntentCommand(String customerId, BigDecimal amount, String currency, String description,
                                String idempotencyKey, Map<String, String> metadata) {
    }

    record PaymentIntentResult(String providerPaymentId, String clientSecret, String status) {
    }

    record RefundResult(String refundId, String status) {
    }

    record GatewayEvent(String eventId, Type type, String providerPaymentId, String failureMessage) {
        public enum Type {PAYMENT_SUCCEEDED, PAYMENT_FAILED, PAYMENT_CANCELED, CHARGE_REFUNDED}
    }
}
