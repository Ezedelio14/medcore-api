package com.medcore.api.payment.strategy;

import com.medcore.api.payment.Payment;
import com.medcore.api.payment.PaymentProvider;
import org.springframework.stereotype.Component;

/** Paid in person at the front desk; a receptionist confirms the payment once received. */
@Component
public class ClinicPayment implements PaymentStrategy {

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.CLINIC;
    }

    @Override
    public PaymentInitiation initiate(Payment payment) {
        return new PaymentInitiation(null, "Pay %s %s at the clinic reception on the day of the appointment"
                .formatted(payment.getAmount(), payment.getCurrency().toUpperCase()));
    }

    @Override
    public void confirmManually(Payment payment) {
        payment.markSucceeded();
    }

    @Override
    public void refund(Payment payment) {
        // Cash/terminal refunds are handled by the front desk; the system only records the outcome.
    }
}
