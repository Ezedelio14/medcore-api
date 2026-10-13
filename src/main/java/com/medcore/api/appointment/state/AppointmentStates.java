package com.medcore.api.appointment.state;

import com.medcore.api.appointment.AppointmentStatus;

import java.util.EnumMap;
import java.util.Map;

/** Registry mapping each persisted status to its (stateless, shared) state object. */
public final class AppointmentStates {

    private static final Map<AppointmentStatus, AppointmentState> STATES = new EnumMap<>(AppointmentStatus.class);

    static {
        register(new PendingState());
        register(new ConfirmedState());
        register(new CancelledState());
        register(new CompletedState());
        register(new NoShowState());
    }

    private AppointmentStates() {
    }

    private static void register(AppointmentState state) {
        STATES.put(state.status(), state);
    }

    public static AppointmentState of(AppointmentStatus status) {
        return STATES.get(status);
    }
}
