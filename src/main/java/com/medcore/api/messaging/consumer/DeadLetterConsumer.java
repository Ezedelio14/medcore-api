package com.medcore.api.messaging.consumer;

import com.medcore.api.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Monitors every dead-letter topic. Records here exhausted their retries; they are logged and audited so
 * operators can inspect and replay them.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeadLetterConsumer {

    private final AuditService auditService;

    @KafkaListener(groupId = "medcore-dlt-monitor", topicPattern = ".*\\.DLT")
    public void onDeadLetter(ConsumerRecord<String, String> record) {
        String reason = header(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE);
        String originalTopic = header(record, KafkaHeaders.DLT_ORIGINAL_TOPIC);
        log.error("Dead-lettered message from {} (key={}): {}", originalTopic, record.key(), reason);
        auditService.record(null, "KAFKA_DEAD_LETTER", "KafkaMessage", null, Map.of(
                "topic", record.topic(),
                "originalTopic", String.valueOf(originalTopic),
                "key", String.valueOf(record.key()),
                "reason", String.valueOf(reason)));
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
