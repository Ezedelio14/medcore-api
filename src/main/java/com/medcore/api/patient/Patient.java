package com.medcore.api.patient;

import com.medcore.api.clinic.Clinic;
import com.medcore.api.common.entity.AuditableEntity;
import com.medcore.api.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "patients")
@Getter
@Setter
@NoArgsConstructor
public class Patient extends AuditableEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /** Primary clinic of the patient (optional: patients may book at any clinic). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clinic_id")
    private Clinic clinic;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Gender gender;

    @Column(length = 40)
    private String phone;

    @Column(name = "emergency_contact")
    private String emergencyContact;

    @Column(name = "blood_type", length = 4)
    private BloodType bloodType;

    private String allergies;

    @Column(name = "stripe_customer_id", length = 64)
    private String stripeCustomerId;

    public String fullName() {
        return firstName + " " + lastName;
    }
}
