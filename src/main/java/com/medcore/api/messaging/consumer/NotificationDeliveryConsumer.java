package com.medcore.api.messaging.consumer;

import com.medcore.api.integration.email.EmailSender;
import com.medcore.api.messaging.EventEnvelope;
import com.medcore.api.messaging.KafkaTopics;
import com.medcore.api.messaging.NotificationEvent;
import com.medcore.api.messaging.ProcessedEventGuard;
import com.medcore.api.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Delivers created notifications over e-mail (other channels can be added as more adapters). */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDeliveryConsumer {

    private static final String CONSUMER = "notification-delivery";

    private final EventEnvelopeReader reader;
    private final ProcessedEventGuard guard;
    private final UserRepository userRepository;
    private final EmailSender emailSender;

    @KafkaListener(groupId = "medcore-notification-delivery", topics = KafkaTopics.NOTIFICATION_CREATED)
    public void onNotificationCreated(String message) {
        EventEnvelope envelope = reader.read(message);
        if (!guard.firstDelivery(CONSUMER, envelope.eventId().toString())) {
            return;
        }
        try {
            NotificationEvent event = reader.payload(envelope, NotificationEvent.class);
            userRepository.findById(event.userId()).ifPresentOrElse(
                    user -> emailSender.send(user.getEmail(), event.title(), event.message()),
                    () -> log.warn("Notification {} targets unknown user", event.notificationId()));
        } catch (RuntimeException e) {
            guard.release(CONSUMER, envelope.eventId().toString());
            throw e;
        }
    }
}
