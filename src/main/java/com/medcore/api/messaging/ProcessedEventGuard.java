package com.medcore.api.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Makes consumers idempotent: Kafka delivers at-least-once, so redeliveries are skipped by event id. */
@Component
@RequiredArgsConstructor
public class ProcessedEventGuard {

    private static final Duration RETENTION = Duration.ofDays(7);

    private final StringRedisTemplate redis;

    /** Returns true the first time an event id is seen by the given consumer. */
    public boolean firstDelivery(String consumer, String eventId) {
        Boolean stored = redis.opsForValue()
                .setIfAbsent("medcore:kafka:processed:" + consumer + ":" + eventId, "1", RETENTION);
        return Boolean.TRUE.equals(stored);
    }

    /** Releases the marker so a failed attempt can be retried. */
    public void release(String consumer, String eventId) {
        redis.delete("medcore:kafka:processed:" + consumer + ":" + eventId);
    }
}
