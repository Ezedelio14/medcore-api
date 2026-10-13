package com.medcore.api.appointment.state;

import com.medcore.api.appointment.Appointment;
import com.medcore.api.appointment.AppointmentStatus;

import java.time.Instant;

/** Booked but not yet confirmed by the clinic (or awaiting payment). */
final class PendingState implements AppointmentState {

    @Override
    public AppointmentStatus status() {
        return AppointmentStatus.PENDING;
    }

    @Override
    public void confirm(Appointment appointment) {
        appointment.applyStatus(AppointmentStatus.CONFIRMED);
    }

    @Override
    public void cancel(Appointment appointment, String reason) {
        appointment.applyCancellationReason(reason);
        appointment.applyStatus(AppointmentStatus.CANCELLED);
    }

    @Override
    public void reschedule(Appointment appointment, Instant newStart, int durationMinutes) {
        appointment.applySchedule(newStart, durationMinutes);
    }
}
