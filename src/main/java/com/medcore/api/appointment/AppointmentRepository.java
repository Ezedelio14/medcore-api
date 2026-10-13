package com.medcore.api.appointment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID>, JpaSpecificationExecutor<Appointment> {

    @EntityGraph(attributePaths = {"patient", "patient.user", "doctor", "doctor.user", "clinic"})
    Optional<Appointment> findWithDetailsById(UUID id);

    /** Search with the associations needed by AppointmentResponse fetched eagerly (avoids N+1 and lazy loading). */
    @Override
    @EntityGraph(attributePaths = {"patient", "doctor", "clinic"})
    Page<Appointment> findAll(Specification<Appointment> spec, Pageable pageable);

    /** True if the doctor already has an active appointment overlapping [start, end). */
    @Query("""
            select count(a) > 0 from Appointment a
            where a.doctor.id = :doctorId
              and a.status in :statuses
              and a.scheduledAt < :end and a.endsAt > :start
              and (:excludeId is null or a.id <> :excludeId)
            """)
    boolean existsOverlapping(UUID doctorId, Instant start, Instant end,
                              Collection<AppointmentStatus> statuses, UUID excludeId);

    @Query("""
            select a from Appointment a
            where a.doctor.id = :doctorId
              and a.status in :statuses
              and a.scheduledAt < :to and a.endsAt > :from
            order by a.scheduledAt
            """)
    List<Appointment> findActiveInRange(UUID doctorId, Instant from, Instant to, Collection<AppointmentStatus> statuses);

    @EntityGraph(attributePaths = {"patient", "patient.user", "doctor"})
    @Query("""
            select a from Appointment a
            where a.status = com.medcore.api.appointment.AppointmentStatus.CONFIRMED
              and a.reminderSentAt is null
              and a.scheduledAt between :from and :to
            """)
    List<Appointment> findDueForReminder(Instant from, Instant to);
}
