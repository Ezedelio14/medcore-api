package com.medcore.api.messaging;

/**
 * Marker for events published in-process through Spring's ApplicationEventPublisher (Observer pattern).
 * Every domain event knows the Kafka topic it is relayed to and its partitioning key.
 */
public interface DomainEvent {

    String topic();

    /** Kafka message key; events of the same aggregate land on the same partition and stay ordered. */
    String key();
}
