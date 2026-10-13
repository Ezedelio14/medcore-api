package com.medcore.api.appointment;

import com.medcore.api.appointment.ScheduleTemplateProvider.ScheduleWindow;
import com.medcore.api.clinic.Clinic;
import com.medcore.api.clinic.OpeningHours;
import com.medcore.api.common.exception.BusinessException;
import com.medcore.api.common.exception.ResourceNotFoundException;
import com.medcore.api.doctor.Doctor;
import com.medcore.api.doctor.DoctorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Computes free slots: weekly schedule windows, clipped to clinic opening hours, minus active appointments
 * and slots in the past. All wall-clock times are interpreted in the clinic's time zone.
 */
@Service
@RequiredArgsConstructor
public class AvailabilityService {

    private static final int MAX_RANGE_DAYS = 31;

    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final ScheduleTemplateProvider scheduleTemplateProvider;
    private final Clock clock;

    public record Availability(String timezone, List<Slot> slots) {
    }

    public record Slot(Instant start, Instant end) {
        boolean overlaps(Instant otherStart, Instant otherEnd) {
            return start.isBefore(otherEnd) && end.isAfter(otherStart);
        }
    }

    @Transactional(readOnly = true)
    public Availability freeSlots(UUID doctorId, LocalDate from, LocalDate to) {
        if (to.isBefore(from) || from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
            throw new BusinessException("INVALID_DATE_RANGE", "Date range must be ascending and at most %d days".formatted(MAX_RANGE_DAYS));
        }
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", doctorId));
        Clinic clinic = doctor.getClinic();
        if (!doctor.isBookable()) {
            return new Availability(clinic.getTimezone(), List.of());
        }
        ZoneId zone = clinic.zoneId();

        List<Slot> candidates = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            candidates.addAll(candidateSlots(doctorId, clinic, day));
        }
        if (candidates.isEmpty()) {
            return new Availability(clinic.getTimezone(), candidates);
        }

        Instant now = clock.instant();
        Instant rangeStart = from.atStartOfDay(zone).toInstant();
        Instant rangeEnd = to.plusDays(1).atStartOfDay(zone).toInstant();
        List<Appointment> booked = appointmentRepository.findActiveInRange(doctorId, rangeStart, rangeEnd, AppointmentStatus.ACTIVE);

        List<Slot> free = candidates.stream()
                .filter(slot -> slot.start().isAfter(now))
                .filter(slot -> booked.stream().noneMatch(a -> slot.overlaps(a.getScheduledAt(), a.getEndsAt())))
                .toList();
        return new Availability(clinic.getTimezone(), free);
    }

    /**
     * Returns the duration (minutes) of the schedule slot starting exactly at {@code start},
     * or empty if {@code start} is not a valid slot boundary for the doctor.
     */
    @Transactional(readOnly = true)
    public Optional<Integer> slotDurationAt(Doctor doctor, Instant start) {
        Clinic clinic = doctor.getClinic();
        LocalDate day = start.atZone(clinic.zoneId()).toLocalDate();
        return candidateSlots(doctor.getId(), clinic, day).stream()
                .filter(slot -> slot.start().equals(start))
                .findFirst()
                .map(slot -> (int) Duration.between(slot.start(), slot.end()).toMinutes());
    }

    private List<Slot> candidateSlots(UUID doctorId, Clinic clinic, LocalDate day) {
        ZoneId zone = clinic.zoneId();
        Optional<OpeningHours> openingHours = clinic.openingHoursOn(day.getDayOfWeek());
        boolean clinicDefinesHours = !clinic.getOpeningHours().isEmpty();
        if (clinicDefinesHours && openingHours.isEmpty()) {
            return List.of(); // clinic closed that day
        }

        List<Slot> slots = new ArrayList<>();
        for (ScheduleWindow window : scheduleTemplateProvider.windowsFor(doctorId)) {
            if (window.dayOfWeek() != day.getDayOfWeek()) {
                continue;
            }
            LocalTime cursor = window.start();
            while (!cursor.plusMinutes(window.slotMinutes()).isAfter(window.end())
                    && cursor.plusMinutes(window.slotMinutes()).isAfter(cursor)) { // guards midnight wrap-around
                LocalTime slotStart = cursor;
                LocalTime slotEnd = cursor.plusMinutes(window.slotMinutes());
                if (openingHours.map(h -> h.covers(slotStart, slotEnd)).orElse(true)) {
                    slots.add(new Slot(day.atTime(slotStart).atZone(zone).toInstant(), day.atTime(slotEnd).atZone(zone).toInstant()));
                }
                cursor = slotEnd;
            }
        }
        slots.sort((a, b) -> a.start().compareTo(b.start()));
        return slots;
    }
}
