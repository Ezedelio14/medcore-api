package com.medcore.api.appointment.state;

import com.medcore.api.appointment.AppointmentStatus;

/** Terminal state: every transition is rejected. */
final class CompletedState implements AppointmentState {

    @Override
    public AppointmentStatus status() {
        return AppointmentStatus.COMPLETED;
    }
}
