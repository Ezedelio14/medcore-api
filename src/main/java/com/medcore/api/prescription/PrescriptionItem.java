package com.medcore.api.prescription;

import com.medcore.api.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "prescription_items")
@Getter
@Setter
@NoArgsConstructor
public class PrescriptionItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prescription_id", nullable = false)
    private Prescription prescription;

    @Column(name = "medication_name", nullable = false)
    private String medicationName;

    @Column(nullable = false, length = 120)
    private String dosage;

    @Column(nullable = false, length = 120)
    private String frequency;

    @Column(nullable = false, length = 120)
    private String duration;

    private String instructions;
}
