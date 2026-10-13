package com.medcore.api.messaging.consumer;

import com.medcore.api.messaging.AppointmentEvent;
import com.medcore.api.messaging.EventEnvelope;
import com.medcore.api.messaging.KafkaTopics;
import com.medcore.api.messaging.PaymentEvent;
import com.medcore.api.messaging.PrescriptionEvent;
import com.medcore.api.messaging.ProcessedEventGuard;
import com.medcore.api.notification.NotificationService;
import com.medcore.api.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Kafka consumer turning domain events into user notifications. Failures are retried by the
 * container's error handler and finally dead-lettered (see KafkaConfig).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventsConsumer {

    private static final String CONSUMER = "notifications";
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final EventEnvelopeReader reader;
    private final ProcessedEventGuard guard;
    private final NotificationService notifications;

    @KafkaListener(groupId = "medcore-notifications", topics = {
            KafkaTopics.APPOINTMENT_CREATED, KafkaTopics.APPOINTMENT_CONFIRMED, KafkaTopics.APPOINTMENT_CANCELLED,
            KafkaTopics.APPOINTMENT_COMPLETED, KafkaTopics.APPOINTMENT_RESCHEDULED, KafkaTopics.APPOINTMENT_REMINDER})
    public void onAppointmentEvent(String message) {
        EventEnvelope envelope = reader.read(message);
        handleOnce(envelope, () -> {
            AppointmentEvent event = reader.payload(envelope, AppointmentEvent.class);
            String when = WHEN.format(event.scheduledAt());
            switch (envelope.eventType()) {
                case KafkaTopics.APPOINTMENT_CREATED -> {
                    notifications.create(event.patientUserId(), NotificationType.APPOINTMENT_CREATED,
                            "Appointment requested", "Your appointment with %s on %s was booked.".formatted(event.doctorName(), when));
                    notifications.create(event.doctorUserId(), NotificationType.APPOINTMENT_CREATED,
                            "New appointment", "A new appointment was booked for %s.".formatted(when));
                }
                case KafkaTopics.APPOINTMENT_CONFIRMED -> notifications.create(event.patientUserId(),
                        NotificationType.APPOINTMENT_CONFIRMED, "Appointment confirmed",
                        "Your appointment with %s on %s is confirmed.".formatted(event.doctorName(), when));
                case KafkaTopics.APPOINTMENT_RESCHEDULED -> notifications.create(event.patientUserId(),
                        NotificationType.APPOINTMENT_CONFIRMED, "Appointment rescheduled",
                        "Your appointment with %s was moved to %s.".formatted(event.doctorName(), when));
                case KafkaTopics.APPOINTMENT_CANCELLED -> {
                    String body = "The appointment on %s was cancelled.".formatted(when);
                    notifications.create(event.patientUserId(), NotificationType.APPOINTMENT_CANCELLED, "Appointment cancelled", body);
                    notifications.create(event.doctorUserId(), NotificationType.APPOINTMENT_CANCELLED, "Appointment cancelled", body);
                }
                case KafkaTopics.APPOINTMENT_COMPLETED -> notifications.create(event.patientUserId(),
                        NotificationType.APPOINTMENT_COMPLETED, "How was your visit?",
                        "Your appointment with %s is complete. Leave a review!".formatted(event.doctorName()));
                case KafkaTopics.APPOINTMENT_REMINDER -> notifications.create(event.patientUserId(),
                        NotificationType.APPOINTMENT_REMINDER, "Appointment reminder",
                        "Reminder: appointment with %s on %s.".formatted(event.doctorName(), when));
                default -> log.warn("Unhandled appointment event {}", envelope.eventType());
            }
        });
    }

    @KafkaListener(groupId = "medcore-notifications",
            topics = {KafkaTopics.PAYMENT_COMPLETED, KafkaTopics.PAYMENT_FAILED, KafkaTopics.PAYMENT_REFUNDED})
    public void onPaymentEvent(String message) {
        EventEnvelope envelope = reader.read(message);
        handleOnce(envelope, () -> {
            PaymentEvent event = reader.payload(envelope, PaymentEvent.class);
            String amount = "%s %s".formatted(event.amount(), event.currency().toUpperCase());
            switch (envelope.eventType()) {
                case KafkaTopics.PAYMENT_COMPLETED -> notifications.create(event.patientUserId(),
                        NotificationType.PAYMENT_COMPLETED, "Payment received", "We received your payment of %s.".formatted(amount));
                case KafkaTopics.PAYMENT_FAILED -> notifications.create(event.patientUserId(),
                        NotificationType.PAYMENT_FAILED, "Payment failed", "Your payment of %s failed. Please try again.".formatted(amount));
                case KafkaTopics.PAYMENT_REFUNDED -> notifications.create(event.patientUserId(),
                        NotificationType.PAYMENT_COMPLETED, "Payment refunded", "Your payment of %s was refunded.".formatted(amount));
                default -> log.warn("Unhandled payment event {}", envelope.eventType());
            }
        });
    }

    @KafkaListener(groupId = "medcore-notifications", topics = KafkaTopics.PRESCRIPTION_CREATED)
    public void onPrescriptionEvent(String message) {
        EventEnvelope envelope = reader.read(message);
        handleOnce(envelope, () -> {
            PrescriptionEvent event = reader.payload(envelope, PrescriptionEvent.class);
            notifications.create(event.patientUserId(), NotificationType.PRESCRIPTION_CREATED,
                    "New prescription", "A new prescription is available in your medical records.");
        });
    }

    private void handleOnce(EventEnvelope envelope, Runnable handler) {
        if (!guard.firstDelivery(CONSUMER, envelope.eventId().toString())) {
            log.debug("Skipping duplicate event {}", envelope.eventId());
            return;
        }
        try {
            handler.run();
        } catch (RuntimeException e) {
            guard.release(CONSUMER, envelope.eventId().toString()); // allow the retry to process it
            throw e;
        }
    }
}
