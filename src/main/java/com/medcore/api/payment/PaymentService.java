package com.medcore.api.payment;

import com.medcore.api.appointment.Appointment;
import com.medcore.api.appointment.AppointmentAccessPolicy;
import com.medcore.api.appointment.AppointmentPaymentStatus;
import com.medcore.api.appointment.AppointmentRepository;
import com.medcore.api.appointment.AppointmentService;
import com.medcore.api.common.exception.BusinessException;
import com.medcore.api.common.exception.ConflictException;
import com.medcore.api.common.exception.ResourceNotFoundException;
import com.medcore.api.config.MedcoreProperties;
import com.medcore.api.integration.payment.PaymentGateway;
import com.medcore.api.integration.payment.PaymentGateway.GatewayEvent;
import com.medcore.api.messaging.KafkaTopics;
import com.medcore.api.messaging.PaymentEvent;
import com.medcore.api.messaging.ProcessedEventGuard;
import com.medcore.api.payment.dto.PaymentInitiationResponse;
import com.medcore.api.payment.dto.ReceiptResponse;
import com.medcore.api.payment.strategy.PaymentInitiation;
import com.medcore.api.payment.strategy.PaymentStrategy;
import com.medcore.api.payment.strategy.PaymentStrategyResolver;
import com.medcore.api.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final String WEBHOOK_CONSUMER = "stripe-webhook";

    private final PaymentRepository paymentRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentService appointmentService;
    private final AppointmentAccessPolicy accessPolicy;
    private final PaymentStrategyResolver strategyResolver;
    private final PaymentGateway paymentGateway;
    private final ProcessedEventGuard processedEventGuard;
    private final ApplicationEventPublisher events;
    private final MedcoreProperties properties;

    @Transactional
    public PaymentInitiationResponse initiate(UserPrincipal caller, UUID appointmentId, PaymentProvider method) {
        Appointment appointment = appointmentRepository.findWithDetailsById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", appointmentId));
        accessPolicy.require(accessPolicy.canView(caller, appointment));
        if (!appointment.getStatus().isActive()) {
            throw new BusinessException("APPOINTMENT_NOT_PAYABLE", "Only pending or confirmed appointments can be paid");
        }
        if (appointment.isPaid()) {
            throw new ConflictException("ALREADY_PAID", "This appointment is already paid");
        }

        PaymentStrategy strategy = strategyResolver.resolve(method);
        Payment payment = paymentRepository.save(Payment.initiate(appointment, method, properties.stripe().defaultCurrency()));
        PaymentInitiation initiation = strategy.initiate(payment);
        appointment.updatePaymentStatus(AppointmentPaymentStatus.PENDING);

        events.publishEvent(PaymentEvent.of(KafkaTopics.PAYMENT_CREATED, payment));
        return new PaymentInitiationResponse(payment.getId(), method, payment.getStatus(),
                initiation.clientSecret(), initiation.instructions());
    }

    @Transactional(readOnly = true)
    public Payment get(UserPrincipal caller, UUID paymentId) {
        Payment payment = load(paymentId);
        accessPolicy.require(accessPolicy.canView(caller, appointmentOf(payment)));
        return payment;
    }

    /** Front desk / insurer confirmation for non-gateway payments. */
    @Transactional
    public Payment confirmManually(UserPrincipal caller, UUID paymentId) {
        Payment payment = load(paymentId);
        Appointment appointment = appointmentOf(payment);
        accessPolicy.require(accessPolicy.canManage(caller, appointment));
        if (payment.isFinal()) {
            throw new ConflictException("PAYMENT_FINALIZED", "Payment is already " + payment.getStatus());
        }
        strategyResolver.resolve(payment.getProvider()).confirmManually(payment);
        onSucceeded(payment, appointment);
        return payment;
    }

    @Transactional
    public Payment refund(UserPrincipal caller, UUID paymentId) {
        Payment payment = load(paymentId);
        Appointment appointment = appointmentOf(payment);
        accessPolicy.require(accessPolicy.canManage(caller, appointment));
        return refund(payment, appointment);
    }

    /** Refunds the successful payment of an appointment, if any (used when a paid appointment is cancelled). */
    @Transactional
    public void refundIfPaid(UUID appointmentId) {
        paymentRepository.findByAppointmentIdOrderByCreatedAtDesc(appointmentId).stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCEEDED)
                .findFirst()
                .ifPresent(p -> refund(p, p.getAppointment()));
    }

    @Transactional(readOnly = true)
    public ReceiptResponse receipt(UserPrincipal caller, UUID paymentId) {
        Payment payment = load(paymentId);
        Appointment appointment = appointmentOf(payment);
        accessPolicy.require(accessPolicy.canView(caller, appointment));
        if (payment.getStatus() != PaymentStatus.SUCCEEDED && payment.getStatus() != PaymentStatus.REFUNDED) {
            throw new BusinessException("RECEIPT_UNAVAILABLE", "Receipts are only available for completed payments");
        }
        String number = "MC-%d-%s".formatted(payment.getPaidAt().atZone(appointment.getClinic().zoneId()).getYear(),
                payment.getId().toString().substring(0, 8).toUpperCase());
        String clinicName = appointment.getClinic().getLegalName() != null
                ? appointment.getClinic().getLegalName() : appointment.getClinic().getName();
        return new ReceiptResponse(number, payment.getId(), clinicName, appointment.getDoctor().fullName(),
                appointment.getPatient().fullName(), appointment.getScheduledAt(), payment.getAmount(),
                payment.getCurrency(), payment.getProvider().name(), payment.getPaidAt());
    }

    /** Applies a verified Stripe webhook event. Idempotent: duplicates and stale events are ignored. */
    @Transactional
    public void handleWebhook(String payload, String signature) {
        paymentGateway.parseWebhook(payload, signature).ifPresent(event -> {
            if (!processedEventGuard.firstDelivery(WEBHOOK_CONSUMER, event.eventId())) {
                log.debug("Duplicate Stripe event {}", event.eventId());
                return;
            }
            try {
                apply(event);
            } catch (RuntimeException e) {
                processedEventGuard.release(WEBHOOK_CONSUMER, event.eventId()); // Stripe retries on non-2xx
                throw e;
            }
        });
    }

    private void apply(GatewayEvent event) {
        Payment payment = paymentRepository.findByProviderPaymentId(event.providerPaymentId()).orElse(null);
        if (payment == null) {
            log.warn("Stripe event {} references unknown payment intent {}", event.eventId(), event.providerPaymentId());
            return;
        }
        Appointment appointment = appointmentOf(payment);
        switch (event.type()) {
            case PAYMENT_SUCCEEDED -> {
                if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
                    payment.markSucceeded();
                    onSucceeded(payment, appointment);
                }
            }
            case PAYMENT_FAILED -> {
                if (!payment.isFinal()) {
                    payment.markFailed(event.failureMessage());
                    appointment.updatePaymentStatus(AppointmentPaymentStatus.FAILED);
                    events.publishEvent(PaymentEvent.of(KafkaTopics.PAYMENT_FAILED, payment));
                }
            }
            case PAYMENT_CANCELED -> {
                if (!payment.isFinal()) {
                    payment.setStatus(PaymentStatus.CANCELLED);
                    appointment.updatePaymentStatus(AppointmentPaymentStatus.UNPAID);
                }
            }
            case CHARGE_REFUNDED -> {
                if (payment.getStatus() != PaymentStatus.REFUNDED) {
                    payment.markRefunded();
                    appointment.updatePaymentStatus(AppointmentPaymentStatus.REFUNDED);
                    events.publishEvent(PaymentEvent.of(KafkaTopics.PAYMENT_REFUNDED, payment));
                }
            }
        }
    }

    private void onSucceeded(Payment payment, Appointment appointment) {
        appointment.updatePaymentStatus(AppointmentPaymentStatus.PAID);
        appointmentService.confirmAfterPayment(appointment);
        events.publishEvent(PaymentEvent.of(KafkaTopics.PAYMENT_COMPLETED, payment));
    }

    private Payment refund(Payment payment, Appointment appointment) {
        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            throw new BusinessException(HttpStatus.CONFLICT, "PAYMENT_NOT_REFUNDABLE", "Only succeeded payments can be refunded");
        }
        strategyResolver.resolve(payment.getProvider()).refund(payment);
        payment.markRefunded();
        appointment.updatePaymentStatus(AppointmentPaymentStatus.REFUNDED);
        events.publishEvent(PaymentEvent.of(KafkaTopics.PAYMENT_REFUNDED, payment));
        return payment;
    }

    private Payment load(UUID paymentId) {
        return paymentRepository.findWithDetailsById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
    }

    private Appointment appointmentOf(Payment payment) {
        UUID appointmentId = payment.getAppointment().getId();
        return appointmentRepository.findWithDetailsById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", appointmentId));
    }
}
