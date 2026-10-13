package com.medcore.api.clinic;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ClinicStaffRepository extends JpaRepository<ClinicStaff, UUID> {

    boolean existsByClinicIdAndUserId(UUID clinicId, UUID userId);

    @Query("select s.clinic.id from ClinicStaff s where s.user.id = :userId")
    List<UUID> findClinicIdsByUserId(UUID userId);
}
