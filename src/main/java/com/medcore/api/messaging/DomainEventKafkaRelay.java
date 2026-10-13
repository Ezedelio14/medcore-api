package com.medcore.api.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Observer: listens to every in-process {@link DomainEvent} and relays it to Kafka only after the
 * originating transaction commits, so consumers never see events for rolled-back changes.
 * (For strict at-least-once delivery across crashes, evolve this into a transactional outbox.)
 */
@Component
@RequiredArgsConstructor
public class DomainEventKafkaRelay {

    private final KafkaEventPublisher publisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void relay(DomainEvent event) {
        publisher.publish(event);
    }
}
