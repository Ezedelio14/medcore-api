package com.medcore.api.appointment.state;

import com.medcore.api.appointment.AppointmentStatus;
import com.medcore.api.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class InvalidStateTransitionException extends BusinessException {

    public InvalidStateTransitionException(AppointmentStatus current, String action) {
        super(HttpStatus.CONFLICT, "INVALID_STATE_TRANSITION",
                "Cannot %s an appointment in status %s".formatted(action, current));
    }
}
