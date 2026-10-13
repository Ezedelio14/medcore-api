package com.medcore.api.messaging;

import java.util.UUID;

public record PrescriptionEvent(String topic, UUID prescriptionId, UUID patientUserId, UUID doctorId)
        implements DomainEvent {

    @Override
    public String key() {
        return prescriptionId.toString();
    }
}
