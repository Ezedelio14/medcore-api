package com.medcore.api.payment;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    @EntityGraph(attributePaths = {"appointment", "patient", "patient.user"})
    Optional<Payment> findByProviderPaymentId(String providerPaymentId);

    @EntityGraph(attributePaths = {"appointment", "patient", "patient.user"})
    Optional<Payment> findWithDetailsById(UUID id);

    List<Payment> findByAppointmentIdOrderByCreatedAtDesc(UUID appointmentId);
}
