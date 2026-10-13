package com.medcore.api.appointment.state;

import com.medcore.api.appointment.AppointmentStatus;

/** Terminal state: every transition is rejected. */
final class NoShowState implements AppointmentState {

    @Override
    public AppointmentStatus status() {
        return AppointmentStatus.NO_SHOW;
    }
}
