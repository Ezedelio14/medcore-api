package com.medcore.api.doctor;

import com.medcore.api.clinic.Clinic;
import com.medcore.api.common.entity.AuditableEntity;
import com.medcore.api.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "doctors")
@Getter
@Setter
@NoArgsConstructor
public class Doctor extends AuditableEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinic_id", nullable = false)
    private Clinic clinic;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "license_number", nullable = false, unique = true, length = 64)
    private String licenseNumber;

    private String biography;

    @Column(name = "consultation_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal consultationPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DoctorStatus status = DoctorStatus.ACTIVE;

    @ManyToMany
    @JoinTable(name = "doctor_specialties",
            joinColumns = @JoinColumn(name = "doctor_id"),
            inverseJoinColumns = @JoinColumn(name = "specialty_id"))
    private Set<Specialty> specialties = new HashSet<>();

    public String fullName() {
        return "Dr. " + firstName + " " + lastName;
    }

    public boolean isBookable() {
        return status == DoctorStatus.ACTIVE;
    }
}
