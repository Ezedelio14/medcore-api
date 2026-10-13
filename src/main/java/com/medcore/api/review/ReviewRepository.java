package com.medcore.api.review;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    @Query("select avg(r.rating) from Review r where r.doctor.id = :doctorId")
    Double averageRatingForDoctor(UUID doctorId);
}
