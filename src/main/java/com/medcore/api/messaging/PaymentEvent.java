package com.medcore.api.messaging;

import com.medcore.api.payment.Payment;
import com.medcore.api.payment.PaymentProvider;
import com.medcore.api.payment.PaymentStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentEvent(
        String topic,
        UUID paymentId,
        UUID appointmentId,
        UUID patientUserId,
        BigDecimal amount,
        String currency,
        PaymentProvider provider,
        PaymentStatus status,
        String failureReason) implements DomainEvent {

    public static PaymentEvent of(String topic, Payment p) {
        return new PaymentEvent(topic, p.getId(), p.getAppointment().getId(), p.getPatient().getUser().getId(),
                p.getAmount(), p.getCurrency(), p.getProvider(), p.getStatus(), p.getFailureReason());
    }

    @Override
    public String key() {
        return appointmentId.toString();
    }
}
