package com.medcore.api.appointment;

import com.medcore.api.clinic.Clinic;
import com.medcore.api.common.exception.BusinessException;
import com.medcore.api.common.exception.ConflictException;
import com.medcore.api.common.exception.ForbiddenException;
import com.medcore.api.config.MedcoreProperties;
import com.medcore.api.doctor.Doctor;
import com.medcore.api.doctor.DoctorRepository;
import com.medcore.api.messaging.AppointmentEvent;
import com.medcore.api.messaging.KafkaTopics;
import com.medcore.api.patient.Patient;
import com.medcore.api.patient.PatientRepository;
import com.medcore.api.security.UserPrincipal;
import com.medcore.api.support.TestFixtures;
import com.medcore.api.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    private static final Instant SLOT = Instant.parse("2026-11-03T09:30:00Z");

    @Mock private AppointmentRepository appointmentRepository;
    @Mock private DoctorRepository doctorRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private AvailabilityService availabilityService;
    @Mock private AppointmentAccessPolicy accessPolicy;
    @Mock private ApplicationEventPublisher events;

    private AppointmentService service;
    private Doctor doctor;
    private Patient patient;
    private UserPrincipal patientPrincipal;

    @BeforeEach
    void setUp() {
        MedcoreProperties properties = new MedcoreProperties(null, null, null, null, null, null,
                new MedcoreProperties.Appointments(Duration.ofHours(24), "0 * * * * *", Duration.ofHours(2)), null);
        service = new AppointmentService(appointmentRepository, doctorRepository, patientRepository, availabilityService,
                accessPolicy, events, properties, Clock.fixed(NOW, ZoneOffset.UTC));

        Clinic clinic = TestFixtures.clinic();
        doctor = TestFixtures.doctor(clinic);
        patient = TestFixtures.patient();
        patientPrincipal = new UserPrincipal(patient.getUser().getId(), "p@medcore.test", Role.PATIENT, "jti", NOW.plusSeconds(900));
    }

    @Test
    void booksFreeSlotAndPublishesCreatedEvent() {
        stubBookingPreconditions();
        when(availabilityService.slotDurationAt(doctor, SLOT)).thenReturn(Optional.of(30));
        when(appointmentRepository.existsOverlapping(eq(doctor.getId()), eq(SLOT), eq(SLOT.plusSeconds(1800)), any(), isNull()))
                .thenReturn(false);
        when(appointmentRepository.save(any())).thenAnswer(inv -> TestFixtures.withId(inv.getArgument(0)));

        Appointment booked = service.book(patientPrincipal, doctor.getId(), null, SLOT, "Headache");

        assertThat(booked.getStatus()).isEqualTo(AppointmentStatus.PENDING);
        assertThat(booked.getPaymentStatus()).isEqualTo(AppointmentPaymentStatus.UNPAID);
        assertThat(booked.getPrice()).isEqualByComparingTo(doctor.getConsultationPrice());
        assertThat(booked.getEndsAt()).isEqualTo(SLOT.plus(Duration.ofMinutes(30)));

        ArgumentCaptor<AppointmentEvent> event = ArgumentCaptor.forClass(AppointmentEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().topic()).isEqualTo(KafkaTopics.APPOINTMENT_CREATED);
        assertThat(event.getValue().appointmentId()).isEqualTo(booked.getId());
    }

    @Test
    void rejectsOverlappingBooking() {
        stubBookingPreconditions();
        when(availabilityService.slotDurationAt(doctor, SLOT)).thenReturn(Optional.of(30));
        when(appointmentRepository.existsOverlapping(any(), any(), any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.book(patientPrincipal, doctor.getId(), null, SLOT, null))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("SLOT_UNAVAILABLE");
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void rejectsTimeOutsideDoctorSchedule() {
        stubBookingPreconditions();
        when(availabilityService.slotDurationAt(doctor, SLOT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.book(patientPrincipal, doctor.getId(), null, SLOT, null))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("INVALID_SLOT");
    }

    @Test
    void rejectsBookingByUnauthorizedStaff() {
        UserPrincipal receptionist = new UserPrincipal(TestFixtures.user(Role.RECEPTIONIST).getId(), "r@medcore.test",
                Role.RECEPTIONIST, "jti", NOW.plusSeconds(900));
        when(patientRepository.findById(patient.getId())).thenReturn(Optional.of(patient));
        when(doctorRepository.findByIdForUpdate(doctor.getId())).thenReturn(Optional.of(doctor));
        when(accessPolicy.canBookAt(receptionist, doctor.getClinic().getId())).thenReturn(false);
        doCallRealMethod().when(accessPolicy).require(false);

        assertThatThrownBy(() -> service.book(receptionist, doctor.getId(), patient.getId(), SLOT, null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void patientCannotCancelWithinMinimumNotice() {
        Appointment soon = TestFixtures.withId(Appointment.book(patient, doctor, NOW.plus(Duration.ofMinutes(90)), 30, null));
        when(appointmentRepository.findWithDetailsById(soon.getId())).thenReturn(Optional.of(soon));
        when(accessPolicy.canCancelOrReschedule(patientPrincipal, soon)).thenReturn(true);

        assertThatThrownBy(() -> service.cancel(patientPrincipal, soon.getId(), "Sick"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("INSUFFICIENT_NOTICE");
        assertThat(soon.getStatus()).isEqualTo(AppointmentStatus.PENDING);
    }

    private void stubBookingPreconditions() {
        when(patientRepository.findByUserId(patientPrincipal.id())).thenReturn(Optional.of(patient));
        when(doctorRepository.findByIdForUpdate(doctor.getId())).thenReturn(Optional.of(doctor));
        when(accessPolicy.canBookAt(patientPrincipal, doctor.getClinic().getId())).thenReturn(true);
    }
}
