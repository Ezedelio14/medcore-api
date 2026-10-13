package com.medcore.api.messaging;

import com.medcore.api.notification.NotificationType;

import java.util.UUID;

public record NotificationEvent(UUID notificationId, UUID userId, NotificationType type, String title, String message)
        implements DomainEvent {

    @Override
    public String topic() {
        return KafkaTopics.NOTIFICATION_CREATED;
    }

    @Override
    public String key() {
        return userId.toString();
    }
}
