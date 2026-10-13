package com.medcore.api.doctor;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface DoctorRepository extends JpaRepository<Doctor, UUID> {

    Optional<Doctor> findByUserId(UUID userId);

    /**
     * Serializes concurrent bookings for the same doctor. Combined with the DB exclusion
     * constraint this guarantees no double booking even under heavy contention.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Doctor d where d.id = :id")
    Optional<Doctor> findByIdForUpdate(UUID id);
}
