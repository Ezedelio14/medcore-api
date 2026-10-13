package com.medcore.api.appointment;

import java.util.EnumSet;
import java.util.Set;

public enum AppointmentStatus {
    PENDING, CONFIRMED, CANCELLED, COMPLETED, NO_SHOW;

    /** Statuses that occupy a slot in the doctor's agenda (mirrors the DB exclusion constraint). */
    public static final Set<AppointmentStatus> ACTIVE = EnumSet.of(PENDING, CONFIRMED);

    public boolean isActive() {
        return ACTIVE.contains(this);
    }
}
