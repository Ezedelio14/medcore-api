package com.medcore.api.payment.strategy;

import com.medcore.api.common.exception.BusinessException;
import com.medcore.api.payment.Payment;
import com.medcore.api.payment.PaymentProvider;

/**
 * Strategy pattern: each way of paying for an appointment (online card, at the clinic, insurance)
 * encapsulates its own initiation, settlement and refund rules behind this interface.
 * {@link PaymentStrategyResolver} picks the strategy at runtime from the requested {@link PaymentProvider}.
 */
public interface PaymentStrategy {

    PaymentProvider provider();

    /** Starts the payment; may set {@code providerPaymentId} and status on the (already persisted) payment. */
    PaymentInitiation initiate(Payment payment);

    /** Settles a payment confirmed outside an automated gateway (front desk, insurer authorization). */
    default void confirmManually(Payment payment) {
        throw new BusinessException("MANUAL_CONFIRMATION_NOT_SUPPORTED",
                "%s payments are confirmed automatically by the provider".formatted(provider()));
    }

    /** Returns the money to the patient. Implementations must be safe to call once per succeeded payment. */
    void refund(Payment payment);
}
