package com.medcore.api.clinic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ClinicRepository extends JpaRepository<Clinic, UUID> {
}
