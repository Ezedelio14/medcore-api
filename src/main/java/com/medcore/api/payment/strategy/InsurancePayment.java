package com.medcore.api.payment.strategy;

import com.medcore.api.payment.Payment;
import com.medcore.api.payment.PaymentProvider;
import org.springframework.stereotype.Component;

/**
 * Covered by the patient's health insurance. A claim reference is generated and the payment is settled
 * when clinic staff record the insurer's authorization.
 */
@Component
public class InsurancePayment implements PaymentStrategy {

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.INSURANCE;
    }

    @Override
    public PaymentInitiation initiate(Payment payment) {
        payment.setProviderPaymentId("CLAIM-" + payment.getId());
        return new PaymentInitiation(null,
                "Insurance claim %s created; bring your insurance card to the appointment".formatted(payment.getProviderPaymentId()));
    }

    @Override
    public void confirmManually(Payment payment) {
        payment.markSucceeded();
    }

    @Override
    public void refund(Payment payment) {
        // Claim reversal is handled with the insurer out of band.
    }
}
