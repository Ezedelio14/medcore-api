package com.medcore.api.messaging;

import com.medcore.api.appointment.Appointment;
import com.medcore.api.appointment.AppointmentStatus;

import java.time.Instant;
import java.util.UUID;

public record AppointmentEvent(
        String topic,
        UUID appointmentId,
        UUID patientId,
        UUID patientUserId,
        UUID doctorId,
        UUID doctorUserId,
        UUID clinicId,
        String doctorName,
        Instant scheduledAt,
        AppointmentStatus status,
        String cancellationReason) implements DomainEvent {

    public static AppointmentEvent of(String topic, Appointment a) {
        return new AppointmentEvent(topic, a.getId(),
                a.getPatient().getId(), a.getPatient().getUser().getId(),
                a.getDoctor().getId(), a.getDoctor().getUser().getId(),
                a.getClinic().getId(), a.getDoctor().fullName(),
                a.getScheduledAt(), a.getStatus(), a.getCancellationReason());
    }

    @Override
    public String key() {
        return appointmentId.toString();
    }
}
