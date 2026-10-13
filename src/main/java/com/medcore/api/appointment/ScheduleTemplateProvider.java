package com.medcore.api.appointment;

import com.medcore.api.doctor.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Supplies a doctor's weekly working windows. Cached in Redis because availability lookups are by far
 * the hottest read path and schedules change rarely.
 */
@Component
@RequiredArgsConstructor
public class ScheduleTemplateProvider {

    public static final String CACHE = "doctor-schedules";

    private final ScheduleRepository scheduleRepository;

    @Cacheable(cacheNames = CACHE, key = "#doctorId")
    public List<ScheduleWindow> windowsFor(UUID doctorId) {
        return scheduleRepository.findByDoctorIdAndActiveTrue(doctorId).stream()
                .map(s -> new ScheduleWindow(s.getDayOfWeek(), s.getStartTime(), s.getEndTime(), s.getAppointmentDuration()))
                .toList();
    }

    public record ScheduleWindow(DayOfWeek dayOfWeek, LocalTime start, LocalTime end, int slotMinutes)
            implements Serializable {
    }
}
