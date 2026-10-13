package com.medcore.api.medicalrecord;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, UUID> {

    Page<MedicalRecord> findByPatientIdOrderByCreatedAtDesc(UUID patientId, Pageable pageable);
}
