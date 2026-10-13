package com.medcore.api.appointment;

import com.medcore.api.appointment.state.AppointmentState;
import com.medcore.api.appointment.state.AppointmentStates;
import com.medcore.api.clinic.Clinic;
import com.medcore.api.common.entity.AuditableEntity;
import com.medcore.api.doctor.Doctor;
import com.medcore.api.patient.Patient;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * Appointment aggregate. Lifecycle transitions are delegated to an {@link AppointmentState}
 * (State pattern), so illegal transitions (e.g. completing a cancelled appointment) are rejected in one place.
 */
@Entity
@Table(name = "appointments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Appointment extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinic_id", nullable = false)
    private Clinic clinic;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    /** Duration in minutes. */
    @Column(nullable = false)
    private int duration;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AppointmentStatus status;

    private String reason;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 32)
    private AppointmentPaymentStatus paymentStatus;

    @Column(name = "reminder_sent_at")
    private Instant reminderSentAt;

    @Version
    private long version;

    public static Appointment book(Patient patient, Doctor doctor, Instant scheduledAt, int durationMinutes, String reason) {
        Appointment appointment = new Appointment();
        appointment.patient = patient;
        appointment.doctor = doctor;
        appointment.clinic = doctor.getClinic();
        appointment.reason = reason;
        appointment.price = doctor.getConsultationPrice();
        appointment.status = AppointmentStatus.PENDING;
        appointment.paymentStatus = AppointmentPaymentStatus.UNPAID;
        appointment.applySchedule(scheduledAt, durationMinutes);
        return appointment;
    }

    // --- lifecycle: delegated to the current state -------------------------------------------

    public AppointmentState state() {
        return AppointmentStates.of(status);
    }

    public void confirm() {
        state().confirm(this);
    }

    public void cancel(String reason) {
        state().cancel(this, reason);
    }

    public void complete() {
        state().complete(this);
    }

    public void markNoShow() {
        state().markNoShow(this);
    }

    public void reschedule(Instant newStart, int durationMinutes) {
        state().reschedule(this, newStart, durationMinutes);
    }

    // --- mutators used by the state objects ---------------------------------------------------

    public void applyStatus(AppointmentStatus newStatus) {
        this.status = newStatus;
    }

    public void applySchedule(Instant start, int durationMinutes) {
        this.scheduledAt = start;
        this.duration = durationMinutes;
        this.endsAt = start.plus(Duration.ofMinutes(durationMinutes));
        this.reminderSentAt = null;
    }

    public void applyCancellationReason(String reason) {
        this.cancellationReason = reason;
    }

    // --- payment & reminders ------------------------------------------------------------------

    public void updatePaymentStatus(AppointmentPaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public void markReminderSent(Instant at) {
        this.reminderSentAt = at;
    }

    public boolean isPaid() {
        return paymentStatus == AppointmentPaymentStatus.PAID;
    }
}
