package com.medcore.api.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findByPatientIdOrderByCreatedAtDesc(UUID patientId);
}
