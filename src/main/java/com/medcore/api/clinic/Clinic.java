package com.medcore.api.clinic;

import com.medcore.api.common.entity.AuditableEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Entity
@Table(name = "clinics")
@Getter
@Setter
@NoArgsConstructor
public class Clinic extends AuditableEntity {

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "legal_name")
    private String legalName;

    private String email;

    @Column(length = 40)
    private String phone;

    private String address;

    @Column(length = 120)
    private String city;

    /** ISO 3166-1 alpha-2 country code. */
    @Column(length = 2)
    private String country;

    /** IANA time zone id (e.g. Europe/Lisbon); schedules are interpreted in this zone. */
    @Column(nullable = false, length = 64)
    private String timezone = "UTC";

    /** Default consultation length in minutes, used when a schedule does not override it. */
    @Column(name = "default_appointment_duration", nullable = false)
    private int defaultAppointmentDuration = 30;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ClinicStatus status = ClinicStatus.ACTIVE;

    @ElementCollection
    @CollectionTable(name = "clinic_opening_hours", joinColumns = @JoinColumn(name = "clinic_id"))
    private Set<OpeningHours> openingHours = new HashSet<>();

    public ZoneId zoneId() {
        return ZoneId.of(timezone);
    }

    public Optional<OpeningHours> openingHoursOn(DayOfWeek day) {
        return openingHours.stream().filter(h -> h.getDayOfWeek() == day).findFirst();
    }
}
