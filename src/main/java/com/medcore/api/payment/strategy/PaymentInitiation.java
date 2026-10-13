package com.medcore.api.payment.strategy;

/**
 * What the client needs to complete a payment.
 *
 * @param clientSecret Stripe PaymentIntent client secret (online payments only)
 * @param instructions human-readable next step
 */
public record PaymentInitiation(String clientSecret, String instructions) {
}
