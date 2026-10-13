package com.medcore.api.notification;

import com.medcore.api.common.exception.ResourceNotFoundException;
import com.medcore.api.messaging.NotificationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher events;

    /** Persists an in-app notification and emits {@code notification.created} for delivery (email/SMS/push). */
    @Transactional
    public Notification create(UUID userId, NotificationType type, String title, String message) {
        Notification notification = notificationRepository.save(new Notification(userId, type, title, message));
        events.publishEvent(new NotificationEvent(notification.getId(), userId, type, title, message));
        return notification;
    }

    @Transactional(readOnly = true)
    public Page<Notification> listForUser(UUID userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .filter(n -> n.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));
        notification.markRead();
    }
}
