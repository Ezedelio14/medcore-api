package com.medcore.api.appointment.state;

import com.medcore.api.appointment.Appointment;
import com.medcore.api.appointment.AppointmentStatus;

import java.time.Instant;

/**
 * State pattern: each appointment status is a state object that decides which transitions are allowed.
 * Default implementations reject the transition; concrete states override only what they permit.
 */
public interface AppointmentState {

    AppointmentStatus status();

    default void confirm(Appointment appointment) {
        throw new InvalidStateTransitionException(status(), "confirm");
    }

    default void cancel(Appointment appointment, String reason) {
        throw new InvalidStateTransitionException(status(), "cancel");
    }

    default void complete(Appointment appointment) {
        throw new InvalidStateTransitionException(status(), "complete");
    }

    default void markNoShow(Appointment appointment) {
        throw new InvalidStateTransitionException(status(), "mark as no-show");
    }

    default void reschedule(Appointment appointment, Instant newStart, int durationMinutes) {
        throw new InvalidStateTransitionException(status(), "reschedule");
    }
}
