package com.medcore.api.payment;

import com.medcore.api.appointment.Appointment;
import com.medcore.api.common.entity.AuditableEntity;
import com.medcore.api.patient.Patient;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentProvider provider;

    @Column(name = "provider_payment_id")
    private String providerPaymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentStatus status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "paid_at")
    private Instant paidAt;

    public static Payment initiate(Appointment appointment, PaymentProvider provider, String currency) {
        Payment payment = new Payment();
        payment.appointment = appointment;
        payment.patient = appointment.getPatient();
        payment.amount = appointment.getPrice();
        payment.currency = currency.toLowerCase();
        payment.provider = provider;
        payment.status = PaymentStatus.PENDING;
        return payment;
    }

    public void markSucceeded() {
        this.status = PaymentStatus.SUCCEEDED;
        this.paidAt = Instant.now();
        this.failureReason = null;
    }

    public void markFailed(String reason) {
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
    }

    public void markRefunded() {
        this.status = PaymentStatus.REFUNDED;
    }

    public boolean isFinal() {
        return status == PaymentStatus.SUCCEEDED || status == PaymentStatus.REFUNDED || status == PaymentStatus.CANCELLED;
    }
}
