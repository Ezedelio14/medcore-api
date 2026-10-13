package com.medcore.api.doctor;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpecialtyRepository extends JpaRepository<Specialty, UUID> {
}
