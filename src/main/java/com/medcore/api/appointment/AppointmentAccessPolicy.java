package com.medcore.api.appointment;

import com.medcore.api.clinic.ClinicStaffRepository;
import com.medcore.api.common.exception.ForbiddenException;
import com.medcore.api.security.UserPrincipal;
import com.medcore.api.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resource-level authorization for appointments (role checks alone are not enough:
 * a patient may only see their own appointments, staff only their clinic's, etc.).
 */
@Component
@RequiredArgsConstructor
public class AppointmentAccessPolicy {

    private final ClinicStaffRepository clinicStaffRepository;

    public boolean canView(UserPrincipal user, Appointment appointment) {
        return switch (user.role()) {
            case SUPER_ADMIN -> true;
            case PATIENT -> isPatient(user, appointment);
            case DOCTOR -> isDoctor(user, appointment);
            case RECEPTIONIST, CLINIC_ADMIN -> isClinicStaff(user, appointment.getClinic().getId());
        };
    }

    public boolean canManage(UserPrincipal user, Appointment appointment) {
        return switch (user.role()) {
            case SUPER_ADMIN -> true;
            case DOCTOR -> isDoctor(user, appointment);
            case RECEPTIONIST, CLINIC_ADMIN -> isClinicStaff(user, appointment.getClinic().getId());
            case PATIENT -> false;
        };
    }

    public boolean canCancelOrReschedule(UserPrincipal user, Appointment appointment) {
        return user.role() == Role.PATIENT ? isPatient(user, appointment) : canManage(user, appointment);
    }

    public boolean canBookAt(UserPrincipal user, UUID clinicId) {
        return switch (user.role()) {
            case SUPER_ADMIN, PATIENT -> true;
            case RECEPTIONIST, CLINIC_ADMIN -> isClinicStaff(user, clinicId);
            case DOCTOR -> false;
        };
    }

    public void require(boolean allowed) {
        if (!allowed) {
            throw new ForbiddenException("You are not allowed to access this appointment");
        }
    }

    /** Restricts appointment searches to what the caller is allowed to see. */
    public Specification<Appointment> visibilityScope(UserPrincipal user, UUID ownPatientId, UUID ownDoctorId) {
        return switch (user.role()) {
            case SUPER_ADMIN -> Specification.unrestricted();
            case PATIENT -> AppointmentSpecifications.forPatient(ownPatientId);
            case DOCTOR -> AppointmentSpecifications.forDoctor(ownDoctorId);
            case RECEPTIONIST, CLINIC_ADMIN ->
                    AppointmentSpecifications.forClinics(clinicStaffRepository.findClinicIdsByUserId(user.id()));
        };
    }

    private static boolean isPatient(UserPrincipal user, Appointment appointment) {
        return appointment.getPatient().getUser().getId().equals(user.id());
    }

    private static boolean isDoctor(UserPrincipal user, Appointment appointment) {
        return appointment.getDoctor().getUser().getId().equals(user.id());
    }

    private boolean isClinicStaff(UserPrincipal user, UUID clinicId) {
        return clinicStaffRepository.existsByClinicIdAndUserId(clinicId, user.id());
    }
}
