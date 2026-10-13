package com.medcore.api.doctor;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.UUID;

public interface ScheduleRepository extends JpaRepository<Schedule, UUID> {

    List<Schedule> findByDoctorIdAndActiveTrue(UUID doctorId);

    List<Schedule> findByDoctorIdAndDayOfWeekAndActiveTrueOrderByStartTime(UUID doctorId, DayOfWeek dayOfWeek);
}
