package com.medcore.api.prescription;

import com.medcore.api.common.entity.BaseEntity;
import com.medcore.api.doctor.Doctor;
import com.medcore.api.medicalrecord.MedicalRecord;
import com.medcore.api.patient.Patient;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "prescriptions")
@Getter
@Setter
@NoArgsConstructor
public class Prescription extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medical_record_id")
    private MedicalRecord medicalRecord;

    @Column(name = "issued_at")
    private Instant issuedAt;

    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PrescriptionStatus status = PrescriptionStatus.DRAFT;

    @OneToMany(mappedBy = "prescription", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PrescriptionItem> items = new ArrayList<>();

    public void addItem(PrescriptionItem item) {
        item.setPrescription(this);
        items.add(item);
    }

    public void issue() {
        if (items.isEmpty()) {
            throw new IllegalStateException("A prescription needs at least one item");
        }
        this.status = PrescriptionStatus.ISSUED;
        this.issuedAt = Instant.now();
    }
}
