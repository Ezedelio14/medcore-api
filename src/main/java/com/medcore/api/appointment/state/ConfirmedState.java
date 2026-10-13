package com.medcore.api.appointment.state;

import com.medcore.api.appointment.Appointment;
import com.medcore.api.appointment.AppointmentStatus;

import java.time.Instant;

final class ConfirmedState implements AppointmentState {

    @Override
    public AppointmentStatus status() {
        return AppointmentStatus.CONFIRMED;
    }

    @Override
    public void cancel(Appointment appointment, String reason) {
        appointment.applyCancellationReason(reason);
        appointment.applyStatus(AppointmentStatus.CANCELLED);
    }

    @Override
    public void complete(Appointment appointment) {
        if (appointment.getScheduledAt().isAfter(Instant.now())) {
            throw new InvalidStateTransitionException(status(), "complete a future");
        }
        appointment.applyStatus(AppointmentStatus.COMPLETED);
    }

    @Override
    public void markNoShow(Appointment appointment) {
        if (appointment.getScheduledAt().isAfter(Instant.now())) {
            throw new InvalidStateTransitionException(status(), "mark as no-show a future");
        }
        appointment.applyStatus(AppointmentStatus.NO_SHOW);
    }

    /** Rescheduling a confirmed appointment sends it back to PENDING for re-confirmation. */
    @Override
    public void reschedule(Appointment appointment, Instant newStart, int durationMinutes) {
        appointment.applySchedule(newStart, durationMinutes);
        appointment.applyStatus(AppointmentStatus.PENDING);
    }
}
