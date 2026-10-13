package com.medcore.api.clinic;

import com.medcore.api.common.entity.BaseEntity;
import com.medcore.api.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** Membership of a receptionist / clinic admin in a clinic. */
@Entity
@Table(name = "clinic_staff")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClinicStaff extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinic_id", nullable = false)
    private Clinic clinic;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ClinicStaff(Clinic clinic, User user) {
        this.clinic = clinic;
        this.user = user;
    }
}
