package com.medcore.api.messaging;

import com.medcore.api.common.web.CorrelationIdFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/** Kafka producer: wraps a {@link DomainEvent} in an {@link EventEnvelope} and publishes it. */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventPublisher {

    public static final String CORRELATION_HEADER = "x-correlation-id";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;

    public void publish(DomainEvent event) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        EventEnvelope envelope = new EventEnvelope(UUID.randomUUID(), event.topic(), Instant.now(), correlationId,
                jsonMapper.valueToTree(event));
        ProducerRecord<String, String> record =
                new ProducerRecord<>(event.topic(), event.key(), jsonMapper.writeValueAsString(envelope));
        if (correlationId != null) {
            record.headers().add(CORRELATION_HEADER, correlationId.getBytes(StandardCharsets.UTF_8));
        }
        kafkaTemplate.send(record).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish {} (key={})", event.topic(), event.key(), ex);
            } else {
                log.debug("Published {} (key={}) to partition {} offset {}", event.topic(), event.key(),
                        result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
            }
        });
    }
}
