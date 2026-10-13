package com.medcore.api.appointment;

import com.medcore.api.appointment.dto.AppointmentResponse;
import com.medcore.api.appointment.dto.BookAppointmentRequest;
import com.medcore.api.appointment.dto.BookingResponse;
import com.medcore.api.payment.PaymentProvider;
import com.medcore.api.payment.PaymentService;
import com.medcore.api.payment.dto.PaymentInitiationResponse;
import com.medcore.api.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Facade pattern: one entry point for multi-step booking flows that span scheduling and payments,
 * so the controller does not orchestrate several services itself.
 *
 * <p>Intentionally not transactional: the appointment is committed first (releasing the doctor lock quickly),
 * then payment is initiated against the external gateway. If payment initiation fails the appointment stays
 * PENDING/UNPAID and the client can retry with {@code POST /api/v1/payments}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentBookingFacade {

    private final AppointmentService appointmentService;
    private final PaymentService paymentService;

    public BookingResponse book(UserPrincipal caller, BookAppointmentRequest request) {
        Appointment appointment = appointmentService.book(caller, request.doctorId(), request.patientId(),
                request.scheduledAt(), request.reason());

        PaymentProvider method = request.paymentMethod() != null ? request.paymentMethod() : PaymentProvider.STRIPE;
        PaymentInitiationResponse payment = null;
        try {
            payment = paymentService.initiate(caller, appointment.getId(), method);
        } catch (RuntimeException e) {
            log.warn("Payment initiation failed for appointment {}; client may retry", appointment.getId(), e);
        }
        return new BookingResponse(AppointmentResponse.from(appointmentService.get(caller, appointment.getId())), payment);
    }

    /** Cancels and, when the appointment was already paid, refunds it. */
    public AppointmentResponse cancel(UserPrincipal caller, UUID appointmentId, String reason) {
        Appointment cancelled = appointmentService.cancel(caller, appointmentId, reason);
        if (cancelled.isPaid()) {
            paymentService.refundIfPaid(appointmentId);
        }
        return AppointmentResponse.from(appointmentService.get(caller, appointmentId));
    }
}
