package com.medcore.api.appointment;

import com.medcore.api.appointment.dto.AppointmentFilter;
import com.medcore.api.common.exception.BusinessException;
import com.medcore.api.common.exception.ConflictException;
import com.medcore.api.common.exception.ResourceNotFoundException;
import com.medcore.api.config.MedcoreProperties;
import com.medcore.api.doctor.Doctor;
import com.medcore.api.doctor.DoctorRepository;
import com.medcore.api.messaging.AppointmentEvent;
import com.medcore.api.messaging.KafkaTopics;
import com.medcore.api.patient.Patient;
import com.medcore.api.patient.PatientRepository;
import com.medcore.api.security.UserPrincipal;
import com.medcore.api.user.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.medcore.api.appointment.AppointmentSpecifications.forClinic;
import static com.medcore.api.appointment.AppointmentSpecifications.forDoctor;
import static com.medcore.api.appointment.AppointmentSpecifications.forPatient;
import static com.medcore.api.appointment.AppointmentSpecifications.scheduledFrom;
import static com.medcore.api.appointment.AppointmentSpecifications.scheduledUntil;
import static com.medcore.api.appointment.AppointmentSpecifications.withPaymentStatus;
import static com.medcore.api.appointment.AppointmentSpecifications.withStatus;

/**
 * Appointment use cases. Double booking is prevented in three layers:
 * the slot must be free in {@link AvailabilityService}, the doctor row is locked (PESSIMISTIC_WRITE) while
 * the overlap check + insert run, and a PostgreSQL exclusion constraint rejects any overlap that slips through.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final AvailabilityService availabilityService;
    private final AppointmentAccessPolicy accessPolicy;
    private final ApplicationEventPublisher events;
    private final MedcoreProperties properties;
    private final Clock clock;

    @Transactional
    public Appointment book(UserPrincipal caller, UUID doctorId, UUID requestedPatientId, Instant scheduledAt, String reason) {
        Patient patient = resolvePatient(caller, requestedPatientId);
        Doctor doctor = doctorRepository.findByIdForUpdate(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", doctorId));
        accessPolicy.require(accessPolicy.canBookAt(caller, doctor.getClinic().getId()));
        if (!doctor.isBookable()) {
            throw new BusinessException("DOCTOR_UNAVAILABLE", "This doctor is not accepting appointments");
        }

        int duration = requireFreeSlot(doctor, scheduledAt, null);
        Appointment appointment = appointmentRepository.save(Appointment.book(patient, doctor, scheduledAt, duration, reason));
        appointmentRepository.flush(); // surface exclusion-constraint violations inside this method

        log.info("Appointment {} booked for doctor {} at {}", appointment.getId(), doctorId, scheduledAt);
        events.publishEvent(AppointmentEvent.of(KafkaTopics.APPOINTMENT_CREATED, appointment));
        return appointment;
    }

    @Transactional(readOnly = true)
    public Appointment get(UserPrincipal caller, UUID id) {
        Appointment appointment = load(id);
        accessPolicy.require(accessPolicy.canView(caller, appointment));
        return appointment;
    }

    @Transactional(readOnly = true)
    public Page<Appointment> search(UserPrincipal caller, AppointmentFilter filter, Pageable pageable) {
        UUID ownPatientId = caller.role() == Role.PATIENT
                ? patientRepository.findByUserId(caller.id()).map(Patient::getId).orElse(null) : null;
        UUID ownDoctorId = caller.role() == Role.DOCTOR
                ? doctorRepository.findByUserId(caller.id()).map(Doctor::getId).orElse(null) : null;
        if ((caller.role() == Role.PATIENT && ownPatientId == null) || (caller.role() == Role.DOCTOR && ownDoctorId == null)) {
            return Page.empty(pageable);
        }

        Specification<Appointment> spec = Specification.allOf(
                accessPolicy.visibilityScope(caller, ownPatientId, ownDoctorId),
                forDoctor(filter.doctorId()),
                forPatient(filter.patientId()),
                forClinic(filter.clinicId()),
                withStatus(filter.status()),
                withPaymentStatus(filter.paymentStatus()),
                scheduledFrom(filter.from()),
                scheduledUntil(filter.to()));
        return appointmentRepository.findAll(spec, pageable);
    }

    @Transactional
    public Appointment confirm(UserPrincipal caller, UUID id) {
        Appointment appointment = load(id);
        accessPolicy.require(accessPolicy.canManage(caller, appointment));
        appointment.confirm();
        events.publishEvent(AppointmentEvent.of(KafkaTopics.APPOINTMENT_CONFIRMED, appointment));
        return appointment;
    }

    /** System-initiated confirmation (e.g. after a successful online payment). */
    @Transactional
    public void confirmAfterPayment(Appointment appointment) {
        if (appointment.getStatus() == AppointmentStatus.PENDING) {
            appointment.confirm();
            events.publishEvent(AppointmentEvent.of(KafkaTopics.APPOINTMENT_CONFIRMED, appointment));
        }
    }

    @Transactional
    public Appointment cancel(UserPrincipal caller, UUID id, String reason) {
        Appointment appointment = load(id);
        accessPolicy.require(accessPolicy.canCancelOrReschedule(caller, appointment));
        if (caller.role() == Role.PATIENT) {
            requireMinimumNotice(appointment, "cancel");
        }
        appointment.cancel(reason);
        events.publishEvent(AppointmentEvent.of(KafkaTopics.APPOINTMENT_CANCELLED, appointment));
        return appointment;
    }

    @Transactional
    public Appointment reschedule(UserPrincipal caller, UUID id, Instant newStart) {
        Appointment appointment = load(id);
        accessPolicy.require(accessPolicy.canCancelOrReschedule(caller, appointment));
        if (caller.role() == Role.PATIENT) {
            requireMinimumNotice(appointment, "reschedule");
        }
        Doctor doctor = doctorRepository.findByIdForUpdate(appointment.getDoctor().getId()).orElseThrow();
        int duration = requireFreeSlot(doctor, newStart, appointment.getId());
        appointment.reschedule(newStart, duration);
        appointmentRepository.flush();
        events.publishEvent(AppointmentEvent.of(KafkaTopics.APPOINTMENT_RESCHEDULED, appointment));
        return appointment;
    }

    @Transactional
    public Appointment complete(UserPrincipal caller, UUID id) {
        Appointment appointment = load(id);
        accessPolicy.require(accessPolicy.canManage(caller, appointment));
        appointment.complete();
        events.publishEvent(AppointmentEvent.of(KafkaTopics.APPOINTMENT_COMPLETED, appointment));
        return appointment;
    }

    @Transactional
    public Appointment markNoShow(UserPrincipal caller, UUID id) {
        Appointment appointment = load(id);
        accessPolicy.require(accessPolicy.canManage(caller, appointment));
        appointment.markNoShow();
        return appointment;
    }

    Appointment load(UUID id) {
        return appointmentRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", id));
    }

    /** Validates the slot boundary and the absence of overlaps; returns the slot duration in minutes. */
    private int requireFreeSlot(Doctor doctor, Instant start, UUID excludeAppointmentId) {
        if (!start.isAfter(clock.instant())) {
            throw new BusinessException("SLOT_IN_PAST", "Appointments must be scheduled in the future");
        }
        int duration = availabilityService.slotDurationAt(doctor, start)
                .orElseThrow(() -> new BusinessException("INVALID_SLOT",
                        "The requested time does not match any of the doctor's available slots"));
        Instant end = start.plus(Duration.ofMinutes(duration));
        if (appointmentRepository.existsOverlapping(doctor.getId(), start, end, AppointmentStatus.ACTIVE, excludeAppointmentId)) {
            throw new ConflictException("SLOT_UNAVAILABLE", "The selected time slot is no longer available");
        }
        return duration;
    }

    private void requireMinimumNotice(Appointment appointment, String action) {
        Duration notice = properties.appointments().cancellationMinNotice();
        if (Duration.between(clock.instant(), appointment.getScheduledAt()).compareTo(notice) < 0) {
            throw new BusinessException("INSUFFICIENT_NOTICE",
                    "Patients must %s at least %d hours in advance; please contact the clinic".formatted(action, notice.toHours()));
        }
    }

    private Patient resolvePatient(UserPrincipal caller, UUID requestedPatientId) {
        if (caller.role() == Role.PATIENT) {
            return patientRepository.findByUserId(caller.id())
                    .orElseThrow(() -> new BusinessException("PATIENT_PROFILE_MISSING", "Complete your patient profile before booking"));
        }
        if (requestedPatientId == null) {
            throw new BusinessException("PATIENT_REQUIRED", "patientId is required when booking on behalf of a patient");
        }
        return patientRepository.findById(requestedPatientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", requestedPatientId));
    }
}
