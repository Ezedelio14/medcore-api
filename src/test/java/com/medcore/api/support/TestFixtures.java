package com.medcore.api.support;

import com.medcore.api.appointment.Appointment;
import com.medcore.api.clinic.Clinic;
import com.medcore.api.common.entity.BaseEntity;
import com.medcore.api.doctor.Doctor;
import com.medcore.api.patient.Patient;
import com.medcore.api.user.Role;
import com.medcore.api.user.User;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** In-memory entity builders for unit tests (ids are assigned reflectively, as JPA would). */
public final class TestFixtures {

    private TestFixtures() {
    }

    public static <T extends BaseEntity> T withId(T entity) {
        ReflectionTestUtils.setField(entity, BaseEntity.class, "id", UUID.randomUUID(), UUID.class);
        return entity;
    }

    public static User user(Role role) {
        return withId(new User(role.name().toLowerCase() + "@medcore.test", "{noop}secret", role));
    }

    public static Clinic clinic() {
        Clinic clinic = withId(new Clinic());
        clinic.setName("MedCore Lisboa");
        clinic.setTimezone("Europe/Lisbon");
        return clinic;
    }

    public static Doctor doctor(Clinic clinic) {
        Doctor doctor = withId(new Doctor());
        doctor.setUser(user(Role.DOCTOR));
        doctor.setClinic(clinic);
        doctor.setFirstName("Rita");
        doctor.setLastName("Costa");
        doctor.setLicenseNumber("LIC-" + UUID.randomUUID());
        doctor.setConsultationPrice(new BigDecimal("60.00"));
        return doctor;
    }

    public static Patient patient() {
        Patient patient = withId(new Patient());
        patient.setUser(user(Role.PATIENT));
        patient.setFirstName("Ana");
        patient.setLastName("Silva");
        return patient;
    }

    public static Appointment appointment(Instant start) {
        Clinic clinic = clinic();
        return withId(Appointment.book(patient(), doctor(clinic), start, 30, "Check-up"));
    }
}
