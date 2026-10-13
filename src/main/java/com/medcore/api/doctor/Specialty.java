package com.medcore.api.doctor;

import com.medcore.api.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "specialties")
@Getter
@Setter
@NoArgsConstructor
public class Specialty extends BaseEntity {

    @Column(nullable = false, unique = true, length = 120)
    private String name;

    private String description;
}
