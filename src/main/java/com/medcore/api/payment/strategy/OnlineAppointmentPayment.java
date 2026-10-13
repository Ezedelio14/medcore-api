package com.medcore.api.payment.strategy;

import com.medcore.api.integration.payment.PaymentGateway;
import com.medcore.api.integration.payment.PaymentGateway.PaymentIntentCommand;
import com.medcore.api.integration.payment.PaymentGateway.PaymentIntentResult;
import com.medcore.api.patient.Patient;
import com.medcore.api.payment.Payment;
import com.medcore.api.payment.PaymentProvider;
import com.medcore.api.payment.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Online card payment through the {@link PaymentGateway} (Stripe). The payment stays REQUIRES_ACTION until the
 * client confirms it with the returned client secret; the outcome arrives through the Stripe webhook.
 */
@Component
@RequiredArgsConstructor
public class OnlineAppointmentPayment implements PaymentStrategy {

    private final PaymentGateway paymentGateway;

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.STRIPE;
    }

    @Override
    public PaymentInitiation initiate(Payment payment) {
        Patient patient = payment.getPatient();
        if (patient.getStripeCustomerId() == null) {
            patient.setStripeCustomerId(paymentGateway.createCustomer(patient.getUser().getEmail(), patient.fullName(),
                    Map.of("patientId", patient.getId().toString())));
        }
        PaymentIntentResult intent = paymentGateway.createPaymentIntent(new PaymentIntentCommand(
                patient.getStripeCustomerId(),
                payment.getAmount(),
                payment.getCurrency(),
                "MedCore appointment " + payment.getAppointment().getId(),
                "payment-" + payment.getId(), // idempotency key: retries never create a second intent
                Map.of("paymentId", payment.getId().toString(),
                        "appointmentId", payment.getAppointment().getId().toString())));

        payment.setProviderPaymentId(intent.providerPaymentId());
        payment.setStatus(PaymentStatus.REQUIRES_ACTION);
        return new PaymentInitiation(intent.clientSecret(), "Confirm the card payment on the client with Stripe.js");
    }

    @Override
    public void refund(Payment payment) {
        paymentGateway.refund(payment.getProviderPaymentId(), payment.getAmount());
    }
}
