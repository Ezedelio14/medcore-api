package com.medcore.api.appointment;

import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

/**
 * Specification pattern: small composable query predicates. Each factory returns
 * {@link Specification#unrestricted()} when its criterion is absent, so callers can
 * combine optional filters with {@link Specification#allOf} without branching.
 */
public final class AppointmentSpecifications {

    private AppointmentSpecifications() {
    }

    public static Specification<Appointment> forPatient(UUID patientId) {
        return patientId == null ? Specification.unrestricted() : (root, query, cb) -> cb.equal(root.get("patient").get("id"), patientId);
    }

    public static Specification<Appointment> forDoctor(UUID doctorId) {
        return doctorId == null ? Specification.unrestricted() : (root, query, cb) -> cb.equal(root.get("doctor").get("id"), doctorId);
    }

    public static Specification<Appointment> forClinic(UUID clinicId) {
        return clinicId == null ? Specification.unrestricted() : (root, query, cb) -> cb.equal(root.get("clinic").get("id"), clinicId);
    }

    public static Specification<Appointment> forClinics(Collection<UUID> clinicIds) {
        return clinicIds == null ? Specification.unrestricted() : (root, query, cb) -> root.get("clinic").get("id").in(clinicIds);
    }

    public static Specification<Appointment> withStatus(AppointmentStatus status) {
        return status == null ? Specification.unrestricted() : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Appointment> withPaymentStatus(AppointmentPaymentStatus paymentStatus) {
        return paymentStatus == null ? Specification.unrestricted() : (root, query, cb) -> cb.equal(root.get("paymentStatus"), paymentStatus);
    }

    public static Specification<Appointment> scheduledFrom(Instant from) {
        return from == null ? Specification.unrestricted() : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("scheduledAt"), from);
    }

    public static Specification<Appointment> scheduledUntil(Instant to) {
        return to == null ? Specification.unrestricted() : (root, query, cb) -> cb.lessThan(root.get("scheduledAt"), to);
    }
}
